package com.martialarts.client;

import com.martialarts.MartialArts;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/**
 * Проигрывание анимаций боевых искусств через библиотеку PlayerAnimator.
 * Сами анимации лежат в assets/martialarts/player_animation/*.json
 */
public final class ClientAnimations {
    public static final ResourceLocation LAYER = new ResourceLocation(MartialArts.MODID, "martial_layer");

    /** Анимации, в которых руки видно и от первого лица. */
    private static final Set<String> FIRST_PERSON_ARMS = Set.of(
            "power_strike", "guard", "parry", "flurry", "jab_right", "jab_left",
            "finisher_boxing", "finisher_karate", "finisher_muay_thai", "finisher_kung_fu", "finisher_judo",
            "sig_none", "sig_boxing", "sig_karate", "sig_kung_fu", "sig_judo");

    private ClientAnimations() {}

    /** Вызывается один раз при запуске клиента. Высокий приоритет — поверх анимаций ванилы. */
    public static void registerLayer() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1000, player -> new ModifierLayer<>());
    }

    @SuppressWarnings("unchecked")
    public static void play(int entityId, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof AbstractClientPlayer player)) return;

        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(new ResourceLocation(MartialArts.MODID, name));
        if (anim == null) return;

        IAnimation stored = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (!(stored instanceof ModifierLayer<?> raw)) return;
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;

        KeyframeAnimationPlayer animPlayer = new KeyframeAnimationPlayer(anim);
        if (FIRST_PERSON_ARMS.contains(name)) {
            animPlayer.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            animPlayer.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        }
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.INOUTSINE), animPlayer);
    }
}
