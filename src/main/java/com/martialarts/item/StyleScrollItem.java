package com.martialarts.item;

import com.martialarts.data.MartialCapability;
import com.martialarts.data.MartialStyle;
import com.martialarts.network.ModNetwork;
import com.martialarts.skill.Skills;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Свиток стиля: ПКМ — выучить стиль (свиток расходуется) или переключиться на него, если уже выучен.
 */
public class StyleScrollItem extends Item {
    private final MartialStyle style;

    public StyleScrollItem(MartialStyle style, Properties properties) {
        super(properties);
        this.style = style;
    }

    public MartialStyle getStyle() {
        return style;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            MartialCapability.get(sp).ifPresent(d -> {
                if (!d.isLearned(style)) {
                    d.learn(style);
                    d.style = style;
                    if (!sp.getAbilities().instabuild) stack.shrink(1);
                    sp.sendSystemMessage(Component.translatable("msg.martialarts.learned", style.displayName())
                            .withStyle(ChatFormatting.GOLD));
                    Skills.sound(sp, SoundEvents.PLAYER_LEVELUP, 0.7f, 0.8f);
                    Skills.addXp(sp, d, 10);
                } else {
                    d.style = style;
                    Skills.actionBar(sp, Component.translatable("msg.martialarts.style_selected", style.displayName()));
                    Skills.sound(sp, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
                }
                d.dirty = true;
                ModNetwork.sync(sp);
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.martialarts." + style.key()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.martialarts.scroll_use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
