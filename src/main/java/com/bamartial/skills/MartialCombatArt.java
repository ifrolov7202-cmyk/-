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
        boolean base = container.getExecutor().getOriginal().getMainHandItem().isEmpty()
                && container.getExecutor().getSkill(BattleArtsSkillSlots.BATTLE_STYLE).getSkill() instanceof MartialBattleStyle style
                && style.getKind() == kind;
        if (!base) return false;
        // в Ультра инстинкте — без затрат метра
        if (!container.getExecutor().isLogicalClient()
                && container.getExecutor().getOriginal() instanceof ServerPlayer sp && Techniques.freeArtReady(sp)) {
            return true;
        }
        return super.canExecute(container);
    }

    /** Вернуть потраченный метр (искусства в Ультра инстинкте бесплатны). */
    static void refundMeter(ServerPlayer player, float before) {
        var styleContainer = MartialSkills.styleContainer(player);
        float spent = before - MartialSkills.meter(styleContainer);
        if (spent > 0) MartialBattleStyle.addMeter(styleContainer, spent);
    }

    @Override
    public boolean shouldDraw(SkillContainer container) {
        return container.getExecutor().getOriginal().getMainHandItem().isEmpty();
    }

    @Override
    public void executeOnServer(SkillContainer container, FriendlyByteBuf args) {
        ServerPlayer player = container.getServerExecutor().getOriginal();
        boolean free = UltraInstinct.isActive(player);
        if (Techniques.signature(player, kind)) {
            float before = MartialSkills.meter(MartialSkills.styleContainer(player));
            super.executeOnServer(container, args);
            if (free) refundMeter(player, before);
        }
    }
}
