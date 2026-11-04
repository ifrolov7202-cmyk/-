package com.bamartial.skills;

import com.bamartial.BAMartial;
import com.bamartial.MartialConfig;
import com.bamartial.anim.MartialMoves;
import com.bamartial.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.DodgeAnimation;
import yesman.epicfight.api.utils.AttackResult;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageTypeTags;
import yesman.epicfight.world.entity.eventlistener.TakeDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ультра инстинкт (как в Лукизме): тело дерётся само.
 * <ul>
 *   <li>Включение: клавиша Ultimate Art (V) при полной шкале или само — на грани поражения (раз в 5 минут).</li>
 *   <li>Пока активен: уклонения настоящими шагами Epic Fight (с остаточным образом) и контрудар стиля по атакующему,
 *       +40% урона, +25% скорости, быстрее связки, искусства без затрат метра, не отбрасывает.</li>
 *   <li>После: «тело на пределе» — выносливость Epic Fight пуста и не восстанавливается 10 с, бойец медленнее.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BAMartial.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UltraInstinct {
    private static final UUID DAMAGE_ID = UUID.fromString("0b6c8d2e-5a1f-4b7e-9c3d-2f8e1a6b7d01");
    private static final UUID SPEED_ID = UUID.fromString("0b6c8d2e-5a1f-4b7e-9c3d-2f8e1a6b7d02");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("0b6c8d2e-5a1f-4b7e-9c3d-2f8e1a6b7d03");
    private static final UUID KNOCKBACK_ID = UUID.fromString("0b6c8d2e-5a1f-4b7e-9c3d-2f8e1a6b7d04");
    private static final UUID TIRED_ID = UUID.fromString("0b6c8d2e-5a1f-4b7e-9c3d-2f8e1a6b7d05");
    private static final int EXHAUSTION_TICKS = 400;

    private static final class State {
        int ticks;
        int exhausted;
        long autoReadyAt;
        int dodges;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private UltraInstinct() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), id -> new State());
    }

    public static boolean isActive(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.ticks > 0;
    }

    public static boolean canActivate(Player p) {
        State s = STATES.get(p.getUUID());
        return s == null || (s.ticks <= 0 && s.exhausted <= 0);
    }

    // =====================================================================
    //  Включение / выключение
    // =====================================================================

    public static boolean activate(ServerPlayer p, boolean auto) {
        if (!canActivate(p)) return false;
        State s = state(p);
        s.ticks = MartialConfig.get(MartialConfig.UI_DURATION, 15) * 20;
        s.dodges = 0;

        setModifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID, 0.4, AttributeModifier.Operation.MULTIPLY_TOTAL);
        setModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.25, AttributeModifier.Operation.MULTIPLY_TOTAL);
        setModifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, 1.0, AttributeModifier.Operation.ADDITION);
        setModifier(p, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 1.0, AttributeModifier.Operation.ADDITION);
        removeModifier(p, Attributes.MOVEMENT_SPEED, TIRED_ID);
        // тело без сознания не чувствует боли и усталости
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        p.removeEffect(MobEffects.WEAKNESS);
        p.removeEffect(MobEffects.DIG_SLOWDOWN);
        ServerPlayerPatch sp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        if (sp != null) sp.setStamina(sp.getMaxStamina());

        EpicFightParticles.AIR_BURST.get().spawnParticleWithArgument(p.serverLevel(), p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(), 0, -1, 2);
        Techniques.sound(p, EpicFightSounds.FORBIDDEN_STRENGTH.get(), 1.0f, 1.2f);
        Techniques.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.7f);
        ModNetwork.fx(p, "afterimage");

        if (p.connection != null) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("msg.ba_martialarts.ui.title")
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
                    auto ? "msg.ba_martialarts.ui.auto" : "msg.ba_martialarts.ui.manual").withStyle(ChatFormatting.GRAY)));
        }
        // спокойная стойка пробуждения (Weapons of Miracles / Battle Arts), если есть
        if (sp != null && sp.isEpicFightMode() && MartialMoves.awakenAvailable()) {
            sp.playAnimationSynchronized(MartialMoves.AWAKEN, 0.0F);
        }

        ModNetwork.syncUltraInstinct(p, s.ticks);
        MartialAdvancements.award(p, "ultra_instinct");
        if (auto) MartialAdvancements.award(p, "unconscious_fighter");
        return true;
    }

    /** Завершить Ультра инстинкт (exhaust — наступает истощение). */
    public static void forceEnd(ServerPlayer p, boolean exhaust) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.ticks <= 0) return;
        s.ticks = 0;
        end(p, s, exhaust);
    }

    private static void end(ServerPlayer p, State s, boolean exhaust) {
        removeModifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        removeModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID);
        removeModifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
        removeModifier(p, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
        ModNetwork.syncUltraInstinct(p, 0);
        if (!exhaust) return;

        s.exhausted = EXHAUSTION_TICKS;
        setModifier(p, Attributes.MOVEMENT_SPEED, TIRED_ID, -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL);
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(p, PlayerPatch.class);
        if (patch != null) {
            patch.setStamina(0);
            patch.setStaminaRegenAwaitTicks(200);
        }
        Techniques.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.0f, 1.2f);
        Techniques.actionBar(p, Component.translatable("msg.ba_martialarts.ui.end").withStyle(ChatFormatting.DARK_GRAY));
    }

    // =====================================================================
    //  Уклонение (слушатель стиля TAKE_DAMAGE_EVENT_ATTACK)
    // =====================================================================

    /** Тело уходит от удара шагом Epic Fight и отвечает контрударом стиля. */
    public static void tryDodge(ServerPlayerPatch sp, TakeDamageEvent.Attack event, StyleKind kind) {
        ServerPlayer p = sp.getOriginal();
        if (!isActive(p)) return;
        DamageSource src = event.getDamageSource();
        Entity attacker = src.getEntity() != null ? src.getEntity() : src.getDirectEntity();
        if (attacker == null || attacker == p) return;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || src.is(EpicFightDamageTypeTags.BYPASS_DODGE)) return;
        if (sp.getEntityState().knockDown()) return;
        if (p.getRandom().nextFloat() >= MartialConfig.get(MartialConfig.UI_DODGE_CHANCE, 0.75)) return;

        event.setCanceled(true);
        event.setResult(AttackResult.ResultType.MISSED);

        if (sp.isEpicFightMode()) {
            AnimationManager.AnimationAccessor<DodgeAnimation> dodge = switch (p.getRandom().nextInt(3)) {
                case 0 -> MartialMoves.DODGE_LEFT;
                case 1 -> MartialMoves.DODGE_RIGHT;
                default -> MartialMoves.DODGE_BACK;
            };
            if (dodge != null) sp.playAnimationSynchronized(dodge, 0.0F);
        }
        ModNetwork.fx(p, "afterimage");
        Techniques.sound(p, EpicFightSounds.WHOOSH.get(), 0.8f, 1.4f);
        sp.onDodgeSuccess(src, p.position());

        State s = state(p);
        s.dodges++;
        if (s.dodges >= 10) MartialAdvancements.award(p, "untouchable");

        if (attacker instanceof LivingEntity enemy && enemy.isAlive() && enemy.distanceToSqr(p) < 25) {
            Techniques.scheduleCounter(p, kind, enemy, 6);
        }
    }

    // =====================================================================
    //  События
    // =====================================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (s.exhausted > 0 && --s.exhausted == 0) removeModifier(p, Attributes.MOVEMENT_SPEED, TIRED_ID);
        if (s.ticks <= 0) return;

        s.ticks--;
        // серебристый остаточный образ бойца
        if (p.tickCount % 8 == 0) ModNetwork.fx(p, "afterimage");
        if (p.tickCount % 20 == 0) Techniques.sound(p, SoundEvents.WARDEN_HEARTBEAT, 0.5f, 1.4f);
        if (s.ticks == 0) end(p, s, true);
    }

    /** Включение на грани поражения: тело продолжает драться без сознания. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamaged(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || isActive(p)) return;
        if (!MartialConfig.get(MartialConfig.UI_AUTO_TRIGGER, true)) return;
        if (p.isCreative() || p.isSpectator() || MartialSkills.style(p) == null) return;
        if (!p.getMainHandItem().isEmpty() || !canActivate(p)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        State s = state(p);
        long now = p.level().getGameTime();
        if (now < s.autoReadyAt) return;

        float health = p.getHealth();
        float after = health - event.getAmount();
        float threshold = (float) (p.getMaxHealth() * MartialConfig.get(MartialConfig.UI_AUTO_HEALTH, 0.2));
        if (after > threshold) return;

        if (after < 1.0f) event.setAmount(Math.max(0f, health - 1.0f));
        s.autoReadyAt = now + MartialConfig.get(MartialConfig.UI_AUTO_COOLDOWN, 300) * 20L;
        activate(p, true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            forceEnd(p, false);
            State s = STATES.get(p.getUUID());
            if (s != null) s.exhausted = 0;
            removeModifier(p, Attributes.MOVEMENT_SPEED, TIRED_ID);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            forceEnd(p, false);
            removeModifier(p, Attributes.MOVEMENT_SPEED, TIRED_ID);
        }
        STATES.remove(event.getEntity().getUUID());
    }

    // =====================================================================

    private static void setModifier(ServerPlayer p, Attribute attribute, UUID id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        if (inst.getModifier(id) != null) inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, "ba_martialarts_ultra_instinct", amount, op));
    }

    private static void removeModifier(ServerPlayer p, Attribute attribute, UUID id) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst != null && inst.getModifier(id) != null) inst.removeModifier(id);
    }
}
