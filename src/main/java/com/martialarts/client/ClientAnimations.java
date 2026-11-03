package com.martialarts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Анимации техник — анимации Epic Fight, проигрываются на клиентах (только визуально;
 * урон и эффекты считает сервер мода). См. EpicFightCompat.
 */
public final class ClientAnimations {
    private ClientAnimations() {}

    public static void play(int entityId, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (entity instanceof AbstractClientPlayer player) {
            EpicFightCompat.tryPlay(player, name);
        }
    }
}
