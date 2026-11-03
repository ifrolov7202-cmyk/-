package com.martialarts.compat;

import com.martialarts.client.ClientData;
import com.martialarts.data.MartialCapability;
import com.martialarts.data.MartialData;
import com.martialarts.skill.Skill;
import com.martialarts.skill.Skills;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.weaponinnate.WeaponInnateSkill;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Врождённый навык боевых бинтов (клавиша врождённого навыка Epic Fight).
 * Сам выбирает технику: просто — особая техника стиля, Shift — шквал, бег — вихревой удар,
 * если нужная не открыта — сокрушительный удар. Тратит Ци мода, перезарядки — свои.
 */
public class MartialTechniqueSkill extends WeaponInnateSkill {
    public MartialTechniqueSkill(SkillBuilder<? extends WeaponInnateSkill> builder) {
        super(builder);
    }

    @Override
    public boolean isExecutableState(PlayerPatch<?> executor) {
        // в отличие от стандартных навыков — можно и в воздухе
        return !executor.getOriginal().isSpectator() && executor.getEntityState().canUseSkill();
    }

    @Override
    public boolean canExecute(SkillContainer container) {
        if (!super.canExecute(container)) return false;
        Player p = container.getExecutor().getOriginal();
        long now = p.level().getGameTime();
        boolean creative = p.getAbilities().instabuild;

        if (p.level().isClientSide) {
            Skill s = Skill.choose(p.isShiftKeyDown(), p.isSprinting(), ClientData.level);
            return ClientData.cooldownEnd[s.ordinal()] <= now && (creative || ClientData.chi >= s.chiCost);
        }
        MartialData d = MartialCapability.get(p).orElse(null);
        if (d == null) return false;
        Skill s = Skill.choose(p.isShiftKeyDown(), p.isSprinting(), d.level);
        return d.cooldownEnd[s.ordinal()] <= now && (creative || d.chi >= s.chiCost);
    }

    @Override
    public void executeOnServer(SkillContainer container, FriendlyByteBuf args) {
        ServerPlayer p = container.getServerExecutor().getOriginal();
        MartialCapability.get(p).ifPresent(d ->
                Skills.tryUse(p, Skill.choose(p.isShiftKeyDown(), p.isSprinting(), d.level)));
        super.executeOnServer(container, args);
    }

    @Override
    public List<Component> getTooltipOnItem(ItemStack itemstack, CapabilityItem cap, PlayerPatch<?> playerCap) {
        List<Component> list = new ArrayList<>();
        list.add(Component.translatable(this.getTranslationKey()).withStyle(ChatFormatting.WHITE));
        list.add(Component.translatable(this.getTranslationKey() + ".tooltip").withStyle(ChatFormatting.DARK_GRAY));
        return list;
    }

    @Override
    public WeaponInnateSkill registerPropertiesToAnimation() {
        return this;
    }
}
