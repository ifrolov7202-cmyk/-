package com.elementalarsenal.skill;

import com.elementalarsenal.Element;
import com.elementalarsenal.effect.ElementalEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.weaponinnate.WeaponInnateSkill;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

import java.util.List;

/**
 * Врождённый навык стихийного оружия: анимация Epic Fight + стихийная техника поверх неё.
 */
public class ElementalArtSkill extends WeaponInnateSkill {
    private final Element element;

    public ElementalArtSkill(SkillBuilder<? extends WeaponInnateSkill> builder, Element element) {
        super(builder);
        this.element = element;
    }

    @Override
    public void executeOnServer(SkillContainer container, FriendlyByteBuf args) {
        ServerPlayerPatch executor = container.getServerExecutor();
        ServerPlayer player = executor.getOriginal();
        ElementalEffects.beforeArt(player, element);
        executor.playAnimationSynchronized(element.skillAnimation(), 0.0F);
        ElementalEffects.castArt(player, element);
        super.executeOnServer(container, args);
    }

    @Override
    public List<Component> getTooltipOnItem(ItemStack stack, CapabilityItem cap, PlayerPatch<?> playerCap) {
        List<Component> list = super.getTooltipOnItem(stack, cap, playerCap);
        list.add(Component.translatable(this.getTranslationKey() + ".details").withStyle(ChatFormatting.GRAY));
        return list;
    }
}
