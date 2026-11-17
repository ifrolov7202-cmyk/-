package com.bamartial.skills;

import com.bamartial.BAMartial;
import com.bamartial.MartialConfig;
import com.bamartial.anim.MartialMoves;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Силы Херобрина (по мотивам Annoying Villagers; анимации — из мода EpicFight: Annoying Villagers, если он установлен).
 * <ul>
 *   <li>Чёрный огонь: каждый удар стиля поджигает цель.</li>
 *   <li>Боевое искусство (Z): стоя — удар из портала за спиной цели, на бегу — линия обсидиановых столпов,
 *       присев — гроза вокруг себя.</li>
 *   <li>Полёт (двойной прыжок, как в творческом режиме) за выносливость Epic Fight.</li>
 *   <li>Не горит, не получает урона от молний и падения.</li>
 * </ul>
 * Столпы — только картинка (сущности-отображения блоков): мир не меняется, после выключения сервера ничего не остаётся.
 */
@Mod.EventBusSubscriber(modid = BAMartial.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Herobrine {
    /** Метка временных сущностей (столпы): оставшиеся после перезапуска сервера удаляются при загрузке. */
    public static final String TEMP_TAG = "ba_martialarts_temp";
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final ResourceLocation AV_BLACK_FIRE = new ResourceLocation("annoyingvillagers", "black_fire");

    /** Кому мы сами разрешили летать (чтобы не отнять полёт, данный творческим режимом или другим модом). */
    private static final Set<UUID> FLIGHT = new HashSet<>();
    /** Живые временные сущности. */
    private static final Set<UUID> TEMP = new HashSet<>();

    private record Task(long due, ServerLevel level, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();

    private Herobrine() {}

    public static boolean isHerobrine(ServerPlayer p) {
        MartialBattleStyle style = MartialSkills.style(p);
        return style != null && style.getKind() == StyleKind.HEROBRINE;
    }

    // =====================================================================
    //  Боевое искусство: выбор силы и действие перед ударом
    // =====================================================================

    /** На бегу — столпы, присев — гроза, иначе — портал. */
    public static MartialMoves.Technique choose(ServerPlayer p) {
        if (p.isSprinting()) return MartialMoves.HEROBRINE_PILLARS;
        if (p.isShiftKeyDown()) return MartialMoves.HEROBRINE_LIGHTNING;
        return MartialMoves.HEROBRINE_PORTAL;
    }

    /** То, что происходит до удара: шаг через портал, столпы из земли, гром. */
    public static void beforePower(ServerPlayerPatch sp, MartialMoves.Technique technique) {
        ServerPlayer p = sp.getOriginal();
        if (technique == MartialMoves.HEROBRINE_PORTAL) {
            portalStep(sp);
        } else if (technique == MartialMoves.HEROBRINE_PILLARS) {
            pillarLine(p);
        } else if (technique == MartialMoves.HEROBRINE_LIGHTNING) {
            ServerLevel level = p.serverLevel();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 2.2, p.getZ(), 40, 0.4, 0.4, 0.4, 0.2);
            Techniques.sound(p, SoundEvents.TRIDENT_THUNDER, 1.0F, 1.0F);
        }
        playAvSound(p, 1.0F);
    }

    /** Шаг через портал: за спину ближайшего врага впереди (до 16 блоков) или на 8 блоков вперёд. */
    private static void portalStep(ServerPlayerPatch sp) {
        ServerPlayer p = sp.getOriginal();
        ServerLevel level = p.serverLevel();
        LivingEntity target = Techniques.findTarget(p, 16.0);
        Vec3 from = p.position();
        Vec3 dest = null;
        if (target != null) {
            Vec3 dir = new Vec3(target.getX() - p.getX(), 0, target.getZ() - p.getZ());
            dir = dir.lengthSqr() < 1.0E-4 ? Techniques.horizontalLook(p) : dir.normalize();
            double back = target.getBbWidth() * 0.5 + p.getBbWidth() * 0.5 + 0.6;
            Vec3 behind = new Vec3(target.getX() + dir.x * back, target.getY(), target.getZ() + dir.z * back);
            if (free(p, behind)) dest = behind;
        } else {
            Vec3 look = Techniques.horizontalLook(p);
            Vec3 eye = p.getEyePosition();
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(8.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(8.0)) : hit.getLocation().subtract(look.scale(0.6));
            Vec3 feet = new Vec3(end.x, p.getY(), end.z);
            if (free(p, feet)) dest = feet;
        }
        if (dest == null) return;
        portalFx(level, from);
        p.teleportTo(dest.x, dest.y, dest.z);
        p.fallDistance = 0;
        portalFx(level, dest);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
        Techniques.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.6F);
        if (target != null) Techniques.face(sp, target);
    }

    private static boolean free(ServerPlayer p, Vec3 feet) {
        AABB box = p.getBoundingBox().move(feet.subtract(p.position()));
        return p.level().noCollision(p, box);
    }

    private static void portalFx(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 60, 0.4, 0.9, 0.4, 0.4);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 30, 0.3, 0.8, 0.3, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, 12, 0.3, 0.1, 0.3, 0.01);
    }

    /** Линия обсидиановых столпов, вырастающих из земли один за другим. */
    private static void pillarLine(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 look = Techniques.horizontalLook(p);
        long now = level.getGameTime();
        int i = 0;
        for (double d = 1.5; d <= 11.0; d += 1.5, i++) {
            double x = p.getX() + look.x * d;
            double z = p.getZ() + look.z * d;
            double y = groundY(level, x, p.getY(), z);
            if (Double.isNaN(y)) continue;
            float height = 2.0F + (i % 2) * 0.75F;
            schedule(level, now + i, () -> spawnPillar(level, x, y, z, height));
        }
        Techniques.sound(p, SoundEvents.DEEPSLATE_BREAK, 1.2F, 0.6F);
    }

    /** Земля под точкой (до 3 блоков вниз и 1 вверх); NaN — земли нет. */
    private static double groundY(ServerLevel level, double x, double y, double z) {
        BlockPos start = BlockPos.containing(x, y + 1.0, z);
        for (int dy = 0; dy <= 4; dy++) {
            BlockPos pos = start.below(dy);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return pos.getY() + 1.0;
            }
        }
        return Double.NaN;
    }

    private static void spawnPillar(ServerLevel level, double x, double y, double z, float height) {
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:obsidian");
        tag.put("block_state", state);
        tag.put("transformation", transformation(-0.45F, -height, -0.45F, 0.9F, height));
        Entity pillar = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(x, y, z, 0.0F, 0.0F);
            return e;
        });
        if (pillar == null) return;
        pillar.addTag(TEMP_TAG);
        TEMP.add(pillar.getUUID());
        if (!level.addFreshEntity(pillar)) {
            TEMP.remove(pillar.getUUID());
            return;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, obsidian), x, y + 0.1, z, 25, 0.4, 0.1, 0.4, 0.15);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.2, z, 6, 0.3, 0.1, 0.3, 0.02);
        level.playSound(null, x, y, z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
        long now = level.getGameTime();
        // вырастает за 3 тика, стоит секунду, уходит в землю за 5 тиков
        schedule(level, now + 1, () -> move(pillar, 0.0F, height, 3));
        schedule(level, now + 24, () -> move(pillar, -height, height, 5));
        schedule(level, now + 30, () -> {
            TEMP.remove(pillar.getUUID());
            pillar.discard();
        });
    }

    private static void move(Entity pillar, float translateY, float height, int ticks) {
        if (pillar.isRemoved()) return;
        CompoundTag tag = pillar.saveWithoutId(new CompoundTag());
        tag.put("transformation", transformation(-0.45F, translateY, -0.45F, 0.9F, height));
        tag.putInt("interpolation_duration", ticks);
        tag.putInt("start_interpolation", 0);
        pillar.load(tag);
    }

    private static CompoundTag transformation(float tx, float ty, float tz, float width, float height) {
        CompoundTag t = new CompoundTag();
        t.put("left_rotation", floats(0, 0, 0, 1));
        t.put("right_rotation", floats(0, 0, 0, 1));
        t.put("translation", floats(tx, ty, tz));
        t.put("scale", floats(width, height, width));
        return t;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    // =====================================================================
    //  Попадания
    // =====================================================================

    /** Удар Херобрина попал: чёрный огонь; у сил — молния, подброс столпом. */
    public static void onHit(ServerPlayer p, LivingEntity target, AssetAccessor<?> animation, MartialMoves.Role role) {
        ServerLevel level = p.serverLevel();
        if (!target.fireImmune()) target.setSecondsOnFire(role == MartialMoves.Role.ART ? 5 : 3);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                10, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.02);
        if (role != MartialMoves.Role.ART || animation == null) return;
        if (same(animation, MartialMoves.HEROBRINE_LIGHTNING)) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        } else if (same(animation, MartialMoves.HEROBRINE_PILLARS)) {
            Vec3 dm = target.getDeltaMovement();
            target.setDeltaMovement(dm.x, Math.max(dm.y, 0.55), dm.z);
            target.hurtMarked = true;
        }
    }

    private static boolean same(AssetAccessor<?> animation, MartialMoves.Technique technique) {
        return technique != null && technique.anim() != null && technique.anim().registryName() != null
                && technique.anim().registryName().equals(animation.registryName());
    }

    /** Звук чёрного огня из Annoying Villagers, если мод установлен. */
    private static void playAvSound(ServerPlayer p, float pitch) {
        if (!ForgeRegistries.SOUND_EVENTS.containsKey(AV_BLACK_FIRE)) return;
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(AV_BLACK_FIRE);
        if (sound != null) Techniques.sound(p, sound, 0.8F, pitch);
    }

    // =====================================================================
    //  Полёт
    // =====================================================================

    /** Забрать полёт, если его дали мы (стиль сняли, вышел из игры). */
    public static void revokeFlight(ServerPlayer p) {
        if (!FLIGHT.remove(p.getUUID())) return;
        if (p.isCreative() || p.isSpectator()) return;
        Abilities abilities = p.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
        p.onUpdateAbilities();
    }

    /** Может ли игрок сейчас летать как Херобрин (для проверок). */
    public static boolean hasHerobrineFlight(ServerPlayer p) {
        return FLIGHT.contains(p.getUUID()) && p.getAbilities().mayfly;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer p)) return;
        boolean herobrine = isHerobrine(p) && p.isAlive();
        if (herobrine && p.isOnFire() && MartialConfig.get(MartialConfig.HEROBRINE_FIRE_IMMUNE, true)) p.clearFire();

        boolean wantFlight = herobrine && MartialConfig.get(MartialConfig.HEROBRINE_FLIGHT, true) && !p.isCreative() && !p.isSpectator();
        if (!wantFlight) {
            if (FLIGHT.contains(p.getUUID())) revokeFlight(p);
            return;
        }
        Abilities abilities = p.getAbilities();
        if (!abilities.mayfly) {
            abilities.mayfly = true;
            FLIGHT.add(p.getUUID());
            p.onUpdateAbilities();
        }
        if (!abilities.flying || !FLIGHT.contains(p.getUUID())) return;
        ServerPlayerPatch sp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        float cost = (float) (MartialConfig.get(MartialConfig.HEROBRINE_FLIGHT_STAMINA, 0.5) / 20.0);
        if (sp != null && cost > 0) {
            if (sp.getStamina() < cost) {
                abilities.flying = false;
                p.onUpdateAbilities();
                Techniques.actionBar(p, Component.translatable("msg.ba_martialarts.herobrine.tired").withStyle(ChatFormatting.GRAY));
                return;
            }
            sp.setStamina(sp.getStamina() - cost);
            sp.setStaminaRegenAwaitTicks(Math.max(sp.getStaminaRegenAwaitTicks(), 10));
        }
        if (p.tickCount % 4 == 0) {
            p.serverLevel().sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.2, 0.05, 0.2, 0.0);
        }
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !isHerobrine(p)) return;
        if (!MartialConfig.get(MartialConfig.HEROBRINE_FIRE_IMMUNE, true)) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_LIGHTNING)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isHerobrine(p)) {
            event.setDistance(0.0F);
            event.setDamageMultiplier(0.0F);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) revokeFlight(p);
    }

    // =====================================================================
    //  Временные сущности и отложенные действия
    // =====================================================================

    private static void schedule(ServerLevel level, long due, Runnable action) {
        TASKS.add(new Task(due, level, action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.level().getGameTime() >= t.due()) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            try {
                t.action().run();
            } catch (RuntimeException e) {
                LOG.warn("[ba_martialarts] herobrine effect failed", e);
            }
        }
    }

    /** Столпы, оставшиеся в мире после перезапуска сервера, не загружаются. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (!event.getLevel().isClientSide() && e.getTags().contains(TEMP_TAG) && !TEMP.contains(e.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TASKS.clear();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (UUID id : new ArrayList<>(TEMP)) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
        }
        TEMP.clear();
        FLIGHT.clear();
    }

    /** Сколько временных столпов сейчас в мире (для проверок). */
    public static int liveEffects() {
        return TEMP.size();
    }
}
