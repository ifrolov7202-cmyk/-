package com.elementalarsenal.item;

import com.elementalarsenal.ElementalArsenal;
import com.elementalarsenal.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ElementalWeaponItem extends SwordItem {
    private final Element element;

    public ElementalWeaponItem(Element element) {
        super(ArsenalTier.INSTANCE, element.damage, element.speed,
                new Properties().rarity(Rarity.EPIC).fireResistant());
        this.element = element;
    }

    public Element getElement() {
        return element;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String base = "item." + ElementalArsenal.MODID + "." + element.itemId;
        tooltip.add(Component.translatable(base + ".passive").withStyle(element.color));
        tooltip.add(Component.translatable("tooltip.elemental_arsenal.skill",
                Component.translatable("skill." + ElementalArsenal.MODID + "." + element.skillId).withStyle(element.color))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("skill." + ElementalArsenal.MODID + "." + element.skillId + ".tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
