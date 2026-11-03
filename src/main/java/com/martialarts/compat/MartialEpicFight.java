package com.martialarts.compat;

import com.martialarts.MartialArts;
import com.martialarts.data.MartialCapability;
import com.martialarts.data.MartialStyle;
import com.martialarts.item.HandWrapsItem;
import com.martialarts.skill.MartialAdvancements;
import com.martialarts.skill.Skills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.api.forgeevent.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.weaponinnate.WeaponInnateSkill;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.GloveCapability;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener;

import java.util.UUID;
import java.util.function.Function;

/**
 * Интеграция с Epic Fight: тип оружия «бинты», врождённый навык «Техника боевых искусств»,
 * реакция на успешное уклонение Epic Fight.
 */
public final class MartialEpicFight {
    public static final ResourceLocation WRAPS_TYPE = new ResourceLocation(MartialArts.MODID, "martial_wraps");
    private static final UUID DODGE_LISTENER = UUID.fromString("6f1c2a52-6a1e-4d0e-9b3a-5a2f0c7d1e10");

    /** Врождённый навык бинтов (регистрируется Epic Fight). */
    public static yesman.epicfight.skill.Skill TECHNIQUE;

    /** Бинты = кулаки Epic Fight (связки ударов кулаками) + наш врождённый навык. */
    @SuppressWarnings("unchecked")
    public static final Function<Item, CapabilityItem.Builder> WRAPS = (item) -> WeaponCapability.builder()
            .newStyleCombo(CapabilityItem.Styles.ONE_HAND, Animations.FIST_AUTO1, Animations.FIST_AUTO2,
                    Animations.FIST_AUTO3, Animations.FIST_DASH, Animations.FIST_AIR_SLASH)
            .innateSkill(CapabilityItem.Styles.ONE_HAND, (itemstack) -> TECHNIQUE)
            .category(CapabilityItem.WeaponCategories.FIST)
            .constructor(WrapsCapability::new);

    private MartialEpicFight() {}

    /** Конструктор GloveCapability защищённый — открываем его через наследника. */
    public static class WrapsCapability extends GloveCapability {
        public WrapsCapability(CapabilityItem.Builder builder) {
            super(builder);
        }
    }

    public static void onSkillBuild(SkillBuildEvent event) {
        SkillBuildEvent.ModRegistryWorker registry = event.createRegistryWorker(MartialArts.MODID);
        SkillBuilder<WeaponInnateSkill> builder = WeaponInnateSkill.createWeaponInnateBuilder();
        builder.setResource(yesman.epicfight.skill.Skill.Resource.NONE);
        TECHNIQUE = registry.build("martial_technique", MartialTechniqueSkill::new, builder);
    }

    public static void onWeaponPresets(WeaponCapabilityPresetRegistryEvent event) {
        event.getTypeEntry().put(WRAPS_TYPE, WRAPS);
    }

    /** Подписка на успешное уклонение Epic Fight (вызывать при входе/возрождении игрока). */
    public static void attachListeners(ServerPlayer player) {
        ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) return;
        patch.getEventListener().addEventListener(PlayerEventListener.EventType.DODGE_SUCCESS_EVENT, DODGE_LISTENER,
                event -> onDodgeSuccess(player, event.getDamageSource().getEntity()));
    }

    private static void onDodgeSuccess(ServerPlayer p, net.minecraft.world.entity.Entity attacker) {
        MartialCapability.get(p).ifPresent(d -> {
            if (!HandWrapsItem.isFighting(p)) return;
            Skills.addXp(p, d, 2);
            MartialAdvancements.award(p, "dodge");
            // дзюдо: контрбросок после уклонения
            if (d.style == MartialStyle.JUDO && attacker instanceof LivingEntity enemy
                    && enemy != p && enemy.isAlive() && enemy.distanceToSqr(p) < 16) {
                Skills.hit(p, enemy, 2 + d.level * 0.2f);
                Skills.push(p, enemy, 2.0f, 0.5);
                p.serverLevel().sendParticles(ParticleTypes.CRIT, enemy.getX(), enemy.getY() + 1, enemy.getZ(),
                        12, 0.3, 0.3, 0.3, 0.3);
                Skills.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
                Skills.actionBar(p, Component.translatable("msg.martialarts.judo_counter").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        });
    }
}
