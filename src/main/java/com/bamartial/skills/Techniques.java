package com.bamartial.skills;

import com.bamartial.BAMartial;
import com.bamartial.MartialConfig;
import com.bamartial.anim.MartialMoves;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Серверная логика боя: запуск боевых искусств и шквала (настоящие атакующие анимации Epic Fight),
 * эффекты попаданий приёмов, контрудары и тренировочная груша.
 * Урон, оглушение, сбивание с ног и отбрасывание считает сам Epic Fight по свойствам анимации.
 */
@Mod.EventBusSubscriber(modid = BAMartial.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Techniques {
    /** Временное состояние игрока (не сохраняется). */
    private static final class State {
        long nextFreeArt;
        long lastBagHit;
        long lastBagCombo;
        int bagCombo;
        int counterTarget = -1;
        long counterAt;
        StyleKind counterKind = StyleKind.BOXING;
        long judoCounterReady;
        boolean rush;
        int rushStrikes;
        long rushNext;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private Techniques() {}

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), id -> new State());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (s.rush) tickRush(p, s);
        if (s.counterTarget < 0 || p.level().getGameTime() < s.counterAt) return;
        int id = s.counterTarget;
        s.counterTarget = -1;
        performCounter(p, s.counterKind, p.level().getEntity(id));
    }

    // =====================================================================
    //  Серия ударов при зажатой атаке (ЛКМ): пока кнопка зажата и есть выносливость
    // =====================================================================

    /** Клиент сообщил: атака зажата / отпущена. */
    public static void setRush(ServerPlayer p, boolean holding) {
        State s = state(p);
        if (holding) {
            if (s.rush || !MartialConfig.get(MartialConfig.RUSH_ENABLED, true)) return;
            s.rush = true;
            s.rushStrikes = 0;
            s.rushNext = p.level().getGameTime();
        } else if (s.rush) {
            endRush(p, s, true);
        }
    }

    private static void tickRush(ServerPlayer p, State s) {
        ServerPlayerPatch sp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        MartialBattleStyle style = MartialSkills.style(p);
        if (sp == null || style == null || !sp.isEpicFightMode() || !p.isAlive() || !p.getMainHandItem().isEmpty()) {
            s.rush = false;
            return;
        }
        long now = p.level().getGameTime();
        if (now < s.rushNext) return;
        var state = sp.getEntityState();
        // оглушён или сбит — ждём; занят другим приёмом — ждём, пока закончит
        if (state.hurt() || state.knockDown() || (!state.canBasicAttack() && !inRush(sp))) {
            s.rushNext = now + 1;
            return;
        }
        float cost = (float) MartialConfig.get(MartialConfig.RUSH_STAMINA, 0.8);
        if (sp.getStamina() < cost) {
            endRush(p, s, false);
            actionBar(p, Component.translatable("msg.ba_martialarts.rush_tired").withStyle(ChatFormatting.GRAY));
            return;
        }
        AnimationManager.AnimationAccessor<? extends AttackAnimation> anim = MartialMoves.rush(style.getKind(), s.rushStrikes);
        if (anim == null) {
            s.rush = false;
            return;
        }
        sp.playAnimationSynchronized(anim, 0.0F);
        sp.setStamina(sp.getStamina() - cost);
        sp.setStaminaRegenAwaitTicks(Math.max(sp.getStaminaRegenAwaitTicks(), 20));
        s.rushStrikes++;
        s.rushNext = now + MartialMoves.rushInterval(style.getKind());
        actionBar(p, Component.translatable("msg.ba_martialarts.rush", s.rushStrikes).withStyle(ChatFormatting.GOLD));
    }

    /** Конец серии. Отпустил кнопку после 5+ ударов — завершающий удар стиля. */
    private static void endRush(ServerPlayer p, State s, boolean released) {
        s.rush = false;
        int strikes = s.rushStrikes;
        s.rushStrikes = 0;
        if (!released || strikes < 5) return;
        ServerPlayerPatch sp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        MartialBattleStyle style = MartialSkills.style(p);
        if (sp == null || style == null || !sp.isEpicFightMode() || !p.getMainHandItem().isEmpty()) return;
        var state = sp.getEntityState();
        if (state.hurt() || state.knockDown()) return;
        AnimationManager.AnimationAccessor<? extends AttackAnimation> finisher = MartialMoves.counter(style.getKind());
        if (finisher == null || sp.getStamina() < 1.0F) return;
        sp.playAnimationSynchronized(finisher, 0.0F);
        sp.setStamina(sp.getStamina() - 1.0F);
        sp.updateEntityState();
    }

    /** Сейчас идёт удар серии (следующий можно начинать сразу). */
    private static boolean inRush(ServerPlayerPatch sp) {
        try {
            MartialMoves.Info info = MartialMoves.info(sp.getServerAnimator().animationPlayer.getRealAnimation());
            return info != null && info.role() == MartialMoves.Role.BARRAGE;
        } catch (Throwable t) {
            return false;
        }
    }

    // =====================================================================
    //  Боевые искусства (Z) и шквал (C)
    // =====================================================================

    /** Можно ли сейчас применить искусство бесплатно (в Ультра инстинкте, с короткой паузой). */
    public static boolean freeArtReady(ServerPlayer p) {
        return UltraInstinct.isActive(p) && p.level().getGameTime() >= state(p).nextFreeArt;
    }

    /** Боевое искусство стиля: атакующая анимация с собственным хитбоксом, оглушением и импактом. */
    public static boolean performArt(ServerPlayerPatch sp, StyleKind kind) {
        ServerPlayer p = sp.getOriginal();
        State s = state(p);
        long now = p.level().getGameTime();
        if (UltraInstinct.isActive(p) && now < s.nextFreeArt) return false;
        AnimationManager.AnimationAccessor<? extends AttackAnimation> anim = MartialMoves.art(kind);
        if (anim == null) return false;

        LivingEntity target = findTarget(p, artRange(kind));
        if (target != null) face(sp, target);
        switch (kind) {
            // прыжок с переворотом — удары ногами в полёте
            case TAEKWONDO -> launch(p, 0.0, 0.62);
            // подскок в летящее колено
            case MUAY_THAI -> launch(p, 0.25, 0.34);
            default -> { }
        }
        sp.playAnimationSynchronized(anim, 0.0F);
        sp.updateEntityState();
        if (UltraInstinct.isActive(p)) s.nextFreeArt = now + 30;
        MartialAdvancements.award(p, "combat_art");
        actionBar(p, Component.translatable("skill.ba_martialarts." + kind.artId).append("!").withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    /** Шквал ударов: серия из 8 ударов, последний сбивает с ног. */
    public static boolean performFlurry(ServerPlayerPatch sp) {
        ServerPlayer p = sp.getOriginal();
        State s = state(p);
        long now = p.level().getGameTime();
        if (UltraInstinct.isActive(p) && now < s.nextFreeArt) return false;
        if (MartialMoves.FLURRY == null) return false;
        LivingEntity target = findTarget(p, 4.0);
        if (target != null) face(sp, target);
        sp.playAnimationSynchronized(MartialMoves.FLURRY, 0.0F);
        sp.updateEntityState();
        if (UltraInstinct.isActive(p)) s.nextFreeArt = now + 30;
        MartialAdvancements.award(p, "flurry");
        actionBar(p, Component.translatable("skill.ba_martialarts.flurry_of_blows").append("!").withStyle(ChatFormatting.GOLD));
        return true;
    }

    private static double artRange(StyleKind kind) {
        return switch (kind) {
            case KARATE, KUNG_FU -> 6.0;
            case MUAY_THAI -> 7.0;
            default -> 4.0;
        };
    }

    // =====================================================================
    //  Попадания
    // =====================================================================

    /**
     * Удар игрока в рукопашном стиле попал (до расчёта оглушения Epic Fight).
     * Возвращает, сколько метра Battle Arts он даёт.
     */
    public static float onHit(ServerPlayer p, LivingEntity target, EpicFightDamageSource source, LivingHurtEvent hurt) {
        MartialMoves.Info info = MartialMoves.info(source.getAnimation());
        if (info == null) return p.getMainHandItem().isEmpty() ? 8 : 0;

        double mult = MartialConfig.get(MartialConfig.DAMAGE_MULTIPLIER, 1.0);
        if (mult != 1.0 && hurt != null) hurt.setAmount((float) (hurt.getAmount() * mult));

        return switch (info.role()) {
            case COMBO -> 12;
            case FINISHER, DASH, AIR -> 25;
            case ART -> {
                artHit(p, info.kind(), target);
                yield 0;
            }
            case FLURRY -> 0;
            case BARRAGE -> 4;
        };
    }

    private static void artHit(ServerPlayer p, StyleKind kind, LivingEntity target) {
        if (kind == null) return;
        switch (kind) {
            // апперкот приподнимает соперника — затем Epic Fight роняет его анимацией нокдауна
            case BOXING -> {
                Vec3 dm = target.getDeltaMovement();
                target.setDeltaMovement(dm.x, Math.max(dm.y, 0.42), dm.z);
                target.hurtMarked = true;
            }
            // бросок: соперник оказывается за спиной и падает
            case JUDO -> throwBehind(p, target);
            default -> { }
        }
    }

    private static void throwBehind(ServerPlayer p, LivingEntity t) {
        Vec3 back = horizontalLook(p).scale(-(1.3 + t.getBbWidth() * 0.5));
        Vec3 dest = new Vec3(p.getX() + back.x, p.getY(), p.getZ() + back.z);
        AABB moved = t.getBoundingBox().move(dest.subtract(t.position()));
        if (p.level().noCollision(t, moved)) {
            t.teleportTo(dest.x, dest.y, dest.z);
        }
        ServerLevel lvl = p.serverLevel();
        lvl.sendParticles(EpicFightParticles.GROUND_SLAM.get(), t.getX(), t.getY() + 0.1, t.getZ(), 0, 1.5, 60.0, 0.6, 1.0);
        sound(p, EpicFightSounds.SLAM_HEAVY.get(), 1.0F, 1.0F);
    }

    // =====================================================================
    //  Контрудары (Ультра инстинкт, дзюдо)
    // =====================================================================

    /** Запланировать контрудар по атакующему через несколько тиков (после уклонения). */
    public static void scheduleCounter(ServerPlayer p, StyleKind kind, Entity attacker, int delayTicks) {
        if (!(attacker instanceof LivingEntity) || attacker == p) return;
        State s = state(p);
        s.counterTarget = attacker.getId();
        s.counterKind = kind == null ? StyleKind.BOXING : kind;
        s.counterAt = p.level().getGameTime() + delayTicks;
    }

    /** Дзюдо: после удачного уклонения Epic Fight — контрбросок (не чаще раза в 3 секунды). */
    public static void judoCounter(ServerPlayer p, Entity attacker) {
        State s = state(p);
        long now = p.level().getGameTime();
        if (now < s.judoCounterReady) return;
        s.judoCounterReady = now + 60;
        scheduleCounter(p, StyleKind.JUDO, attacker, 4);
        actionBar(p, Component.translatable("msg.ba_martialarts.judo_counter").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static void performCounter(ServerPlayer p, StyleKind kind, Entity e) {
        if (!(e instanceof LivingEntity t) || !t.isAlive() || !p.isAlive() || t.distanceToSqr(p) > 4.5 * 4.5) return;
        if (!p.getMainHandItem().isEmpty()) return;
        ServerPlayerPatch sp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        if (sp == null || !sp.isEpicFightMode()) return;
        var state = sp.getEntityState();
        if (state.attacking() || state.hurt() || state.knockDown()) return;
        AnimationManager.AnimationAccessor<? extends AttackAnimation> anim = MartialMoves.counter(kind);
        if (anim == null) return;
        face(sp, t);
        sp.playAnimationSynchronized(anim, 0.0F);
        sp.updateEntityState();
    }

    // =====================================================================
    //  Тренировочная груша
    // =====================================================================

    /** Удар по тренировочной груше (не чаще, чем раз в 8 тиков). */
    public static boolean bagHit(ServerPlayer p) {
        State s = state(p);
        long now = p.level().getGameTime();
        if (now - s.lastBagHit < 8) return false;
        s.lastBagHit = now;
        s.bagCombo = (now - s.lastBagCombo <= 30) ? s.bagCombo + 1 : 1;
        s.lastBagCombo = now;
        if (s.bagCombo % 4 == 0) {
            actionBar(p, Component.translatable("msg.ba_martialarts.bag_combo", s.bagCombo).withStyle(ChatFormatting.GOLD));
        }
        return true;
    }

    // =====================================================================
    //  Вспомогательное
    // =====================================================================

    /** Довернуть бойца (и камеру) к цели — хитбокс приёма Epic Fight смотрит туда же. */
    private static void face(ServerPlayerPatch sp, LivingEntity target) {
        if (!MartialConfig.get(MartialConfig.AUTO_AIM, true)) return;
        ServerPlayer p = sp.getOriginal();
        double dx = target.getX() - p.getX();
        double dz = target.getZ() - p.getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        p.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.yBodyRot = yaw;
        sp.setModelYRot(yaw, true);
    }

    private static void launch(ServerPlayer p, double forward, double up) {
        Vec3 look = horizontalLook(p);
        Vec3 dm = p.getDeltaMovement();
        p.setDeltaMovement(dm.x + look.x * forward, up, dm.z + look.z * forward);
        p.hurtMarked = true;
        p.fallDistance = 0;
    }

    public static boolean isValidTarget(ServerPlayer p, Entity e) {
        if (!(e instanceof LivingEntity) || e == p || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof TamableAnimal pet && pet.isOwnedBy(p)) return false;
        if (e instanceof ArmorStand stand && stand.isMarker()) return false;
        return !e.isAlliedTo(p);
    }

    /** Ближайший враг впереди (конус ~55°) в пределах дальности и в прямой видимости. */
    public static LivingEntity findTarget(ServerPlayer p, double range) {
        Vec3 look = horizontalLook(p);
        List<LivingEntity> list = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range, 2.0, range),
                e -> isValidTarget(p, e) && e.distanceToSqr(p) <= range * range);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
            double len = to.length();
            if (len > 0.5 && to.scale(1.0 / len).dot(look) < 0.57) continue;
            if (!p.hasLineOfSight(e)) continue;
            if (len < bestScore) {
                bestScore = len;
                best = e;
            }
        }
        return best;
    }

    private static Vec3 horizontalLook(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    public static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void actionBar(ServerPlayer p, Component msg) {
        p.displayClientMessage(msg, true);
    }
}
