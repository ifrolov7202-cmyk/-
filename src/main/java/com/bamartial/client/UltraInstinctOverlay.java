package com.bamartial.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Экранный эффект Ультра инстинкта: тёмные края экрана, белое мерцание, отсчёт времени.
 */
public final class UltraInstinctOverlay {
    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        int ticks = ClientUltraInstinct.ticks;
        if (ticks <= 0 || mc.player == null || mc.options.hideGui) return;

        float pulse = 0.5f + 0.5f * (float) Math.sin((mc.player.tickCount + partialTick) * 0.25);
        int edge = Math.max(24, height / 6);
        int dark = ((int) (150 + 60 * pulse) << 24);
        // тёмные полосы сверху и снизу с градиентом
        g.fillGradient(0, 0, width, edge, dark, 0x00000000);
        g.fillGradient(0, height - edge, width, height, 0x00000000, dark);
        // тонкая белая рамка
        int white = ((int) (40 + 80 * pulse) << 24) | 0xFFFFFF;
        g.fill(0, 0, width, 1, white);
        g.fill(0, height - 1, width, height, white);
        g.fill(0, 0, 1, height, white);
        g.fill(width - 1, 0, width, height, white);

        Font font = mc.font;
        Component title = Component.translatable("msg.ba_martialarts.ui.hud", String.format("%.1f", ticks / 20.0));
        int tw = font.width(title);
        g.drawString(font, title, (width - tw) / 2, 8, 0xFFFFFF, true);
        int barW = 120;
        int x = (width - barW) / 2;
        float part = Math.min(1f, ticks / (float) ClientUltraInstinct.total);
        g.fill(x - 1, 20, x + barW + 1, 24, 0xAA000000);
        g.fill(x, 21, x + (int) (barW * part), 23, 0xFFE8F4FF);
    };

    private UltraInstinctOverlay() {}
}
