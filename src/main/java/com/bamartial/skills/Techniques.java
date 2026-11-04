package com.bamartial.skills;

import com.bamartial.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Серверная логика техник: удары, добивания, боевые искусства стилей, шквал.
 */
@Mod.EventBusSubscriber(modid = com.bamartial.BAMartial.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Techniques {
    /** true, пока урон наносит наша техника — чтобы не считать его как удар рукой. */
    public static boolean inTechnique = false;

    /** Временное состояние игрока (не сохраняется). */
    private static final class State {
        int combo;
        long lastHit;
        int flurryTicks;
        int flurryTarget = -1;
        int leapTicks;
        int leapTarget = -1;
        int tornadoTicks;
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
        tickFlurry(p, s);
        tickLeap(p, s);
        tickTornado(p, s);
    }

    // =====================================================================
    //  Удары голой рукой и добивания
    // =====================================================================

    /** Удар голой рукой в стиле. Добивание прибавляет урон к этому же удару. Возвращает, сколько метра он дал. */
    public static float onUnarmedHit(ServerPlayer p, StyleKind kind, LivingEntity target, LivingHurtEvent hurt) {
        State s = state(p);
        long now = p.level().getGameTime();
        s.combo = (now - s.lastHit <= 30) ? s.combo + 1 : 1;
        s.lastHit = now;
        if (s.combo % 3 != 0) return 12;

        float extra;
        switch (kind) {
            case BOXING -> extra = 5;
            case KARATE -> extra = 6;
            case MUAY_THAI -> {
                extra = 4;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
            }
            case KUNG_FU -> {
                extra = 4;
                target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 10, 3), p);
            }
            case TAEKWONDO -> {
                extra = 5;
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0), p);
            }
            default -> {
                extra = 4;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), p);
                push(p, target, 1.2f, 0.4);
            }
        }
        hurt.setAmount(hurt.getAmount() + extra);
        ServerLevel lvl = p.serverLevel();
        lvl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                12, 0.3, 0.3, 0.3, 0.3);
        sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
        actionBar(p, Component.translatable("msg.ba_martialarts.finisher." + kind.styleId, s.combo).withStyle(ChatFormatting.GOLD));
        return 30;
    }

    public static void judoCounter(ServerPlayer p, Entity attacker) {
        if (!(attacker instanceof LivingEntity enemy) || enemy == p || !enemy.isAlive() || enemy.distanceToSqr(p) > 16) return;
        hit(p, enemy, 4);
        push(p, enemy, 2.0f, 0.5);
        p.serverLevel().sendParticles(ParticleTypes.CRIT, enemy.getX(), enemy.getY() + 1, enemy.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
        actionBar(p, Component.translatable("msg.ba_martialarts.judo_counter").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // =====================================================================
    //  Боевые искусства стилей
    // =====================================================================

    public static boolean signature(ServerPlayer p, StyleKind kind) {
        State s = state(p);
        if (s.flurryTicks > 0 || s.leapTicks > 0 || s.tornadoTicks > 0) return false;
        boolean ok = switch (kind) {
            case BOXING -> uppercut(p);
            case KARATE -> kiai(p);
            case MUAY_THAI -> flyingKnee(p, s);
            case KUNG_FU -> chiPalm(p);
            case TAEKWONDO -> tornadoKick(p, s);
            case JUDO -> ippon(p);
        };
        if (ok) {
            ModNetwork.animate(p, kind.animation);
            actionBar(p, Component.translatable("skill.ba_martialarts." + kind.artId).append("!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return ok;
    }

    /** Бокс: сокрушающий апперкот — враг взлетает и ослаблен. */
    private static boolean uppercut(ServerPlayer p) {
        LivingEntity t = findTarget(p, 3.5);
        if (t == null) return noTarget(p);
        hit(p, t, 12);
        t.setDeltaMovement(t.getDeltaMovement().x * 0.2, 1.15, t.getDeltaMovement().z * 0.2);
        t.hurtMarked = true;
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1), p);
        ServerLevel lvl = p.serverLevel();
        lvl.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 0.5, t.getZ(), 2, 0.2, 0.2, 0.2, 0);
        lvl.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 0.5, t.getZ(), 30, 0.3, 0.8, 0.3, 0.5);
        sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
        sound(p, SoundEvents.GENERIC_EXPLODE, 0.4f, 1.6f);
        return true;
    }

    /** Каратэ: киай — ударная волна по конусу на 7 блоков. */
    private static boolean kiai(ServerPlayer p) {
        Vec3 look = horizontalLook(p);
        for (LivingEntity t : around(p, 7.5)) {
            Vec3 to = new Vec3(t.getX() - p.getX(), 0, t.getZ() - p.getZ());
            if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(look) > 0.45) {
                hit(p, t, 10);
                push(p, t, 2.6f, 0.3);
            }
        }
        ServerLevel lvl = p.serverLevel();
        for (int i = 1; i <= 7; i++) {
            Vec3 c = p.position().add(look.scale(i)).add(0, 1.0, 0);
            double spread = 0.25 * i;
            lvl.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 4 + i, spread, 0.3, spread, 0.02);
            lvl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.5f);
        return true;
    }

    /** Муай-тай: летящее колено — прыжок к цели до 10 блоков и удар коленом при подлёте. */
    private static boolean flyingKnee(ServerPlayer p, State s) {
        LivingEntity t = findTarget(p, 10.0);
        if (t == null) return noTarget(p);
        Vec3 to = t.position().subtract(p.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        double dist = flat.length();
        Vec3 dir = dist < 1.0E-3 ? horizontalLook(p) : flat.scale(1 / dist);
        double speed = Math.min(1.9, 0.5 + dist * 0.22);
        p.setDeltaMovement(dir.x * speed, 0.5, dir.z * speed);
        p.hurtMarked = true;
        p.fallDistance = 0;
        s.leapTicks = 16;
        s.leapTarget = t.getId();
        sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 0.8f);
        return true;
    }

    private static void tickLeap(ServerPlayer p, State s) {
        if (s.leapTicks <= 0) return;
        s.leapTicks--;
        p.fallDistance = 0;
        Entity e = p.level().getEntity(s.leapTarget);
        if (!(e instanceof LivingEntity t) || !t.isAlive()) {
            s.leapTicks = 0;
            return;
        }
        if (p.distanceToSqr(t) <= 2.6 * 2.6) {
            s.leapTicks = 0;
            hit(p, t, 11);
            push(p, t, 2.0f, 0.45);
            p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 1.0, 0.2));
            p.hurtMarked = true;
            ServerLevel lvl = p.serverLevel();
            lvl.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 1.0, t.getZ(), 1, 0, 0, 0, 0);
            lvl.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1.0, t.getZ(), 25, 0.4, 0.4, 0.4, 0.5);
            sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
        }
    }

    /** Кунг-фу: ладонь Ци — луч на 16 блоков, пробивает до 5 врагов. */
    private static boolean chiPalm(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(16));
        Vec3 blockHit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation();
        double len = eye.distanceTo(blockHit);

        List<LivingEntity> list = p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, blockHit).inflate(1.2),
                e -> isValidTarget(p, e));
        list.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        int hits = 0;
        for (LivingEntity t : list) {
            Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
            Vec3 rel = c.subtract(eye);
            double along = rel.dot(look);
            if (along < 0 || along > len + 0.5) continue;
            if (rel.subtract(look.scale(along)).length() > 1.0 + t.getBbWidth() * 0.5) continue;
            hit(p, t, 10);
            push(p, t, 1.4f, 0.2);
            if (++hits >= 5) break;
        }
        ServerLevel lvl = p.serverLevel();
        for (double d = 0.8; d < len; d += 0.4) {
            Vec3 c = eye.add(look.scale(d)).add(0, -0.2, 0);
            lvl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.8f);
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.2f);
        return true;
    }

    /** Тхэквондо: торнадо-кик — прыжок с вращением, 6 ударов по всем вокруг. */
    private static boolean tornadoKick(ServerPlayer p, State s) {
        p.setDeltaMovement(p.getDeltaMovement().x, 0.55, p.getDeltaMovement().z);
        p.hurtMarked = true;
        p.fallDistance = 0;
        s.tornadoTicks = 12;
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.3f);
        return true;
    }

    private static void tickTornado(ServerPlayer p, State s) {
        if (s.tornadoTicks <= 0) return;
        s.tornadoTicks--;
        p.fallDistance = 0;
        if (s.tornadoTicks % 2 != 0) return;
        for (LivingEntity t : around(p, 3.5)) {
            hit(p, t, 3.5f);
            push(p, t, s.tornadoTicks == 0 ? 1.8f : 0.5f, 0.15);
        }
        ring(p, 2.8, ParticleTypes.SWEEP_ATTACK, 0.9);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.0f + p.getRandom().nextFloat() * 0.5f);
    }

    /** Дзюдо: иппон — бросок через плечо, враг летит за спину и оглушён. */
    private static boolean ippon(ServerPlayer p) {
        LivingEntity t = findTarget(p, 3.5);
        if (t == null) return noTarget(p);
        hit(p, t, 10);
        Vec3 back = horizontalLook(p).scale(-1.1);
        t.setDeltaMovement(back.x, 0.75, back.z);
        t.hurtMarked = true;
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3), p);
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1), p);
        p.serverLevel().sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1.0, t.getZ(), 20, 0.4, 0.4, 0.4, 0.3);
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
        sound(p, SoundEvents.GENERIC_BIG_FALL, 1.0f, 0.8f);
        return true;
    }

    // =====================================================================
    //  Шквал ударов (взрывное искусство)
    // =====================================================================

    public static boolean flurry(ServerPlayer p) {
        State s = state(p);
        if (s.flurryTicks > 0 || s.leapTicks > 0 || s.tornadoTicks > 0) return false;
        LivingEntity t = findTarget(p, 3.5);
        if (t == null) return noTarget(p);
        s.flurryTicks = 20;
        s.flurryTarget = t.getId();
        ModNetwork.animate(p, "flurry");
        actionBar(p, Component.translatable("skill.ba_martialarts.flurry_of_blows").append("!").withStyle(ChatFormatting.GOLD));
        return true;
    }

    private static void tickFlurry(ServerPlayer p, State s) {
        if (s.flurryTicks <= 0) return;
        s.flurryTicks--;
        Entity e = p.level().getEntity(s.flurryTarget);
        if (!(e instanceof LivingEntity t) || !t.isAlive() || p.distanceToSqr(t) > 36) {
            s.flurryTicks = 0;
            return;
        }
        if (s.flurryTicks % 2 != 0) return;
        boolean last = s.flurryTicks == 0;
        hit(p, t, last ? 8 : 2);
        ServerLevel lvl = p.serverLevel();
        double y = t.getY() + t.getBbHeight() * 0.6;
        if (last) {
            push(p, t, 2.5f, 0.4);
            lvl.sendParticles(ParticleTypes.EXPLOSION, t.getX(), y, t.getZ(), 1, 0, 0, 0, 0);
            sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
        } else {
            t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 1.0, 0.2));
            t.hurtMarked = true;
            lvl.sendParticles(ParticleTypes.CRIT, t.getX(), y, t.getZ(), 6, 0.25, 0.25, 0.25, 0.3);
            sound(p, SoundEvents.PLAYER_ATTACK_WEAK, 0.6f, 1.2f + p.getRandom().nextFloat() * 0.4f);
        }
    }

    // =====================================================================
    //  Вспомогательное
    // =====================================================================

    public static void hit(ServerPlayer p, LivingEntity t, float dmg) {
        inTechnique = true;
        try {
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), dmg);
        } finally {
            inTechnique = false;
        }
    }

    public static void push(ServerPlayer p, LivingEntity t, float strength, double up) {
        t.knockback(strength, p.getX() - t.getX(), p.getZ() - t.getZ());
        t.setDeltaMovement(t.getDeltaMovement().add(0, up, 0));
        t.hurtMarked = true;
    }

    public static boolean isValidTarget(ServerPlayer p, Entity e) {
        if (!(e instanceof LivingEntity) || e == p || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof TamableAnimal pet && pet.isOwnedBy(p)) return false;
        if (e instanceof ArmorStand stand && stand.isMarker()) return false;
        return !e.isAlliedTo(p);
    }

    private static List<LivingEntity> around(ServerPlayer p, double r) {
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r, 1.0, r),
                e -> isValidTarget(p, e) && e.distanceToSqr(p) <= r * r);
    }

    private static Vec3 horizontalLook(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private static void ring(ServerPlayer p, double r, SimpleParticleType type, double y) {
        ServerLevel lvl = p.serverLevel();
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16.0;
            lvl.sendParticles(type, p.getX() + Math.cos(a) * r, p.getY() + y, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private static boolean noTarget(ServerPlayer p) {
        actionBar(p, Component.translatable("msg.ba_martialarts.no_target").withStyle(ChatFormatting.GRAY));
        return false;
    }

    public static LivingEntity findTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box,
                e -> isValidTarget(p, e) && e.isPickable(), range * range);
        if (hit != null && hit.getEntity() instanceof LivingEntity le && p.hasLineOfSight(le)) return le;
        return null;
    }

    public static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void actionBar(ServerPlayer p, Component msg) {
        p.displayClientMessage(msg, true);
    }
}
