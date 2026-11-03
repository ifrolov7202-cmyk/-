package com.martialarts.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/**
 * Необязательная совместимость с Epic Fight.
 * Если Epic Fight установлен и игрок в его боевом режиме — техники проигрывают анимации Epic Fight
 * (только на клиенте, только визуально: урон по-прежнему считает наш мод).
 * Классы Epic Fight трогаются только во вложенном Impl и только если мод загружен.
 * При любой несовместимости версий совместимость молча отключается и работают анимации PlayerAnimator.
 */
public final class EpicFightCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Boolean loaded;
    private static boolean broken = false;

    private EpicFightCompat() {}

    public static boolean isLoaded() {
        if (loaded == null) {
            ModList list = ModList.get();
            loaded = list != null && list.isLoaded("epicfight");
        }
        return loaded;
    }

    /**
     * @return true — анимацию взял на себя Epic Fight (или в его режиме её показывать не нужно),
     *         false — играть нашу анимацию через PlayerAnimator.
     */
    public static boolean tryPlay(AbstractClientPlayer player, String name) {
        if (broken || !isLoaded()) return false;
        try {
            return Impl.play(player, name);
        } catch (Throwable t) {
            broken = true;
            LOGGER.warn("[martialarts] Epic Fight compatibility disabled (incompatible Epic Fight version?): {}", t.toString());
            return false;
        }
    }

    private static final class Impl {
        static boolean play(AbstractClientPlayer player, String name) {
            PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
            if (patch == null || !patch.isEpicFightMode()) return false;

            // обычные удары и добивания Epic Fight анимирует сам; рывки и стойку тоже не трогаем,
            // чтобы его анимации с перемещением не удваивали дистанцию рывка
            if (name.startsWith("jab_") || name.startsWith("finisher_") || name.startsWith("dodge_")
                    || name.equals("dash") || name.equals("guard")) {
                return true;
            }

            AssetAccessor<? extends StaticAnimation> anim = switch (name) {
                case "power_strike", "sig_none", "sig_karate", "sig_kung_fu" -> Animations.FIST_DASH;
                case "flurry" -> Animations.RELENTLESS_COMBO;
                case "spin_kick", "sig_taekwondo", "sig_muay_thai" -> Animations.FIST_AIR_SLASH;
                case "sig_boxing", "sig_judo" -> Animations.FIST_AUTO3;
                case "parry" -> Animations.SWORD_GUARD_ACTIVE_HIT1;
                case "guard_broken" -> Animations.BIPED_HIT_SHORT;
                default -> null;
            };
            if (anim != null) {
                patch.getClientAnimator().playAnimation(anim, 0.0F);
            }
            return true;
        }
    }
}
