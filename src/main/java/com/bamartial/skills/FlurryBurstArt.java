package com.bamartial.skills;

import net.forixaim.battle_arts_api.battle_arts_skills.BattleArtsSkillSlots;
import net.forixaim.battle_arts_api.battle_arts_skills.active.burst_arts.BurstArt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

/**
 * «Шквал ударов» — взрывное искусство всех рукопашных стилей (клавиша Battle Arts «Burst Art», по умолчанию C):
 * серия из 8 ударов кулаками, держащая врага в оглушении, последний удар сбивает с ног.
 */
public class FlurryBurstArt extends BurstArt {
    public FlurryBurstArt(SkillBuilder<? extends BurstArt> builder) {
        super(builder);
        allowedWeapons.add(CapabilityItem.WeaponCategories.FIST);
    }

    @Override
    public boolean canExecute(SkillContainer container) {
        boolean base = container.getExecutor().getOriginal().getMainHandItem().isEmpty()
                && container.getExecutor().getSkill(BattleArtsSkillSlots.BATTLE_STYLE).getSkill() instanceof MartialBattleStyle;
        if (!base) return false;
        if (!container.getExecutor().isLogicalClient()
                && container.getExecutor().getOriginal() instanceof ServerPlayer sp && Techniques.freeArtReady(sp)) {
            return true;
        }
        return super.canExecute(container);
    }

    @Override
    public boolean shouldDraw(SkillContainer container) {
        return container.getExecutor().getOriginal().getMainHandItem().isEmpty();
    }

    @Override
    public void executeOnServer(SkillContainer container, FriendlyByteBuf args) {
        ServerPlayerPatch patch = container.getServerExecutor();
        ServerPlayer player = patch.getOriginal();
        boolean free = UltraInstinct.isActive(player);
        float before = MartialSkills.meter(MartialSkills.styleContainer(player));
        if (Techniques.performFlurry(patch)) {
            super.executeOnServer(container, args);
            if (free) MartialCombatArt.refundMeter(player, before);
        }
    }
}
