package com.bamartial.skills;

import net.forixaim.battle_arts_api.battle_arts_skills.BattleArtsSkillSlots;
import net.forixaim.battle_arts_api.battle_arts_skills.active.burst_arts.BurstArt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

/**
 * «Шквал ударов» — взрывное искусство всех рукопашных стилей (клавиша Battle Arts «Burst Art», по умолчанию C):
 * 10 ударов за секунду по одной цели.
 */
public class FlurryBurstArt extends BurstArt {
    public FlurryBurstArt(SkillBuilder<? extends BurstArt> builder) {
        super(builder);
        allowedWeapons.add(CapabilityItem.WeaponCategories.FIST);
    }

    @Override
    public boolean canExecute(SkillContainer container) {
        return container.getExecutor().getOriginal().getMainHandItem().isEmpty()
                && container.getExecutor().getSkill(BattleArtsSkillSlots.BATTLE_STYLE).getSkill() instanceof MartialBattleStyle
                && super.canExecute(container);
    }

    @Override
    public boolean shouldDraw(SkillContainer container) {
        return container.getExecutor().getOriginal().getMainHandItem().isEmpty();
    }

    @Override
    public void executeOnServer(SkillContainer container, FriendlyByteBuf args) {
        ServerPlayer player = container.getServerExecutor().getOriginal();
        if (Techniques.flurry(player)) {
            super.executeOnServer(container, args);
        }
    }
}
