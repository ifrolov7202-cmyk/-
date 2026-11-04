package com.bamartial.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/**
 * Анимации техник — анимации Epic Fight, только визуально на клиентах (урон считает сервер мода).
 */
public final class ClientAnimations {
    private static boolean broken = false;

    private ClientAnimations() {}

    public static void play(int entityId, String name) {
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof AbstractClientPlayer player)) return;
        try {
            PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
            if (patch == null || !patch.isEpicFightMode()) return;
            AssetAccessor<? extends StaticAnimation> anim = switch (name) {
                case "sig_karate", "sig_kung_fu" -> Animations.FIST_DASH;
                case "flurry" -> Animations.RELENTLESS_COMBO;
                case "sig_taekwondo", "sig_muay_thai" -> Animations.FIST_AIR_SLASH;
                case "sig_boxing", "sig_judo" -> Animations.FIST_AUTO3;
                case "ui_counter" -> Animations.FIST_AUTO2;
                default -> null;
            };
            if (anim != null) patch.getClientAnimator().playAnimation(anim, 0.0F);
        } catch (Throwable t) {
            broken = true;
            LogUtils.getLogger().warn("[ba_martialarts] animation playback disabled: {}", t.toString());
        }
    }
}
