package com.bamartial.skills;

import net.forixaim.battle_arts_api.battle_arts_skills.BattleArtsSkillSlots;
import net.forixaim.battle_arts_api.battle_arts_skills.active.combat_arts.CombatArt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

/**
 * Боевое искусство стиля (клавиша Battle Arts «Combat Art», по умолчанию Z). Тратит шкалу метра.
 */
public class MartialCombatArt extends CombatArt {
    private final StyleKind kind;

    public MartialCombatArt(SkillBuilder<? extends CombatArt> builder, StyleKind kind) {
        super(builder);
        this.kind = kind;
        allowedWeapons.add(CapabilityItem.WeaponCategories.FIST);
    }

    @Override
    public boolean canExecute(SkillContainer container) {
        return container.getExecutor().getOriginal().getMainHandItem().isEmpty()
                && container.getExecutor().getSkill(BattleArtsSkillSlots.BATTLE_STYLE).getSkill() instanceof MartialBattleStyle style
                && style.getKind() == kind
                && super.canExecute(container);
    }

    @Override
    public boolean shouldDraw(SkillContainer container) {
        return container.getExecutor().getOriginal().getMainHandItem().isEmpty();
    }

    @Override
    public void executeOnServer(SkillContainer container, FriendlyByteBuf args) {
        ServerPlayer player = container.getServerExecutor().getOriginal();
        if (Techniques.signature(player, kind)) {
            super.executeOnServer(container, args);
        }
    }
}
