package com.bamartial.skills;

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
        if (!player.getMainHandItem().isEmpty() || MartialSkills.style(player) == null) return false;
        if (!container.getExecutor().isLogicalClient() && !UltraInstinct.canActivate(player)) return false;
        return super.canExecute(container);
    }

    @Override
    public boolean shouldDraw(SkillContainer container) {
        return container.getExecutor().getOriginal().getMainHandItem().isEmpty();
    }

    @Override
    public void executeOnServer(SkillContainer container, FriendlyByteBuf args) {
        ServerPlayer player = container.getServerExecutor().getOriginal();
        if (UltraInstinct.activate(player, false)) {
            super.executeOnServer(container, args);
        }
    }
}
