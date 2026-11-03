package com.martialarts.compat;

import com.martialarts.data.MartialCapability;
import com.martialarts.data.MartialStyle;
import com.martialarts.skill.Fighting;
import com.martialarts.skill.MartialAdvancements;
import com.martialarts.skill.Skills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener;

import java.util.UUID;

/**
 * Интеграция с Epic Fight: проверка боевого режима и реакция на его успешное уклонение.
 */
public final class MartialEpicFight {
    private static final UUID DODGE_LISTENER = UUID.fromString("6f1c2a52-6a1e-4d0e-9b3a-5a2f0c7d1e10");

    private MartialEpicFight() {}

    /** Включён ли у игрока боевой режим Epic Fight (переключается клавишей Epic Fight, по умолчанию R). */
    public static boolean isBattleMode(Player player) {
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
        return patch != null && patch.isEpicFightMode();
    }

    /** Подписка на успешное уклонение Epic Fight (вызывать при входе/возрождении игрока). */
    public static void attachListeners(ServerPlayer player) {
        ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) return;
        patch.getEventListener().addEventListener(PlayerEventListener.EventType.DODGE_SUCCESS_EVENT, DODGE_LISTENER,
                event -> onDodgeSuccess(player, event.getDamageSource().getEntity()));
    }

    private static void onDodgeSuccess(ServerPlayer p, Entity attacker) {
        MartialCapability.get(p).ifPresent(d -> {
            if (!Fighting.bareHanded(p)) return;
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
