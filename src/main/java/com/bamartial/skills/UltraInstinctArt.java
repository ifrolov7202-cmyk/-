package com.bamartial.skills;

import com.bamartial.client.ClientUltraInstinct;

import net.forixaim.battle_arts_api.battle_arts_skills.active.ultimate_arts.UltimateArt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

/**
 * «Ультра инстинкт» — абсолютное искусство всех рукопашных стилей (клавиша Battle Arts «Ultimate Art», по умолчанию V).
 * Тратит всю шкалу метра (3 деления). См. {@link UltraInstinct}.
 */
public class UltraInstinctArt extends UltimateArt {
    public UltraInstinctArt(SkillBuilder<? extends UltimateArt> builder) {
        super(builder);
        allowedWeapons.add(CapabilityItem.WeaponCategories.FIST);
    }

    @Override
    public boolean canExecute(SkillContainer container) {
        var player = container.getExecutor().getOriginal();
        // выйти из Ультра инстинкта можно всегда и бесплатно: та же клавиша, в любом положении
        if (active(container)) return true;
        if (!player.getMainHandItem().isEmpty() || MartialSkills.style(player) == null) return false;
        if (!container.getExecutor().isLogicalClient() && !UltraInstinct.canActivate(player)) return false;
        return super.canExecute(container);
    }

    @Override
    public boolean shouldDraw(SkillContainer container) {
        return container.getExecutor().getOriginal().getMainHandItem().isEmpty();
    }

    @Override
    public boolean isExecutableState(yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> executor) {
        if (executor.isLogicalClient() ? ClientUltraInstinct.active() : executor.getOriginal() instanceof ServerPlayer sp && UltraInstinct.isActive(sp)) {
            return true;
        }
        return super.isExecutableState(executor);
    }

    /** Ультра инстинкт сейчас включён (на клиенте — по данным сервера). */
    private static boolean active(SkillContainer container) {
        var executor = container.getExecutor();
        if (executor.isLogicalClient()) return ClientUltraInstinct.active();
        return executor.getOriginal() instanceof ServerPlayer sp && UltraInstinct.isActive(sp);
    }

    @Override
    public void executeOnServer(SkillContainer container, FriendlyByteBuf args) {
        ServerPlayer player = container.getServerExecutor().getOriginal();
        // повторное нажатие — выход, шкала не тратится
        if (UltraInstinct.deactivate(player)) {
            sendExecuted(container);
            return;
        }
        if (UltraInstinct.activate(player, false)) {
            super.executeOnServer(container, args);
        }
    }

    /** Сообщить клиенту, что нажатие обработано (как делает Skill.executeOnServer), не тратя шкалу. */
    private void sendExecuted(SkillContainer container) {
        yesman.epicfight.network.EpicFightNetworkManager.sendToPlayer(
                yesman.epicfight.network.server.SPSkillExecutionFeedback.executed(container.getSlotId()),
                container.getServerExecutor().getOriginal());
    }
}
