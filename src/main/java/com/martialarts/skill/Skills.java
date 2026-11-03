package com.martialarts.skill;

import com.martialarts.MartialConfig;
import com.martialarts.data.MartialCapability;
import com.martialarts.data.MartialData;
import com.martialarts.data.MartialStyle;
import com.martialarts.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Вся серверная логика боевых искусств: техники, комбо, опыт, Ци, пассивные бонусы.
 */
public final class Skills {
    /** Пока true — урон наносит техника (бонус удара рукой и комбо не считаются). Только серверный поток. */
    public static boolean skillDamage = false;

    private static final UUID ATTACK_SPEED_ID = UUID.fromString("6f1c2a52-6a1e-4d0e-9b3a-5a2f0c7d1e01");
    private static final UUID MOVE_SPEED_ID = UUID.fromString("6f1c2a52-6a1e-4d0e-9b3a-5a2f0c7d1e02");

    private Skills() {}

    // =====================================================================
    //  Использование техник
    // =====================================================================

    public static boolean tryUse(ServerPlayer p, Skill skill) {
        MartialData d = MartialCapability.get(p).orElse(null);
        if (d == null) return false;
        long now = p.level().getGameTime();

        if (d.level < skill.requiredLevel) {
            actionBar(p, Component.translatable("msg.martialarts.need_level", skill.displayName(), skill.requiredLevel)
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        if (d.cooldownEnd[skill.ordinal()] > now || d.flurryTicks > 0 || d.leapTicks > 0 || d.tornadoTicks > 0) return false;
        if (!Fighting.bareHanded(p)) {
            actionBar(p, Component.translatable("msg.martialarts.empty_hand").withStyle(ChatFormatting.RED));
            return false;
        }
        float cost = p.getAbilities().instabuild ? 0 : skill.chiCost;
        if (d.chi < cost) {
            actionBar(p, Component.translatable("msg.martialarts.no_chi").withStyle(ChatFormatting.AQUA));
            return false;
        }

        boolean used = switch (skill) {
            case POWER_STRIKE -> powerStrike(p, d);
            case SPIN_KICK -> spinKick(p, d);
            case FLURRY -> flurry(p, d);
            case SIGNATURE -> signature(p, d);
        };

        if (used) {
            d.chi -= cost;
            d.cooldownEnd[skill.ordinal()] = now + skill.cooldown;
            d.dirty = true;
            if (skill == Skill.SIGNATURE) MartialAdvancements.award(p, "signature");
            ModNetwork.sync(p);
        }
        return used;
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

    /** Вихревой удар ногой: урон и отбрасывание всем вокруг. */
    private static boolean spinKick(ServerPlayer p, MartialData d) {
        double r = switch (d.style) {
            case KUNG_FU -> 4.5;
            case TAEKWONDO -> 3.5;
            default -> 3.0;
        };
        float dmg = 4 + d.level * 0.4f;
        if (d.style == MartialStyle.MUAY_THAI) dmg *= 1.3f;
        if (d.style == MartialStyle.TAEKWONDO) dmg *= 1.4f;

        List<LivingEntity> targets = around(p, r);
        for (LivingEntity t : targets) {
            hit(p, t, dmg);
            push(p, t, 1.2f, 0.25);
        }

        p.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.animate(p, "spin_kick");
        ring(p, r, ParticleTypes.SWEEP_ATTACK, 0.8);
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

    // =====================================================================
    //  Особые техники (клавиша H, 15 уровень) — своя у каждого стиля
    // =====================================================================

    private static boolean signature(ServerPlayer p, MartialData d) {
        boolean ok = switch (d.style) {
            case NONE -> chiWave(p, d, 6.0, 6 + d.level * 0.4f, 1.6f, "sig_none");
            case KARATE -> chiWave(p, d, 7.5, 7 + d.level * 0.55f, 2.6f, "sig_karate");
            case BOXING -> uppercut(p, d);
            case MUAY_THAI -> flyingKnee(p, d);
            case KUNG_FU -> chiPalm(p, d);
            case TAEKWONDO -> tornadoKick(p, d);
            case JUDO -> ippon(p, d);
        };
        if (ok) {
            actionBar(p, Component.translatable("msg.martialarts.sig_used",
                    Component.translatable("sig.martialarts." + d.style.key())).withStyle(ChatFormatting.LIGHT_PURPLE));
            addXp(p, d, 6);
        }
        return ok;
    }

    /** Волна Ци / Киай: удар по конусу перед игроком. */
    private static boolean chiWave(ServerPlayer p, MartialData d, double range, float dmg, float knock, String anim) {
        Vec3 look = horizontalLook(p);
        List<LivingEntity> targets = around(p, range);
        int hits = 0;
        for (LivingEntity t : targets) {
            Vec3 to = new Vec3(t.getX() - p.getX(), 0, t.getZ() - p.getZ());
            if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(look) > 0.45) {
                hit(p, t, dmg);
                push(p, t, knock, 0.3);
                hits++;
            }
        }
        ModNetwork.animate(p, anim);
        ServerLevel lvl = p.serverLevel();
        for (int i = 1; i <= (int) range; i++) {
            Vec3 c = p.position().add(look.scale(i)).add(0, 1.0, 0);
            double spread = 0.25 * i;
            lvl.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 4 + i, spread, 0.3, spread, 0.02);
            lvl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.5f);
        return true;
    }

    /** Бокс: сокрушающий апперкот — подбрасывает врага высоко в воздух. */
    private static boolean uppercut(ServerPlayer p, MartialData d) {
        LivingEntity t = findTarget(p, 3.5);
        if (t == null) return noTarget(p);
        p.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.animate(p, "sig_boxing");
        hit(p, t, 10 + d.level * 0.6f);
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

    /** Муай-тай: летящее колено — прыжок к цели (до 10 блоков) и удар при подлёте. */
    private static boolean flyingKnee(ServerPlayer p, MartialData d) {
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
        d.leapTicks = 16;
        d.leapTarget = t.getId();
        ModNetwork.animate(p, "sig_muay_thai");
        sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 0.8f);
        return true;
    }

    private static void tickLeap(ServerPlayer p, MartialData d) {
        if (d.leapTicks <= 0) return;
        d.leapTicks--;
        p.fallDistance = 0;
        Entity e = p.level().getEntity(d.leapTarget);
        if (!(e instanceof LivingEntity t) || !t.isAlive()) {
            d.leapTicks = 0;
            return;
        }
        if (p.distanceToSqr(t) <= 2.6 * 2.6) {
            d.leapTicks = 0;
            hit(p, t, 9 + d.level * 0.5f);
            push(p, t, 2.0f, 0.45);
            p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 1.0, 0.2));
            p.hurtMarked = true;
            ServerLevel lvl = p.serverLevel();
            lvl.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 1.0, t.getZ(), 1, 0, 0, 0, 0);
            lvl.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1.0, t.getZ(), 25, 0.4, 0.4, 0.4, 0.5);
            sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
            sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
        }
    }

    /** Кунг-фу: ладонь Ци — луч энергии на 16 блоков, пробивает до 5 врагов. */
    private static boolean chiPalm(ServerPlayer p, MartialData d) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(16));
        Vec3 blockHit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation();
        double len = eye.distanceTo(blockHit);

        AABB box = new AABB(eye, blockHit).inflate(1.2);
        List<LivingEntity> list = p.level().getEntitiesOfClass(LivingEntity.class, box, e -> isValidTarget(p, e));
        list.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        int hits = 0;
        float dmg = 8 + d.level * 0.5f;
        for (LivingEntity t : list) {
            Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
            Vec3 rel = c.subtract(eye);
            double along = rel.dot(look);
            if (along < 0 || along > len + 0.5) continue;
            double off = rel.subtract(look.scale(along)).length();
            if (off > 1.0 + t.getBbWidth() * 0.5) continue;
            hit(p, t, dmg);
            push(p, t, 1.4f, 0.2);
            if (++hits >= 5) break;
        }
        ModNetwork.animate(p, "sig_kung_fu");
        ServerLevel lvl = p.serverLevel();
        for (double s = 0.8; s < len; s += 0.4) {
            Vec3 c = eye.add(look.scale(s)).add(0, -0.2, 0);
            lvl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.03, 0.03, 0.03, 0.0);
            if (((int) (s * 2.5)) % 3 == 0) lvl.sendParticles(ParticleTypes.GLOW, c.x, c.y, c.z, 1, 0.1, 0.1, 0.1, 0);
        }
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.8f);
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.2f);
        return true;
    }

    /** Тхэквондо: торнадо-кик — прыжок с вращением, 6 ударов по всем вокруг. */
    private static boolean tornadoKick(ServerPlayer p, MartialData d) {
        p.setDeltaMovement(p.getDeltaMovement().x, 0.55, p.getDeltaMovement().z);
        p.hurtMarked = true;
        p.fallDistance = 0;
        d.tornadoTicks = 12;
        ModNetwork.animate(p, "sig_taekwondo");
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.3f);
        return true;
    }

    private static void tickTornado(ServerPlayer p, MartialData d) {
        if (d.tornadoTicks <= 0) return;
        d.tornadoTicks--;
        p.fallDistance = 0;
        if (d.tornadoTicks % 2 != 0) return;
        double r = 3.5;
        for (LivingEntity t : around(p, r)) {
            hit(p, t, 2.5f + d.level * 0.15f);
            push(p, t, d.tornadoTicks == 0 ? 1.8f : 0.5f, 0.15);
        }
        ring(p, r * 0.8, ParticleTypes.SWEEP_ATTACK, 0.9);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.0f + p.getRandom().nextFloat() * 0.5f);
    }

    /** Дзюдо: иппон — бросок через плечо, враг летит за спину и оглушён. */
    private static boolean ippon(ServerPlayer p, MartialData d) {
        LivingEntity t = findTarget(p, 3.5);
        if (t == null) return noTarget(p);
        ModNetwork.animate(p, "sig_judo");
        hit(p, t, 8 + d.level * 0.5f);
        Vec3 back = horizontalLook(p).scale(-1.1);
        t.setDeltaMovement(back.x, 0.75, back.z);
        t.hurtMarked = true;
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3), p);
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1), p);
        ServerLevel lvl = p.serverLevel();
        lvl.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1.0, t.getZ(), 20, 0.4, 0.4, 0.4, 0.3);
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
        sound(p, SoundEvents.GENERIC_BIG_FALL, 1.0f, 0.8f);
        return true;
    }

    // =====================================================================
    //  Шквал ударов (тик)
    // =====================================================================

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
        amount += bonus * damageMult();

        // комбо считается только для полностью заряженных ударов
        if (strength < 0.9f) return amount;

        long now = p.level().getGameTime();
        d.combo = (now - d.lastHitTick <= 30) ? d.combo + 1 : 1;
        d.lastHitTick = now;
        d.chi = Math.min(d.maxChi(), d.chi + 3);

        int xp = 1;
        if (d.combo % 3 == 0) {
            amount += comboFinisher(p, d, target) * damageMult();
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
            case TAEKWONDO -> {
                extra = 3 + d.level * 0.25f;
                t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0), p);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0), p);
            }
            case JUDO -> {
                extra = 2 + d.level * 0.2f;
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), p);
                t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, 2), p);
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
    //  Тик игрока, пассивные бонусы, опыт, смена стиля
    // =====================================================================

    public static void tick(ServerPlayer p, MartialData d) {
        long now = p.level().getGameTime();

        float max = d.maxChi();
        if (d.chi > max) d.chi = max;

        if (d.chi < max && !d.isGuarding(now) && d.flurryTicks <= 0) {
            float regen = 0.25f * (float) MartialConfig.get(MartialConfig.CHI_REGEN_MULTIPLIER, 1.0);
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
        tickLeap(p, d);
        tickTornado(p, d);
        if (p.tickCount % 10 == 0) updatePassives(p, d);

        if (d.dirty) ModNetwork.sync(p);
    }

    /** Мастерство с пустой рукой: быстрее удары и бег. Бокс — ещё быстрее руки, кунг-фу — ноги. */
    private static void updatePassives(ServerPlayer p, MartialData d) {
        boolean on = MartialConfig.get(MartialConfig.PASSIVE_BONUSES, true) && Fighting.bareHanded(p);
        int key = on ? d.level * 16 + d.style.id : -1;
        if (key == d.passiveKey) return;
        d.passiveKey = key;

        double attack = 0;
        double move = 0;
        if (on) {
            attack = d.level * 0.05 + (d.style == MartialStyle.BOXING ? 0.5 : 0);
            move = d.level * 0.005 + (d.style == MartialStyle.KUNG_FU ? 0.05 : 0);
        }
        setModifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, "martialarts_attack_speed",
                attack, AttributeModifier.Operation.ADDITION);
        setModifier(p, Attributes.MOVEMENT_SPEED, MOVE_SPEED_ID, "martialarts_move_speed",
                move, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private static void setModifier(ServerPlayer p, Attribute attribute, UUID id, String name,
                                    double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        if (inst.getModifier(id) != null) inst.removeModifier(id);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    public static void addXp(ServerPlayer p, MartialData d, int amount) {
        if (amount <= 0 || d.level >= MartialData.MAX_LEVEL) return;
        amount = Math.max(1, (int) Math.round(amount * MartialConfig.get(MartialConfig.XP_MULTIPLIER, 1.0)));
        d.xp += amount;
        boolean leveled = false;
        while (d.level < MartialData.MAX_LEVEL && d.xp >= d.xpForNext()) {
            d.xp -= d.xpForNext();
            d.level++;
            leveled = true;
            d.chi = d.maxChi();
            p.sendSystemMessage(Component.translatable("msg.martialarts.level_up", d.level).withStyle(ChatFormatting.GOLD));
            for (Skill s : Skill.values()) {
                if (s.requiredLevel == d.level) {
                    p.sendSystemMessage(Component.translatable("msg.martialarts.unlocked",
                            s.displayName(), Component.translatable("hint.martialarts." + s.id)).withStyle(ChatFormatting.AQUA));
                }
            }
            sound(p, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.3f);
        }
        if (d.level >= MartialData.MAX_LEVEL) d.xp = 0;
        if (leveled) MartialAdvancements.check(p, d);
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
            d.passiveKey = Integer.MIN_VALUE;
            actionBar(p, Component.translatable("msg.martialarts.style_selected", d.style.displayName()));
            sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
            d.dirty = true;
            ModNetwork.sync(p);
        });
    }

    // =====================================================================
    //  Вспомогательное
    // =====================================================================

    private static float damageMult() {
        return (float) MartialConfig.get(MartialConfig.DAMAGE_MULTIPLIER, 1.0);
    }

    public static boolean hit(ServerPlayer p, LivingEntity t, float dmg) {
        skillDamage = true;
        try {
            t.invulnerableTime = 0;
            return t.hurt(p.damageSources().playerAttack(p), dmg * damageMult());
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

    /** Цели вокруг игрока (для техник по площади; игроков — только если разрешено в конфиге). */
    private static List<LivingEntity> around(ServerPlayer p, double r) {
        boolean hitPlayers = MartialConfig.get(MartialConfig.SKILLS_HIT_PLAYERS, true);
        return p.level().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(r, 1.0, r),
                e -> isValidTarget(p, e) && e.distanceToSqr(p) <= r * r && (hitPlayers || !(e instanceof Player)));
    }

    private static Vec3 horizontalLook(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private static void ring(ServerPlayer p, double r, net.minecraft.core.particles.SimpleParticleType type, double y) {
        ServerLevel lvl = p.serverLevel();
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16.0;
            lvl.sendParticles(type, p.getX() + Math.cos(a) * r * 0.7, p.getY() + y, p.getZ() + Math.sin(a) * r * 0.7,
                    1, 0, 0, 0, 0);
        }
    }

    private static boolean noTarget(ServerPlayer p) {
        actionBar(p, Component.translatable("msg.martialarts.no_target").withStyle(ChatFormatting.GRAY));
        return false;
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
