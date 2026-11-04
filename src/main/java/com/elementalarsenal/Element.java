package com.elementalarsenal;

import net.minecraft.ChatFormatting;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.gameasset.Animations;

/**
 * Стихии арсенала: оружие, его навык и характеристики.
 */
public enum Element {
    INFERNO("inferno_greatsword", "inferno_cataclysm", ChatFormatting.GOLD, 7, -3.0F),
    FROST("frostbite_katana", "absolute_zero", ChatFormatting.AQUA, 4, -2.0F),
    STORM("stormcaller_spear", "storm_wrath", ChatFormatting.YELLOW, 5, -2.6F),
    VOID("voidfang_dagger", "shadow_step", ChatFormatting.DARK_PURPLE, 2, -1.4F),
    SOUL("soulreaver_tachi", "soul_harvest", ChatFormatting.DARK_AQUA, 5, -2.4F),
    GALE("galebreaker_sword", "tempest_ascension", ChatFormatting.WHITE, 4, -2.2F);

    public final String itemId;
    public final String skillId;
    public final ChatFormatting color;
    public final int damage;
    public final float speed;

    Element(String itemId, String skillId, ChatFormatting color, int damage, float speed) {
        this.itemId = itemId;
        this.skillId = skillId;
        this.color = color;
        this.damage = damage;
        this.speed = speed;
    }

    /** Анимация навыка. Берётся лениво: поля Animations заполняются при загрузке ресурсов Epic Fight. */
    public AnimationAccessor<? extends AttackAnimation> skillAnimation() {
        return switch (this) {
            case INFERNO -> Animations.THE_GUILLOTINE;
            case FROST -> Animations.BATTOJUTSU_DASH;
            case STORM -> Animations.WRATHFUL_LIGHTING;
            case VOID -> Animations.EVISCERATE_FIRST;
            case SOUL -> Animations.DANCING_EDGE;
            case GALE -> Animations.SWEEPING_EDGE;
        };
    }
}
