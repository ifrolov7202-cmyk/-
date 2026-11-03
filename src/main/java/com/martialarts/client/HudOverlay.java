package com.martialarts.client;

import com.martialarts.data.MartialStyle;
import com.martialarts.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD в левом верхнем углу: стиль и уровень, шкала Ци, шкала опыта, готовность техник, комбо.
 */
public final class HudOverlay {
    private static final int BAR_W = 100;

    public static final IGuiOverlay HUD = (gui, g, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null || !ClientData.received) return;
        if (mc.player.isSpectator()) return;

        Font font = mc.font;
        long now = mc.level.getGameTime();
        if (mc.options.renderDebug) return;
        int x = 6;
        int y = 6;

        // стиль и уровень
        MartialStyle style = MartialStyle.byId(ClientData.style);
        g.drawString(font, Component.translatable("hud.martialarts.title", style.displayName(), ClientData.level),
                x, y, 0xFFFFFF, true);

        // шкала Ци
        y += 11;
        float chiPart = ClientData.maxChi <= 0 ? 0 : Math.min(1f, ClientData.chi / ClientData.maxChi);
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + 6, 0xAA000000);
        g.fill(x, y, x + (int) (BAR_W * chiPart), y + 5, 0xFF33CCFF);
        g.fill(x, y, x + (int) (BAR_W * chiPart), y + 1, 0xFF99EEFF);
        g.drawString(font, Component.translatable("hud.martialarts.chi", (int) ClientData.chi, (int) ClientData.maxChi),
                x + BAR_W + 4, y - 1, 0x88DDFF, true);

        // шкала опыта мастерства
        y += 8;
        float xpPart = ClientData.xpNeeded <= 0 ? 1f : Math.min(1f, (float) ClientData.xp / ClientData.xpNeeded);
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + 3, 0xAA000000);
        g.fill(x, y, x + (int) (BAR_W * xpPart), y + 2, 0xFFFFCC33);

        // техники (клавиша врождённого навыка Epic Fight): просто / Shift / бег
        y += 6;
        int cx = x;
        Skill[] shown = {Skill.SIGNATURE, Skill.FLURRY, Skill.SPIN_KICK, Skill.POWER_STRIKE};
        String[] labels = {"hud.martialarts.tag.signature", "hud.martialarts.tag.flurry",
                "hud.martialarts.tag.spin_kick", "hud.martialarts.tag.power_strike"};
        for (int i = 0; i < shown.length; i++) {
            Skill skill = shown[i];
            String text = Component.translatable(labels[i]).getString();
            int color;
            if (ClientData.level < skill.requiredLevel) {
                text = text + " " + skill.requiredLevel;
                color = 0x555555;
            } else {
                long left = ClientData.cooldownEnd[skill.ordinal()] - now;
                if (left > 0) {
                    text = text + " " + String.format("%.1f", left / 20.0);
                    color = 0xFF5555;
                } else if (ClientData.chi < skill.chiCost && !mc.player.getAbilities().instabuild) {
                    color = 0x5588AA;
                } else {
                    color = 0x55FF55;
                }
            }
            int w = font.width(text) + 6;
            g.fill(cx, y, cx + w, y + 11, 0x88000000);
            g.drawString(font, text, cx + 3, y + 2, color, false);
            cx += w + 2;
        }

        // комбо и стойка — под панелью
        y += 15;
        if (ClientData.combo >= 2) {
            g.drawString(font, Component.translatable("hud.martialarts.combo", ClientData.combo), x, y, 0xFFAA00, true);
        }
    };

    private HudOverlay() {}
}
