package com.elementalarsenal.effect;

import com.elementalarsenal.ElementalArsenal;
import com.elementalarsenal.Element;
import com.elementalarsenal.item.ElementalWeaponItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Серверная логика: стихийные техники навыков и эффекты при обычных ударах.
 */
@Mod.EventBusSubscriber(modid = ElementalArsenal.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ElementalEffects {
    /** true, пока урон наносит наша техника — чтобы не срабатывали эффекты обычного удара. */
    private static boolean inArt = false;

    private static final class Task {
        int ticks;
        final Runnable action;

        Task(int ticks, Runnable action) {
            this.ticks = ticks;
            this.action = action;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> NEW_TASKS = new ArrayList<>();
    /** Цели «Абсолютного нуля»: id сущности -> время, до которого следующий удар раскалывает лёд. */
    private static final Map<UUID, Long> SHATTER = new HashMap<>();
    /** Цель «Теневого шага» для игрока. */
    private static final Map<UUID, Integer> VOID_TARGET = new HashMap<>();
    /** Начальная точка рывка «Абсолютного нуля». */
    private static final Map<UUID, Vec3> FROST_START = new HashMap<>();

    private ElementalEffects() {}

    // =====================================================================
    //  Планировщик
    // =====================================================================

    private static void later(int ticks, Runnable action) {
        NEW_TASKS.add(new Task(ticks, action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        TASKS.addAll(NEW_TASKS);
        NEW_TASKS.clear();
        Iterator<Task> it = TASKS.iterator();
        List<Task> due = new ArrayList<>();
        while (it.hasNext()) {
            Task t = it.next();
            if (--t.ticks <= 0) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) {
            try {
                t.action.run();
            } catch (Exception e) {
                // одна сломанная техника не должна ронять сервер
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
        NEW_TASKS.clear();
        SHATTER.clear();
        VOID_TARGET.clear();
        FROST_START.clear();
    }

    // =====================================================================
    //  Навыки оружия
    // =====================================================================

    /** Вызывается до запуска анимации (телепорт «Теневого шага», запоминание точки рывка). */
    public static void beforeArt(ServerPlayer p, Element element) {
        switch (element) {
            case VOID -> shadowStepTeleport(p);
            case FROST -> FROST_START.put(p.getUUID(), p.position());
            default -> {}
        }
    }

    public static void castArt(ServerPlayer p, Element element) {
        announce(p, element);
        switch (element) {
            case INFERNO -> infernoCataclysm(p);
            case FROST -> absoluteZero(p);
            case STORM -> stormWrath(p);
            case VOID -> shadowStepStrike(p);
            case SOUL -> soulHarvest(p);
            case GALE -> tempestAscension(p);
        }
    }

    // ---------- Огонь: Испепеляющий раскол ----------
    private static void infernoCataclysm(ServerPlayer p) {
        sound(p, SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
        later(13, () -> {
            if (!p.isAlive()) return;
            ServerLevel lvl = p.serverLevel();
            Vec3 look = flatLook(p);
            Vec3 c = p.position().add(look.scale(1.5));
            lvl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
            lvl.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 25, 0.8, 0.2, 0.8, 0.5);
            sound(p, SoundEvents.GENERIC_EXPLODE, 1.3F, 0.7F);
            sound(p, SoundEvents.FIRECHARGE_USE, 1.2F, 0.6F);
            Set<Integer> hitIds = new HashSet<>();
            for (int w = 0; w < 4; w++) {
                final double r = 1.6 + w * 1.7;
                later(w * 2, () -> {
                    ring(lvl, c, r, ParticleTypes.FLAME, (int) (r * 14), 0.05);
                    ring(lvl, c, r, ParticleTypes.SMALL_FLAME, (int) (r * 8), 0.15);
                    lvl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5, c.z, 6, r * 0.5, 0.3, r * 0.5, 0.02);
                    for (LivingEntity e : enemies(p, c, r + 0.8)) {
                        if (!hitIds.add(e.getId())) continue;
                        hit(p, e, 9.0F);
                        e.setSecondsOnFire(6);
                        Vec3 out = e.position().subtract(c).multiply(1, 0, 1);
                        out = out.lengthSqr() < 1.0E-4 ? look : out.normalize();
                        push(e, out.x * 0.7, 0.55, out.z * 0.7);
                        lvl.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 15, 0.3, 0.5, 0.3, 0.05);
                    }
                });
            }
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0, false, false, true));
        });
    }

    // ---------- Лёд: Абсолютный ноль ----------
    private static void absoluteZero(ServerPlayer p) {
        sound(p, SoundEvents.POWDER_SNOW_STEP, 1.2F, 0.5F);
        later(15, () -> {
            if (!p.isAlive()) return;
            ServerLevel lvl = p.serverLevel();
            Vec3 start = FROST_START.getOrDefault(p.getUUID(), p.position());
            FROST_START.remove(p.getUUID());
            Vec3 end = p.position();
            Vec3 path = end.subtract(start);
            int steps = Math.max(4, (int) (path.length() * 3));
            for (int i = 0; i <= steps; i++) {
                Vec3 pt = start.add(path.scale(i / (double) steps));
                lvl.sendParticles(ParticleTypes.SNOWFLAKE, pt.x, pt.y + 1.0, pt.z, 3, 0.25, 0.4, 0.25, 0.02);
            }
            ring(lvl, end, 3.5, ParticleTypes.SNOWFLAKE, 60, 0.05);
            sound(p, SoundEvents.GLASS_BREAK, 1.3F, 0.6F);
            sound(p, SoundEvents.POWDER_SNOW_BREAK, 1.3F, 0.8F);
            AABB box = new AABB(start, end).inflate(2.5, 2.0, 2.5);
            List<LivingEntity> targets = p.level().getEntitiesOfClass(LivingEntity.class, box.inflate(1.5), e -> valid(p, e)
                    && (distToSegment(e.position(), start, end) <= 2.6 || e.distanceTo(p) <= 4.0));
            long until = lvl.getGameTime() + 100;
            for (LivingEntity e : targets) {
                hit(p, e, 8.0F);
                freeze(e, 4);
                SHATTER.put(e.getUUID(), until);
                lvl.sendParticles(ParticleTypes.ITEM_SNOWBALL, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 25, 0.3, 0.5, 0.3, 0.1);
                lvl.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            }
        });
    }

    private static void freeze(LivingEntity e, int slowAmp) {
        e.setTicksFrozen(Math.max(e.getTicksFrozen(), 320));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, slowAmp));
        e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 2));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
        e.hurtMarked = true;
    }

    // ---------- Молния: Гнев Громовержца ----------
    private static void stormWrath(ServerPlayer p) {
        later(12, () -> {
            if (!p.isAlive()) return;
            ServerLevel lvl = p.serverLevel();
            List<LivingEntity> pool = enemies(p, p.position(), 14.0);
            pool.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
            Vec3 from = p.position().add(0, p.getBbHeight() * 0.7, 0);
            Set<Integer> used = new HashSet<>();
            LivingEntity prev = null;
            int jumps = 0;
            Vec3 cursor = from;
            while (jumps < 6) {
                final Vec3 at = cursor;
                LivingEntity next = pool.stream().filter(e -> !used.contains(e.getId()) && e.isAlive())
                        .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(at))).orElse(null);
                if (next == null || next.position().distanceTo(at) > (prev == null ? 14.0 : 8.0)) break;
                used.add(next.getId());
                final LivingEntity target = next;
                final Vec3 a = cursor;
                final float dmg = 11.0F - jumps * 1.0F;
                later(jumps * 3, () -> {
                    if (!target.isAlive()) return;
                    Vec3 b = target.position().add(0, target.getBbHeight() * 0.6, 0);
                    line(lvl, a, b, ParticleTypes.ELECTRIC_SPARK, 6);
                    line(lvl, a, b, ParticleTypes.END_ROD, 2);
                    bolt(lvl, p, target.position());
                    hit(p, target, dmg);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
                    lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 30, 0.4, 0.6, 0.4, 0.4);
                });
                cursor = next.position().add(0, next.getBbHeight() * 0.6, 0);
                prev = next;
                jumps++;
            }
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, false, true));
            lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
        });
    }

    private static void bolt(ServerLevel lvl, ServerPlayer p, Vec3 pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(lvl);
        if (bolt == null) return;
        bolt.setVisualOnly(true);
        bolt.moveTo(pos.x, pos.y, pos.z);
        bolt.setCause(p);
        lvl.addFreshEntity(bolt);
    }

    // ---------- Пустота: Теневой шаг ----------
    private static void shadowStepTeleport(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 far = eye.add(look.scale(24));
        HitResult block = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p.level(), p, eye, limit,
                new AABB(eye, limit).inflate(1.5), e -> e instanceof LivingEntity le && valid(p, le));
        Vec3 origin = p.position();
        Vec3 dest;
        LivingEntity target = ehr != null ? (LivingEntity) ehr.getEntity() : null;
        if (target != null) {
            Vec3 tLook = Vec3.directionFromRotation(0, target.getYRot());
            Vec3 behind = target.position().subtract(tLook.scale(target.getBbWidth() * 0.5 + 1.0));
            dest = freeAt(p, behind) ? behind : target.position().add(origin.subtract(target.position()).multiply(1, 0, 1).normalize().scale(target.getBbWidth() * 0.5 + 1.0));
            VOID_TARGET.put(p.getUUID(), target.getId());
        } else {
            Vec3 flat = flatLook(p);
            HitResult h = p.level().clip(new ClipContext(origin.add(0, 0.5, 0), origin.add(0, 0.5, 0).add(flat.scale(9)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            dest = h.getLocation().subtract(flat.scale(0.8)).subtract(0, 0.5, 0);
            VOID_TARGET.remove(p.getUUID());
        }
        if (!freeAt(p, dest)) return;
        lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, origin.x, origin.y + 1, origin.z, 50, 0.3, 0.8, 0.3, 0.1);
        lvl.sendParticles(ParticleTypes.SQUID_INK, origin.x, origin.y + 1, origin.z, 15, 0.3, 0.6, 0.3, 0.02);
        float yaw = p.getYRot();
        float pitch = p.getXRot();
        if (target != null) {
            Vec3 d = target.position().subtract(dest);
            yaw = (float) (Mth.atan2(d.z, d.x) * (180F / Math.PI)) - 90.0F;
            pitch = 0.0F;
        }
        p.teleportTo(lvl, dest.x, dest.y, dest.z, yaw, pitch);
        p.setYHeadRot(yaw);
        p.fallDistance = 0;
        lvl.sendParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1, dest.z, 60, 0.4, 0.8, 0.4, 0.4);
        lvl.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
        sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.2F);
    }

    private static void shadowStepStrike(ServerPlayer p) {
        Integer id = VOID_TARGET.remove(p.getUUID());
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, true));
        if (id == null) return;
        later(3, () -> {
            Entity ent = p.level().getEntity(id);
            if (!(ent instanceof LivingEntity target) || !target.isAlive() || !p.isAlive()) return;
            ServerLevel lvl = p.serverLevel();
            boolean execute = target.getHealth() <= target.getMaxHealth() * 0.35F;
            hit(p, target, execute ? 22.0F : 11.0F);
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
            double y = target.getY() + target.getBbHeight() * 0.5;
            lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), y, target.getZ(), 40, 0.3, 0.5, 0.3, 0.2);
            lvl.sendParticles(ParticleTypes.SQUID_INK, target.getX(), y, target.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
            if (execute) {
                lvl.sendParticles(ParticleTypes.DRAGON_BREATH, target.getX(), y, target.getZ(), 40, 0.4, 0.6, 0.4, 0.05);
                sound(p, SoundEvents.WITHER_HURT, 0.8F, 1.6F);
                actionBar(p, Component.translatable("msg.elemental_arsenal.execute").withStyle(ChatFormatting.DARK_PURPLE));
            }
            sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
        });
    }

    // ---------- Души: Жатва душ ----------
    private static void soulHarvest(ServerPlayer p) {
        sound(p, SoundEvents.SOUL_ESCAPE, 1.5F, 0.6F);
        final float[] total = {0};
        for (int k = 0; k < 3; k++) {
            final int wave = k;
            later(4 + k * 4, () -> {
                if (!p.isAlive()) return;
                ServerLevel lvl = p.serverLevel();
                Vec3 c = p.position();
                double r = 6.0;
                for (int i = 0; i < 24; i++) {
                    double ang = (i / 24.0) * Math.PI * 2 + wave;
                    double rr = r * (1 - i / 48.0);
                    lvl.sendParticles(ParticleTypes.SOUL, c.x + Math.cos(ang) * rr, c.y + 0.3 + i * 0.05, c.z + Math.sin(ang) * rr, 1, 0, 0, 0, 0.01);
                }
                for (LivingEntity e : enemies(p, c, r)) {
                    hit(p, e, 4.5F);
                    total[0] += 4.5F;
                    Vec3 in = c.subtract(e.position()).multiply(1, 0, 1);
                    if (in.lengthSqr() > 1.0E-4) {
                        in = in.normalize().scale(Math.min(0.6, e.distanceTo(p) * 0.15));
                        push(e, in.x, 0.1, in.z);
                    }
                    Vec3 from = e.position().add(0, e.getBbHeight() * 0.6, 0);
                    line(lvl, from, c.add(0, 1, 0), ParticleTypes.SCULK_SOUL, 5);
                }
                sound(p, SoundEvents.SOUL_ESCAPE, 1.0F, 0.9F + wave * 0.2F);
                if (wave == 2 && total[0] > 0) {
                    p.heal(total[0] * 0.3F);
                    p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1, false, false, true));
                    lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 1, c.z, 40, 0.4, 0.8, 0.4, 0.05);
                    sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.5F, 1.0F);
                }
            });
        }
    }

    // ---------- Ветер: Восходящий шторм ----------
    private static void tempestAscension(ServerPlayer p) {
        sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1.2F, 1.4F);
        later(4, () -> {
            if (!p.isAlive()) return;
            ServerLevel lvl = p.serverLevel();
            Vec3 c = p.position();
            for (int i = 0; i < 3; i++) {
                ring(lvl, c.add(0, i * 0.8, 0), 2.0 + i * 1.5, ParticleTypes.CLOUD, 30 + i * 10, 0.08);
            }
            lvl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y + 1, c.z, 12, 2.5, 0.4, 2.5, 0);
            sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
            sound(p, SoundEvents.ELYTRA_FLYING, 0.6F, 1.6F);
            for (LivingEntity e : enemies(p, c, 6.5)) {
                hit(p, e, 7.0F);
                Vec3 out = e.position().subtract(c).multiply(1, 0, 1);
                out = out.lengthSqr() < 1.0E-4 ? flatLook(p) : out.normalize();
                push(e, out.x * 0.5, 1.2, out.z * 0.5);
                lvl.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.3, e.getZ(), 15, 0.3, 0.2, 0.3, 0.05);
            }
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 100, 2, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, true));
        });
    }

    // =====================================================================
    //  Эффекты обычных ударов
    // =====================================================================

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        ServerLevel lvl = p.serverLevel();

        // Раскол льда: первый удар по замороженной цели
        Long until = inArt ? null : SHATTER.remove(target.getUUID());
        if (until != null) {
            if (until >= lvl.getGameTime()) {
                event.setAmount(event.getAmount() * 1.6F + 3.0F);
                target.setTicksFrozen(0);
                lvl.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 40, 0.4, 0.6, 0.4, 0.25);
                sound(p, SoundEvents.GLASS_BREAK, 1.2F, 1.3F);
                actionBar(p, Component.translatable("msg.elemental_arsenal.shatter").withStyle(ChatFormatting.AQUA));
            }
        }

        if (inArt || event.getSource().getDirectEntity() != p) return;
        if (!(p.getMainHandItem().getItem() instanceof ElementalWeaponItem weapon)) return;
        double y = target.getY() + target.getBbHeight() * 0.6;

        switch (weapon.getElement()) {
            case INFERNO -> {
                target.setSecondsOnFire(4);
                lvl.sendParticles(ParticleTypes.FLAME, target.getX(), y, target.getZ(), 10, 0.3, 0.4, 0.3, 0.03);
            }
            case FROST -> {
                target.setTicksFrozen(Math.min(target.getTicksFrozen() + 70, 300));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                lvl.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), y, target.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            }
            case STORM -> {
                lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), y, target.getZ(), 12, 0.3, 0.4, 0.3, 0.3);
                if (p.getRandom().nextFloat() < 0.18F) {
                    later(1, () -> {
                        if (!target.isAlive()) return;
                        bolt(lvl, p, target.position());
                        hit(p, target, 5.0F);
                    });
                }
            }
            case VOID -> {
                Vec3 tLook = Vec3.directionFromRotation(0, target.getYRot());
                Vec3 toAttacker = p.position().subtract(target.position()).multiply(1, 0, 1);
                if (toAttacker.lengthSqr() > 1.0E-4 && tLook.dot(toAttacker.normalize()) < -0.4) {
                    event.setAmount(event.getAmount() * 1.75F);
                    lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), y, target.getZ(), 20, 0.3, 0.4, 0.3, 0.1);
                    actionBar(p, Component.translatable("msg.elemental_arsenal.backstab").withStyle(ChatFormatting.DARK_PURPLE));
                } else {
                    lvl.sendParticles(ParticleTypes.PORTAL, target.getX(), y, target.getZ(), 10, 0.3, 0.4, 0.3, 0.3);
                }
            }
            case SOUL -> {
                p.heal(Math.max(0.5F, event.getAmount() * 0.15F));
                lvl.sendParticles(ParticleTypes.SOUL, target.getX(), y, target.getZ(), 5, 0.3, 0.4, 0.3, 0.02);
            }
            case GALE -> {
                if (!p.onGround()) {
                    event.setAmount(event.getAmount() * 1.3F);
                }
                Vec3 out = target.position().subtract(p.position()).multiply(1, 0, 1);
                if (out.lengthSqr() > 1.0E-4) {
                    final Vec3 kb = out.normalize().scale(0.35);
                    later(1, () -> push(target, kb.x, 0.15, kb.z));
                }
                lvl.sendParticles(ParticleTypes.CLOUD, target.getX(), y, target.getZ(), 6, 0.3, 0.3, 0.3, 0.03);
            }
        }
    }

    // =====================================================================
    //  Утилиты
    // =====================================================================

    private static void announce(ServerPlayer p, Element element) {
        actionBar(p, Component.translatable("skill." + ElementalArsenal.MODID + "." + element.skillId)
                .append("!").withStyle(element.color, ChatFormatting.BOLD));
    }

    private static boolean valid(ServerPlayer p, LivingEntity e) {
        return e != p && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(p)
                && !(e instanceof ArmorStand)
                && !(e instanceof TamableAnimal ta && ta.isOwnedBy(p))
                && !(e instanceof net.minecraft.world.entity.player.Player other && (other.isCreative() || !p.canHarmPlayer(other)));
    }

    private static List<LivingEntity> enemies(ServerPlayer p, Vec3 c, double r) {
        return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, r * 0.6 + 1.5, r),
                e -> valid(p, e) && e.position().distanceToSqr(c) <= r * r + 2.0);
    }

    private static void hit(ServerPlayer p, LivingEntity target, float amount) {
        inArt = true;
        try {
            target.invulnerableTime = 0;
            target.hurt(p.damageSources().playerAttack(p), amount);
        } finally {
            inArt = false;
        }
    }

    private static void push(LivingEntity e, double x, double y, double z) {
        double resist = 1.0 - e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(x * resist, y * resist, z * resist));
        e.hurtMarked = true;
    }

    private static Vec3 flatLook(ServerPlayer p) {
        return Vec3.directionFromRotation(0, p.getYRot());
    }

    private static boolean freeAt(ServerPlayer p, Vec3 pos) {
        return p.level().noCollision(p, p.getBoundingBox().move(pos.subtract(p.position())));
    }

    private static double distToSegment(Vec3 pt, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        if (len < 1.0E-6) return pt.distanceTo(a);
        double t = Mth.clamp(pt.subtract(a).dot(ab) / len, 0, 1);
        return pt.distanceTo(a.add(ab.scale(t)));
    }

    private static void ring(ServerLevel lvl, Vec3 c, double r, ParticleOptions particle, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double ang = (i / (double) count) * Math.PI * 2;
            double x = c.x + Math.cos(ang) * r;
            double z = c.z + Math.sin(ang) * r;
            lvl.sendParticles(particle, x, c.y + 0.15, z, 1, 0, 0.05, 0, speed);
        }
    }

    private static void line(ServerLevel lvl, Vec3 a, Vec3 b, ParticleOptions particle, int perBlock) {
        Vec3 d = b.subtract(a);
        int n = Math.max(2, (int) (d.length() * perBlock));
        for (int i = 0; i <= n; i++) {
            Vec3 pt = a.add(d.scale(i / (double) n));
            lvl.sendParticles(particle, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void actionBar(ServerPlayer p, Component msg) {
        p.displayClientMessage(msg, true);
    }
}
