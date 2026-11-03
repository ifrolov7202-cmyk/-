package com.martialarts.event;

import com.martialarts.MartialArts;
import com.martialarts.command.MartialCommand;
import com.martialarts.data.MartialCapability;
import com.martialarts.data.MartialData;
import com.martialarts.data.MartialStyle;
import com.martialarts.network.ModNetwork;
import com.martialarts.skill.Skills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.martialarts.MartialConfig;
import com.martialarts.registry.ModItems;
import com.martialarts.skill.MartialAdvancements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MartialArts.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatEvents {
    /** Окно идеального парирования после нажатия стойки (тики). У дзюдо — длиннее. */
    private static final int PARRY_WINDOW = 8;
    private static final int PARRY_WINDOW_JUDO = 12;

    private CombatEvents() {}

    // ---------------------------------------------------------------- данные игрока

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            MartialCapability.Provider provider = new MartialCapability.Provider();
            event.addCapability(MartialCapability.ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        MartialCapability.get(original).ifPresent(oldData ->
                MartialCapability.get(event.getEntity()).ifPresent(newData -> {
                    newData.copyFrom(oldData);
                    if (event.isWasDeath()) newData.chi = newData.maxChi();
                }));
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ModNetwork.sync(sp);
            MartialCapability.get(sp).ifPresent(d -> MartialAdvancements.check(sp, d));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) ModNetwork.sync(sp);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) ModNetwork.sync(sp);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (event.player instanceof ServerPlayer sp) {
            MartialCapability.get(sp).ifPresent(d -> Skills.tick(sp, d));
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MartialCommand.register(event.getDispatcher());
    }

    // ---------------------------------------------------------------- бой

    /** Запоминаем силу замаха до того, как ванилла её сбросит. */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MartialCapability.get(sp).ifPresent(d -> d.lastAttackStrength = sp.getAttackStrengthScale(0.5f));
        }
    }

    /** Уклонение (сразу после рывка) и идеальное парирование (начало стойки) отменяют атаку. */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        DamageSource src = event.getSource();
        if (src.getEntity() == null && src.getDirectEntity() == null) return;

        MartialData d = MartialCapability.get(p).orElse(null);
        if (d == null) return;
        long now = p.level().getGameTime();

        // уклонение
        if (now < d.dodgeUntil) {
            event.setCanceled(true);
            d.dodgeUntil = 0;
            p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
            Skills.sound(p, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0f, 1.5f);
            Skills.actionBar(p, Component.translatable("msg.martialarts.dodge").withStyle(ChatFormatting.AQUA));
            Skills.addXp(p, d, 2);
            MartialAdvancements.award(p, "dodge");
            return;
        }

        int window = d.style == MartialStyle.JUDO ? PARRY_WINDOW_JUDO : PARRY_WINDOW;
        if (!d.isGuarding(now) || now - d.guardStart > window) return;

        event.setCanceled(true);
        d.guardEnd = now; // стойка завершается парированием
        ModNetwork.animate(p, "parry");
        d.chi = Math.min(d.maxChi(), d.chi + 10);

        if (src.getEntity() instanceof LivingEntity attacker && attacker != p) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), p);
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1), p);
            if (attacker.distanceToSqr(p) < 25) {
                if (d.style == MartialStyle.JUDO) {
                    // дзюдо: контрбросок
                    Skills.hit(p, attacker, 2 + d.level * 0.2f);
                    Skills.push(p, attacker, 2.0f, 0.5);
                } else {
                    Skills.push(p, attacker, 1.0f, 0.2);
                }
            }
        }
        p.serverLevel().sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1.2, p.getZ(), 15, 0.4, 0.4, 0.4, 0.4);
        Skills.sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 1.4f);
        Skills.actionBar(p, Component.translatable("msg.martialarts.parry").withStyle(ChatFormatting.GREEN));
        Skills.addXp(p, d, 4);
        MartialAdvancements.award(p, "perfect_parry");
        d.dirty = true;
    }

    // ---------------------------------------------------------------- свитки в мире

    private static final java.util.Set<String> SCROLL_CHESTS = java.util.Set.of(
            "minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/jungle_temple", "minecraft:chests/desert_pyramid",
            "minecraft:chests/stronghold_corridor", "minecraft:chests/stronghold_library",
            "minecraft:chests/village/village_weaponsmith", "minecraft:chests/village/village_temple",
            "minecraft:chests/pillager_outpost", "minecraft:chests/woodland_mansion");

    @SubscribeEvent
    public static void onLootLoad(LootTableLoadEvent event) {
        if (!SCROLL_CHESTS.contains(event.getName().toString())) return;
        if (!MartialConfig.get(MartialConfig.SCROLLS_IN_LOOT, true)) return;
        LootPool.Builder pool = LootPool.lootPool()
                .name("martialarts_scrolls")
                .setRolls(ConstantValue.exactly(1))
                .add(EmptyLootItem.emptyItem().setWeight(64));
        for (var scroll : ModItems.SCROLLS) {
            pool.add(LootItem.lootTableItem(scroll.get()).setWeight(5));
        }
        event.getTable().addPool(pool.build());
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        for (var scroll : ModItems.SCROLLS) {
            event.getRareTrades().add((trader, random) ->
                    new MerchantOffer(new ItemStack(Items.EMERALD, 16), new ItemStack(Items.BOOK),
                            new ItemStack(scroll.get()), 1, 10, 0.05f));
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource src = event.getSource();
        LivingEntity victim = event.getEntity();

        // 1) удар голой рукой
        if (!Skills.skillDamage
                && src.is(DamageTypes.PLAYER_ATTACK)
                && src.getEntity() instanceof ServerPlayer attacker
                && src.getDirectEntity() == attacker
                && attacker.getMainHandItem().isEmpty()) {
            MartialCapability.get(attacker).ifPresent(d ->
                    event.setAmount(Skills.onUnarmedHit(attacker, d, victim, event.getAmount())));
        }

        // 2) защита в стойке
        if (victim instanceof ServerPlayer p && (src.getEntity() != null || src.getDirectEntity() != null)) {
            MartialCapability.get(p).ifPresent(d -> {
                long now = p.level().getGameTime();
                if (!d.isGuarding(now)) return;
                float original = event.getAmount();
                event.setAmount(original * 0.4f);
                d.chi -= original * 1.5f;
                if (d.chi <= 0) {
                    d.chi = 0;
                    d.guardEnd = now;
                    ModNetwork.animate(p, "guard_broken");
                    Skills.sound(p, SoundEvents.SHIELD_BREAK, 1.0f, 1.0f);
                    Skills.actionBar(p, Component.translatable("msg.martialarts.guard_broken").withStyle(ChatFormatting.RED));
                } else {
                    Skills.sound(p, SoundEvents.SHIELD_BLOCK, 0.7f, 0.9f);
                    Skills.addXp(p, d, 1);
                }
                d.dirty = true;
            });
        }
    }

    /** Опыт мастерства за победу голыми руками. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p
                && p.getMainHandItem().isEmpty()
                && event.getEntity() != p) {
            int xp = Math.max(2, (int) (event.getEntity().getMaxHealth() / 4));
            MartialCapability.get(p).ifPresent(d -> Skills.addXp(p, d, xp));
        }
    }

    /** Муай-тай и стойка уменьшают отбрасывание. */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MartialCapability.get(p).ifPresent(d -> {
            float k = 1.0f;
            if (d.style == MartialStyle.MUAY_THAI) k *= 0.6f;
            if (d.isGuarding(p.level().getGameTime())) k *= 0.3f;
            event.setStrength(event.getStrength() * k);
        });
    }

    /** Кунг-фу: урон от падения вдвое меньше. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MartialCapability.get(p).ifPresent(d -> {
            if (d.style == MartialStyle.KUNG_FU) {
                event.setDamageMultiplier(event.getDamageMultiplier() * 0.5f);
            }
        });
    }
}
