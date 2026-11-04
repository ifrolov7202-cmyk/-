package com.bamartial.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import yesman.epicfight.particle.EpicFightParticles;

/**
 * Клиентские эффекты Epic Fight, которые нельзя отправить обычным пакетом частиц
 * (остаточный образ хранит id сущности в параметре частицы).
 */
public final class ClientFx {
    private static boolean broken = false;

    private ClientFx() {}

    public static void play(int entityId, String effect) {
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (entity == null) return;
        try {
            if ("afterimage".equals(effect)) {
                mc.level.addParticle(EpicFightParticles.WHITE_AFTERIMAGE.get(), entity.getX(), entity.getY(), entity.getZ(),
                        Double.longBitsToDouble(entity.getId()), 0.0D, 0.0D);
            }
        } catch (Throwable t) {
            broken = true;
            LogUtils.getLogger().warn("[ba_martialarts] client effects disabled: {}", t.toString());
        }
    }
}
