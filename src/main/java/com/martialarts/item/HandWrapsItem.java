package com.martialarts.item;

import com.martialarts.client.ClientData;
import com.martialarts.data.MartialData;
import com.martialarts.data.MartialStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Боевые бинты. В Epic Fight — оружие-кулаки: ЛКМ — связки ударов, клавиша врождённого навыка — техники стиля.
 * Стиль бинтов работает, только если игрок его выучил (свитком); иначе — бой без стиля.
 */
public class HandWrapsItem extends Item {
    private final MartialStyle style;

    public HandWrapsItem(MartialStyle style, Properties properties) {
        super(properties);
        this.style = style;
    }

    public MartialStyle getStyle() {
        return style;
    }

    /** Стиль, которым сейчас дерётся игрок (по бинтам в основной руке). */
    public static MartialStyle activeStyle(Player player, MartialData data) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof HandWrapsItem wraps && data.isLearned(wraps.style)) {
            return wraps.style;
        }
        return MartialStyle.NONE;
    }

    /** Дерётся ли игрок голыми руками или в бинтах. */
    public static boolean isFighting(Player player) {
        ItemStack stack = player.getMainHandItem();
        return stack.isEmpty() || stack.getItem() instanceof HandWrapsItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (style != MartialStyle.NONE) {
            tooltip.add(Component.translatable("tooltip.martialarts.wraps_style", style.displayName()));
            boolean learned = (ClientData.learnedMask & (1 << style.id)) != 0;
            if (!learned) {
                tooltip.add(Component.translatable("tooltip.martialarts.wraps_not_learned").withStyle(ChatFormatting.RED));
            }
            tooltip.add(Component.translatable("tooltip.martialarts." + style.key()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.martialarts.wraps_training").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.martialarts.wraps_controls").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.martialarts.signature",
                Component.translatable("sig.martialarts." + style.key())).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
