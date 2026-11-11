package com.bamartial.selftest;

import com.bamartial.MartialConfig;
import com.bamartial.anim.MartialMoves;
import com.bamartial.skills.MartialBattleStyle;
import com.bamartial.skills.MartialSkills;
import com.bamartial.skills.StyleKind;
import com.bamartial.skills.Techniques;
import com.bamartial.skills.UltraInstinct;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.forixaim.battle_arts_api.battle_arts_skills.BattleArtsSkillSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillSlots;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Проверка Battle Arts: Martial Arts на настоящем сервере (только для CI, в выпускаемый мод не входит).
 * Тестовый игрок с каждым стилем бьёт неподвижного кадавра: связка Epic Fight, серия ударов при зажатой атаке
 * (удары, попадания, выносливость, завершающий удар, конец серии без выносливости), боевое искусство, шквал.
 * Итог — строка «[SELFTEST] DONE PASS/FAIL» в логе и файл selftest-report.txt.
 */
@Mod(SelfTest.MODID)
public final class SelfTest {
    public static final String MODID = "ba_martialarts_selftest";
    private static final Logger LOG = LogUtils.getLogger();
    /** Сколько тиков держим атаку в проверке серии. */
    private static final int RUSH_TICKS = 40;

    private record Step(String name, int ticks, IntConsumer body) {}

    private final Deque<Step> steps = new ArrayDeque<>();
    private Step current;
    private int stepTick;
    private int startDelay = 60;
    private boolean started;
    private boolean finished;

    private MinecraftServer server;
    private ServerLevel level;
    private ServerPlayer player;
    private ServerPlayerPatch patch;
    private Husk target;
    private Vec3 playerPos;
    private Vec3 targetPos;

    private final Map<MartialMoves.Role, Integer> hits = new EnumMap<>(MartialMoves.Role.class);
    private final Set<String> hitAnims = new LinkedHashSet<>();
    private int otherHits;

    private int checks;
    private final List<String> failures = new ArrayList<>();
    private final List<String> report = new ArrayList<>();

    private float staminaStart;
    private float staminaAtEnd;
    private int endedTick;
    private boolean wasRushing;
    private int casts;
    private final List<String> castAnims = new ArrayList<>();
    private int attacks;
    private int dodges;
    private MartialMoves.Role castRole;

    public SelfTest() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    // =====================================================================
    //  События
    // =====================================================================

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        started = true;
        log("server started, tests begin in " + startDelay + " ticks");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !started || finished) return;
        if (startDelay > 0) {
            if (--startDelay == 0) plan();
            return;
        }
        if (current == null) {
            current = steps.poll();
            stepTick = 0;
            if (current == null) {
                finish();
                return;
            }
        }
        String name = current.name();
        try {
            current.body().accept(stepTick);
        } catch (Throwable t) {
            fail(name + ": exception " + t, t);
            if (player == null || patch == null) {
                steps.clear();
                current = null;
                return;
            }
        }
        try {
            // так сервер тикает настоящего игрока: после обработки пакетов, с позицией «на земле» от клиента
            if (player != null && player.isAlive()) {
                holdTarget();
                player.setOnGround(true);
                player.doTick();
            }
        } catch (Throwable t) {
            fail(name + ": exception in player tick " + t, t);
        }
        stepTick++;
        if (current != null && stepTick >= current.ticks()) current = null;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (target == null || player == null || event.getEntity() != target) return;
        DamageSource source = event.getSource();
        if (source.getEntity() != player) return;
        if (source instanceof EpicFightDamageSource efSource && efSource.getAnimation() != null) {
            MartialMoves.Info info = MartialMoves.info(efSource.getAnimation());
            hitAnims.add(name(efSource.getAnimation()));
            if (info != null) {
                hits.merge(info.role(), 1, Integer::sum);
                return;
            }
        }
        otherHits++;
    }

    // =====================================================================
    //  План проверок
    // =====================================================================

    private void plan() {
        steps.add(new Step("setup", 20, t -> {
            if (t == 0) setup();
            if (t == 19) {
                check("test player has Epic Fight patch in battle mode", patch != null && patch.isEpicFightMode(), "");
                check("target spawned", target != null && target.isAlive(), "");
            }
        }));
        for (StyleKind kind : StyleKind.values()) {
            steps.add(new Step(kind + " equip", 10, t -> {
                if (t == 0) equip(kind);
                if (t == 9) {
                    MartialBattleStyle style = MartialSkills.style(player);
                    check(kind + ": style equipped", style != null && style.getKind() == kind, "style=" + style);
                    SkillContainer art = patch.getSkill(BattleArtsSkillSlots.COMBAT_ART);
                    check(kind + ": combat art slot filled", art != null && art.getSkill() == MartialSkills.COMBAT_ARTS.get(kind),
                            "skill=" + (art == null ? null : art.getSkill()));
                }
            }));
            steps.add(comboStep(kind));
            steps.add(new Step(kind + " rest", 20, t -> { }));
            steps.add(specialAttackStep(kind, true));
            steps.add(new Step(kind + " rest", 20, t -> { }));
            steps.add(specialAttackStep(kind, false));
            steps.add(new Step(kind + " rest", 20, t -> { }));
            steps.add(rushStep(kind));
            steps.add(releaseStep(kind));
            steps.add(new Step(kind + " rest", 20, t -> { }));
            steps.add(tiredStep(kind));
            steps.add(new Step(kind + " rest", 30, t -> { }));
            steps.add(artStep(kind));
            steps.add(new Step(kind + " rest", 30, t -> { }));
        }
        steps.add(flurryStep());
        steps.add(new Step("rest", 40, t -> { }));
        steps.add(ultraStep());
        log("planned " + steps.size() + " steps");
    }

    private void setup() {
        level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        playerPos = new Vec3(spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
        targetPos = playerPos.add(0.0, 0.0, 1.4);

        ServerPlayer p = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "ba_selftest"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p);
        player = p;
        player.setInvulnerable(true);
        patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) throw new IllegalStateException("no Epic Fight patch on the test player");
        patch.toEpicFightMode(true);
        placePlayer();
        spawnTarget();
        log("player at " + playerPos + ", target at " + targetPos);
    }

    private void equip(StyleKind kind) {
        placePlayer();
        SkillContainer slot = patch.getSkill(BattleArtsSkillSlots.BATTLE_STYLE);
        slot.setSkill(MartialSkills.STYLES.get(kind));
        patch.toEpicFightMode(true);
    }

    // ---------------- связка Epic Fight (миксин getAutoAttackMotion) ----------------
    private Step comboStep(StyleKind kind) {
        return new Step(kind + " combo", 140, t -> {
            if (t == 0) {
                placePlayer();
                resetTarget();
                resetHits();
                casts = 0;
                castAnims.clear();
                patch.setStamina(patch.getMaxStamina());
            }
            if (casts < 4 && patch.getEntityState().canBasicAttack()) {
                SkillContainer basic = patch.getSkill(SkillSlots.BASIC_ATTACK);
                if (basic.requestCasting(patch, buf())) {
                    casts++;
                    castAnims.add(name(currentAnimation()));
                }
            }
            if (t == 139) {
                check(kind + ": 4 basic attacks", casts == 4, "casts=" + casts);
                boolean ours = !castAnims.isEmpty() && castAnims.stream().allMatch(a -> a.startsWith("ba_martialarts:"));
                check(kind + ": basic attacks use the style combo", ours, "anims=" + castAnims);
                check(kind + ": every combo move connects", hitAnims.containsAll(castAnims),
                        "hit=" + hitAnims + " cast=" + castAnims + " other=" + otherHits);
            }
        });
    }

    // ---------------- удар в рывке (бег + атака) и в прыжке ----------------
    private Step specialAttackStep(StyleKind kind, boolean dash) {
        MartialMoves.Role want = dash ? MartialMoves.Role.DASH : MartialMoves.Role.AIR;
        String what = dash ? "dash attack" : "air attack";
        return new Step(kind + " " + what, 70, t -> {
            if (t == 0) {
                placePlayer();
                resetTarget();
                resetHits();
                casts = 0;
                castAnims.clear();
                castRole = null;
                patch.setStamina(patch.getMaxStamina());
            }
            if (casts == 0 && patch.getEntityState().canBasicAttack()) {
                // так клиент сообщает серверу о беге и о прыжке
                if (dash) player.setSprinting(true);
                else player.setOnGround(false);
                boolean cast = patch.getSkill(SkillSlots.BASIC_ATTACK).requestCasting(patch, buf());
                player.setSprinting(false);
                player.setOnGround(true);
                if (cast) {
                    casts++;
                    castAnims.add(name(currentAnimation()));
                    MartialMoves.Info info = MartialMoves.info(currentAnimation());
                    castRole = info == null ? null : info.role();
                }
            }
            if (t == 69) {
                check(kind + ": " + what + " plays the style move", casts == 1 && castRole == want, "anims=" + castAnims + " role=" + castRole);
                if (dash) {
                    check(kind + ": dash attack connects", count(want) >= 1, "hits=" + hits + " other=" + otherHits + " anims=" + hitAnims);
                } else {
                    // в прыжке игрок над целью и летит к ней — с места на земле попадание не показательно
                    info(kind + ": air attack hits from a standstill: " + count(want) + " " + hitAnims);
                }
            }
        });
    }

    // ---------------- серия при зажатой атаке ----------------
    private Step rushStep(StyleKind kind) {
        return new Step(kind + " rush", RUSH_TICKS + 1, t -> {
            if (t == 0) {
                placePlayer();
                resetTarget();
                resetHits();
                patch.setStamina(patch.getMaxStamina());
                // обычное восстановление Epic Fight не должно мешать счёту (оно идёт только вне действия)
                patch.setStaminaRegenAwaitTicks(200);
                staminaStart = patch.getStamina();
                Techniques.startRush(player);
                check(kind + ": rush starts on hold", Techniques.isRushing(player), "");
            }
            if (t == RUSH_TICKS) {
                int strikes = Techniques.rushStrikes(player);
                float used = staminaStart - patch.getStamina();
                int expected = (RUSH_TICKS - 1) / MartialMoves.rushInterval(kind) + 1;
                float cost = rushCost();
                Set<String> moves = new LinkedHashSet<>();
                for (int i = 0; i < 4; i++) moves.add(name(MartialMoves.rush(kind, i)));
                check(kind + ": rush keeps going while held", Techniques.isRushing(player), "");
                check(kind + ": rush strike count", Math.abs(strikes - expected) <= 1, "strikes=" + strikes + " expected=" + expected);
                check(kind + ": rush strikes connect", count(MartialMoves.Role.BARRAGE) >= strikes - 1,
                        "hits=" + count(MartialMoves.Role.BARRAGE) + " strikes=" + strikes + " other=" + otherHits + " anims=" + hitAnims);
                check(kind + ": every rush move connects", hitAnims.containsAll(moves), "hit=" + hitAnims + " moves=" + moves);
                check(kind + ": rush stamina per strike", Math.abs(used - strikes * cost) < 0.05F, "used=" + used + " strikes=" + strikes + " cost=" + cost);
                resetHits();
                Techniques.stopRush(player, true);
            }
        });
    }

    private Step releaseStep(StyleKind kind) {
        return new Step(kind + " release", 45, t -> {
            if (t == 0) {
                String anim = name(currentAnimation());
                String want = name(MartialMoves.counter(kind));
                check(kind + ": release after 5+ strikes plays the finisher", anim.equals(want), "anim=" + anim + " want=" + want);
                check(kind + ": rush stops on release", !Techniques.isRushing(player), "");
            }
            if (t == 44) {
                check(kind + ": finisher hits", count(MartialMoves.Role.FINISHER) >= 1, "hits=" + hits + " anims=" + hitAnims);
            }
        });
    }

    private Step tiredStep(StyleKind kind) {
        return new Step(kind + " stamina", 70, t -> {
            float cost = rushCost();
            if (t == 0) {
                placePlayer();
                resetTarget();
                resetHits();
                patch.setStamina(cost * 3 + 0.1F);
                patch.setStaminaRegenAwaitTicks(200);
                staminaStart = patch.getStamina();
                endedTick = -1;
                wasRushing = true;
                Techniques.startRush(player);
            } else if (wasRushing && !Techniques.isRushing(player)) {
                wasRushing = false;
                endedTick = t;
                staminaAtEnd = patch.getStamina();
            }
            if (t == 69) {
                check(kind + ": rush ends when stamina runs out", endedTick >= 0, "still rushing");
                float used = staminaStart - staminaAtEnd;
                check(kind + ": strikes until stamina is out", endedTick < 0 || Math.abs(used - 3 * cost) < 0.05F, "used=" + used + " ended at " + endedTick);
                check(kind + ": no finisher when stamina ran out", count(MartialMoves.Role.FINISHER) == 0, "hits=" + hits);
                Techniques.stopRush(player, true);
            }
        });
    }

    // ---------------- боевое искусство (Z) ----------------
    private Step artStep(StyleKind kind) {
        return new Step(kind + " combat art", 80, t -> {
            if (t == 0) {
                placePlayer();
                resetTarget();
                resetHits();
                MartialBattleStyle.addMeter(MartialSkills.styleContainer(player), 10000.0F);
                SkillContainer art = patch.getSkill(BattleArtsSkillSlots.COMBAT_ART);
                boolean cast = art != null && art.getSkill() != null && art.requestCasting(patch, buf());
                check(kind + ": combat art cast", cast, "skill=" + (art == null ? null : art.getSkill()) + " anim=" + name(currentAnimation()));
                if (!cast) Techniques.performArt(patch, kind);
            }
            if (t == 79) {
                check(kind + ": combat art hits", count(MartialMoves.Role.ART) >= 1, "hits=" + hits + " other=" + otherHits + " anims=" + hitAnims);
            }
        });
    }

    // ---------------- шквал ударов (C) ----------------
    private Step flurryStep() {
        return new Step("flurry of blows", 90, t -> {
            if (t == 0) {
                equip(StyleKind.BOXING);
                placePlayer();
                resetTarget();
                resetHits();
            }
            if (t == 5) {
                MartialBattleStyle.addMeter(MartialSkills.styleContainer(player), 10000.0F);
                SkillContainer burst = patch.getSkill(BattleArtsSkillSlots.BURST_ART);
                boolean cast = burst != null && burst.getSkill() != null && burst.requestCasting(patch, buf());
                check("flurry of blows cast", cast, "skill=" + (burst == null ? null : burst.getSkill()) + " anim=" + name(currentAnimation()));
                if (!cast) Techniques.performFlurry(patch);
            }
            if (t == 89) {
                check("flurry of blows hits", count(MartialMoves.Role.FLURRY) >= 4, "hits=" + hits + " other=" + otherHits + " anims=" + hitAnims);
            }
        });
    }

    // ---------------- Ультра инстинкт (V): уклонение и контрудар ----------------
    private Step ultraStep() {
        return new Step("ultra instinct", 230, t -> {
            if (t == 0) {
                placePlayer();
                resetTarget();
                resetHits();
                attacks = 0;
                dodges = 0;
                MartialBattleStyle.addMeter(MartialSkills.styleContainer(player), 10000.0F);
                SkillContainer ultimate = patch.getSkill(BattleArtsSkillSlots.ULTIMATE_ART);
                boolean cast = ultimate != null && ultimate.getSkill() != null && ultimate.requestCasting(patch, buf());
                check("ultra instinct cast", cast && UltraInstinct.isActive(player),
                        "skill=" + (ultimate == null ? null : ultimate.getSkill()) + " active=" + UltraInstinct.isActive(player));
            }
            if (t >= 40 && t <= 200 && t % 20 == 0) {
                // кадавр «бьёт» игрока
                player.setInvulnerable(false);
                player.setHealth(player.getMaxHealth());
                player.invulnerableTime = 0;
                player.hurt(level.damageSources().mobAttack(target), 2.0F);
                player.setInvulnerable(true);
                attacks++;
            }
            if (t >= 41 && t <= 201 && t % 20 == 1 && isDodge(name(currentAnimation()))) dodges++;
            if (t == 229) {
                check("ultra instinct dodges attacks", dodges >= 1, "dodges=" + dodges + "/" + attacks);
                check("ultra instinct counterattacks connect", count(MartialMoves.Role.FINISHER) >= 1,
                        "hits=" + hits + " other=" + otherHits + " anims=" + hitAnims);
                player.setHealth(player.getMaxHealth());
            }
        });
    }

    private static boolean isDodge(String anim) {
        return anim.equals(name(MartialMoves.DODGE_LEFT)) || anim.equals(name(MartialMoves.DODGE_RIGHT))
                || anim.equals(name(MartialMoves.DODGE_BACK));
    }

    // =====================================================================
    //  Вспомогательное
    // =====================================================================

    private void placePlayer() {
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
        player.setYHeadRot(0.0F);
        player.yBodyRot = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        if (patch != null) patch.setModelYRot(0.0F, true);
    }

    private void spawnTarget() {
        Husk husk = EntityType.HUSK.create(level);
        if (husk == null) throw new IllegalStateException("could not create the target");
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        husk.setSilent(true);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        husk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        husk.setHealth(husk.getMaxHealth());
        husk.moveTo(targetPos.x, targetPos.y, targetPos.z, 180.0F, 0.0F);
        level.addFreshEntity(husk);
        target = husk;
    }

    private void resetTarget() {
        if (target == null || !target.isAlive()) spawnTarget();
        LivingEntity living = target;
        living.setHealth(living.getMaxHealth());
        holdTarget();
    }

    /** Цель стоит на месте лицом к игроку (отбрасывание и броски не уводят её из-под ударов). */
    private void holdTarget() {
        if (target == null || !target.isAlive()) return;
        target.moveTo(targetPos.x, targetPos.y, targetPos.z, 180.0F, 0.0F);
        target.setYHeadRot(180.0F);
        target.yBodyRot = 180.0F;
        target.setDeltaMovement(Vec3.ZERO);
    }

    private void resetHits() {
        hits.clear();
        hitAnims.clear();
        otherHits = 0;
    }

    private int count(MartialMoves.Role role) {
        return hits.getOrDefault(role, 0);
    }

    private AssetAccessor<?> currentAnimation() {
        return patch.getServerAnimator().animationPlayer.getRealAnimation();
    }

    private static String name(AssetAccessor<?> animation) {
        return animation == null ? "null" : String.valueOf(animation.registryName());
    }

    private static float rushCost() {
        return (float) MartialConfig.get(MartialConfig.RUSH_STAMINA, 0.8);
    }

    private static FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private void check(String what, boolean ok, String detail) {
        checks++;
        String line = (ok ? "ok   " : "FAIL ") + what + (detail.isEmpty() ? "" : " | " + detail);
        report.add(line);
        if (ok) log(line);
        else {
            failures.add(what + (detail.isEmpty() ? "" : " | " + detail));
            LOG.warn("[SELFTEST] {}", line);
        }
    }

    /** Сведения без проверки (в отчёт и лог). */
    private void info(String line) {
        report.add("info " + line);
        log("info " + line);
    }

    private void fail(String what, Throwable t) {
        checks++;
        failures.add(what);
        report.add("FAIL " + what);
        LOG.error("[SELFTEST] " + what, t);
    }

    private void finish() {
        finished = true;
        String result = failures.isEmpty() ? "PASS" : "FAIL";
        report.add("");
        report.add("RESULT " + result + " checks=" + checks + " failures=" + failures.size());
        report.add("chosen animations: " + MartialMoves.chosenSources());
        try {
            Files.write(Path.of("selftest-report.txt"), report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("[SELFTEST] could not write the report", e);
        }
        for (String f : failures) LOG.warn("[SELFTEST] FAILED: {}", f);
        log("DONE " + result + " checks=" + checks + " failures=" + failures.size());
        if (target != null) target.discard();
    }

    private static void log(String msg) {
        LOG.info("[SELFTEST] {}", msg);
    }
}
