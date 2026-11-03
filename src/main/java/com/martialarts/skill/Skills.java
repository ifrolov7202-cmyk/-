package com.martialarts.skill;

import com.martialarts.data.MartialCapability;
import com.martialarts.data.MartialData;
import com.martialarts.data.MartialStyle;
import com.martialarts.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Вся серверная логика боевых искусств: техники, комбо, опыт, Ци.
 */
public final class Skills {
    /** Пока true — урон наносит техника (бонус удара рукой и комбо не считаются). Только серверный поток. */
    public static boolean skillDamage = false;

    private Skills() {}

    // =====================================================================
    //  Использование техник
    // =====================================================================

    public static void tryUse(ServerPlayer p, Skill skill) {
        MartialData d = MartialCapability.get(p).orElse(null);
        if (d == null) return;
        long now = p.level().getGameTime();

        if (d.level < skill.requiredLevel) {
            actionBar(p, Component.translatable("msg.martialarts.need_level", skill.displayName(), skill.requiredLevel)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (d.cooldownEnd[skill.ordinal()] > now || d.flurryTicks > 0) return;
        if (skill.needsEmptyHand && !p.getMainHandItem().isEmpty()) {
            actionBar(p, Component.translatable("msg.martialarts.empty_hand").withStyle(ChatFormatting.RED));
            return;
        }
        float cost = p.getAbilities().instabuild ? 0 : skill.chiCost;
        if (d.chi < cost) {
            actionBar(p, Component.translatable("msg.martialarts.no_chi").withStyle(ChatFormatting.AQUA));
            return;
        }

        boolean used = switch (skill) {
            case DASH -> dash(p, d);
            case POWER_STRIKE -> powerStrike(p, d);
            case GUARD -> guard(p, d, now);
            case SPIN_KICK -> spinKick(p, d);
            case FLURRY -> flurry(p, d);
        };

        if (used) {
            d.chi -= cost;
            d.cooldownEnd[skill.ordinal()] = now + skill.cooldown;
            d.dirty = true;
            ModNetwork.sync(p);
        }
    }

    /** Рывок: резкое перемещение в сторону взгляда. */
    private static boolean dash(ServerPlayer p, MartialData d) {
        if (p.isPassenger() || p.isFallFlying()) return false;
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0E-4) return false;
        dir = dir.normalize();

        double power = d.style == MartialStyle.KUNG_FU ? 2.2 : 1.5;
        double y = p.onGround() ? 0.25 : Math.max(p.getDeltaMovement().y, 0.1);
        p.setDeltaMovement(dir.x * power, y, dir.z * power);
        p.hurtMarked = true;
        p.fallDistance = 0;
        ModNetwork.animate(p, "dash");

        ServerLevel lvl = p.serverLevel();
        lvl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
        sound(p, SoundEvents.PHANTOM_FLAP, 0.8f, 1.4f);
        return true;
    }

    /** Сокрушительный удар: сильный удар по цели перед игроком с отбрасыванием. */
    private static boolean powerStrike(ServerPlayer p, MartialData d) {
        LivingEntity t = findTarget(p, 4.0);
        if (t == null) {
            actionBar(p, Component.translatable("msg.martialarts.no_target").withStyle(ChatFormatting.GRAY));
            return false;
        }
        float dmg = 5 + d.level * 0.5f;
        if (d.style == MartialStyle.KARATE) dmg *= 1.5f;

        p.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.animate(p, "power_strike");
        hit(p, t, dmg);
        push(p, t, d.style == MartialStyle.KARATE ? 2.2f : 1.6f, 0.35);

        ServerLevel lvl = p.serverLevel();
        double y = t.getY() + t.getBbHeight() * 0.5;
        lvl.sendParticles(ParticleTypes.EXPLOSION, t.getX(), y, t.getZ(), 1, 0, 0, 0, 0);
        lvl.sendParticles(ParticleTypes.CRIT, t.getX(), y, t.getZ(), 20, 0.3, 0.3, 0.3, 0.4);
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
        sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
        addXp(p, d, 3);
        return true;
    }

    /** Стойка: 2 секунды защиты. Первые 0.4 секунды — окно идеального парирования. */
    private static boolean guard(ServerPlayer p, MartialData d, long now) {
        d.guardStart = now;
        d.guardEnd = now + 40;
        ModNetwork.animate(p, "guard");
        sound(p, SoundEvents.ARMOR_EQUIP_GENERIC, 1.0f, 1.2f);
        p.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY() + 1.0, p.getZ(), 15, 0.4, 0.5, 0.4, 0.1);
        actionBar(p, Component.translatable("msg.martialarts.guard").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** Вихревой удар ногой: урон и отбрасывание всем вокруг. */
    private static boolean spinKick(ServerPlayer p, MartialData d) {
        double r = d.style == MartialStyle.KUNG_FU ? 4.5 : 3.0;
        float dmg = 4 + d.level * 0.4f;
        if (d.style == MartialStyle.MUAY_THAI) dmg *= 1.3f;

        List<LivingEntity> targets = p.level().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(r, 1.0, r),
                e -> isValidTarget(p, e) && e.distanceToSqr(p) <= r * r);
        for (LivingEntity t : targets) {
            hit(p, t, dmg);
            push(p, t, 1.2f, 0.25);
        }

        p.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.animate(p, "spin_kick");
        ServerLevel lvl = p.serverLevel();
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16.0;
            lvl.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    p.getX() + Math.cos(a) * r * 0.7, p.getY() + 0.8, p.getZ() + Math.sin(a) * r * 0.7,
                    1, 0, 0, 0, 0);
        }
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
        if (!targets.isEmpty()) addXp(p, d, 2 + targets.size());
        return true;
    }

    /** Шквал ударов: серия из 10 быстрых ударов по одной цели за секунду. */
    private static boolean flurry(ServerPlayer p, MartialData d) {
        LivingEntity t = findTarget(p, 3.5);
        if (t == null) {
            actionBar(p, Component.translatable("msg.martialarts.no_target").withStyle(ChatFormatting.GRAY));
            return false;
        }
        d.flurryTicks = 20;
        d.flurryTarget = t.getId();
        ModNetwork.animate(p, "flurry");
        actionBar(p, Component.translatable("msg.martialarts.flurry").withStyle(ChatFormatting.GOLD));
        return true;
    }

    private static void tickFlurry(ServerPlayer p, MartialData d) {
        if (d.flurryTicks <= 0) return;
        d.flurryTicks--;

        Entity e = p.level().getEntity(d.flurryTarget);
        if (!(e instanceof LivingEntity t) || !t.isAlive() || p.distanceToSqr(t) > 36) {
            d.flurryTicks = 0;
            return;
        }
        if (d.flurryTicks % 2 != 0) return;

        boolean last = d.flurryTicks == 0;
        p.swing((d.flurryTicks / 2) % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        float dmg = last ? 6 + d.level * 0.3f : 1.0f + d.level * 0.1f;
        hit(p, t, dmg);

        ServerLevel lvl = p.serverLevel();
        double y = t.getY() + t.getBbHeight() * 0.6;
        if (last) {
            push(p, t, 2.5f, 0.4);
            lvl.sendParticles(ParticleTypes.EXPLOSION, t.getX(), y, t.getZ(), 1, 0, 0, 0, 0);
            sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
            addXp(p, d, 5);
        } else {
            // удерживаем цель рядом, чтобы все удары серии попали
            t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 1.0, 0.2));
            t.hurtMarked = true;
            lvl.sendParticles(ParticleTypes.CRIT, t.getX(), y, t.getZ(), 6, 0.25, 0.25, 0.25, 0.3);
            sound(p, SoundEvents.PLAYER_ATTACK_WEAK, 0.6f, 1.2f + p.getRandom().nextFloat() * 0.4f);
        }
    }

    // =====================================================================
    //  Удары голой рукой и комбо
    // =====================================================================

    /** Вызывается из LivingHurtEvent, когда игрок бьёт пустой рукой. Возвращает новый урон. */
    public static float onUnarmedHit(ServerPlayer p, MartialData d, LivingEntity target, float amount) {
        float strength = d.lastAttackStrength;
        float bonus = (d.style.unarmedBonus + (d.level - 1) * 0.3f) * strength * strength;
        amount += bonus;

        // комбо считается только для полностью заряженных ударов
        if (strength < 0.9f) return amount;

        long now = p.level().getGameTime();
        d.combo = (now - d.lastHitTick <= 30) ? d.combo + 1 : 1;
        d.lastHitTick = now;
        d.chi = Math.min(d.maxChi(), d.chi + 3);

        int xp = 1;
        if (d.combo % 3 == 0) {
            amount += comboFinisher(p, d, target);
            xp += 2;
        } else {
            ModNetwork.animate(p, d.combo % 3 == 1 ? "jab_right" : "jab_left");
        }
        addXp(p, d, xp);
        d.dirty = true;
        return amount;
    }

    /** Добивающий удар (каждый 3-й удар комбо). Эффект зависит от стиля. */
    private static float comboFinisher(ServerPlayer p, MartialData d, LivingEntity t) {
        float extra = 1;
        switch (d.style) {
            case BOXING -> extra = 3 + d.level * 0.2f;
            case KARATE -> extra = 3 + d.level * 0.25f;
            case MUAY_THAI -> {
                extra = 2 + d.level * 0.15f;
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
            }
            case KUNG_FU -> {
                extra = 2 + d.level * 0.15f;
                t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 10, 3), p);
            }
            default -> { }
        }

        ModNetwork.animate(p, d.style == MartialStyle.NONE ? "power_strike" : "finisher_" + d.style.key());
        ServerLevel lvl = p.serverLevel();
        lvl.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(),
                12, 0.3, 0.3, 0.3, 0.3);
        sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
        actionBar(p, Component.translatable("msg.martialarts.finisher." + d.style.key(), d.combo)
                .withStyle(ChatFormatting.GOLD));
        return extra;
    }

    // =====================================================================
    //  Тик игрока, опыт, смена стиля
    // =====================================================================

    public static void tick(ServerPlayer p, MartialData d) {
        long now = p.level().getGameTime();
        float max = d.maxChi();
        if (d.chi > max) d.chi = max;

        if (d.chi < max && !d.isGuarding(now) && d.flurryTicks <= 0) {
            float regen = 0.25f;
            if (d.style == MartialStyle.BOXING) regen *= 1.75f;
            // медитация: присесть и стоять на месте — Ци восстанавливается втрое быстрее
            if (p.isCrouching() && p.getDeltaMovement().horizontalDistanceSqr() < 0.001) {
                regen *= 3;
                if (p.tickCount % 10 == 0) {
                    p.serverLevel().sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(),
                            4, 0.4, 0.4, 0.4, 0.5);
                }
            }
            d.chi = Math.min(max, d.chi + regen);
            if (p.tickCount % 5 == 0) d.dirty = true;
        }

        if (d.combo > 0 && now - d.lastHitTick > 40) {
            d.combo = 0;
            d.dirty = true;
        }

        if (d.isGuarding(now) && p.tickCount % 4 == 0) {
            p.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY() + 1.0, p.getZ(),
                    2, 0.4, 0.5, 0.4, 0.02);
        }

        tickFlurry(p, d);

        if (d.dirty) ModNetwork.sync(p);
    }

    public static void addXp(ServerPlayer p, MartialData d, int amount) {
        if (amount <= 0 || d.level >= MartialData.MAX_LEVEL) return;
        d.xp += amount;
        while (d.level < MartialData.MAX_LEVEL && d.xp >= d.xpForNext()) {
            d.xp -= d.xpForNext();
            d.level++;
            d.chi = d.maxChi();
            p.sendSystemMessage(Component.translatable("msg.martialarts.level_up", d.level).withStyle(ChatFormatting.GOLD));
            for (Skill s : Skill.values()) {
                if (s.requiredLevel == d.level) {
                    p.sendSystemMessage(Component.translatable("msg.martialarts.unlocked",
                            s.displayName(), Component.keybind("key.martialarts." + s.id)).withStyle(ChatFormatting.AQUA));
                }
            }
            sound(p, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.3f);
        }
        if (d.level >= MartialData.MAX_LEVEL) d.xp = 0;
        d.dirty = true;
    }

    public static void cycleStyle(ServerPlayer p) {
        MartialCapability.get(p).ifPresent(d -> {
            MartialStyle[] all = MartialStyle.values();
            if (d.learnedMask == 0) {
                actionBar(p, Component.translatable("msg.martialarts.no_styles").withStyle(ChatFormatting.GRAY));
                return;
            }
            int idx = d.style.ordinal();
            for (int i = 1; i <= all.length; i++) {
                MartialStyle s = all[(idx + i) % all.length];
                if (d.isLearned(s)) {
                    d.style = s;
                    break;
                }
            }
            actionBar(p, Component.translatable("msg.martialarts.style_selected", d.style.displayName()));
            sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
            d.dirty = true;
            ModNetwork.sync(p);
        });
    }

    // =====================================================================
    //  Вспомогательное
    // =====================================================================

    public static boolean hit(ServerPlayer p, LivingEntity t, float dmg) {
        skillDamage = true;
        try {
            t.invulnerableTime = 0;
            return t.hurt(p.damageSources().playerAttack(p), dmg);
        } finally {
            skillDamage = false;
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

    /** Ищет живую цель, на которую смотрит игрок, в пределах range блоков. */
    public static LivingEntity findTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box,
                e -> isValidTarget(p, e) && e.isPickable(), range * range);
        if (hit != null && hit.getEntity() instanceof LivingEntity le && p.hasLineOfSight(le)) {
            return le;
        }
        return null;
    }

    public static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void actionBar(ServerPlayer p, Component msg) {
        p.displayClientMessage(msg, true);
    }
}
