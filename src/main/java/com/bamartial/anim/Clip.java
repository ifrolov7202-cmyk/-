package com.bamartial.anim;

import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.model.armature.HumanoidArmature;

import java.util.ArrayList;
import java.util.List;

/**
 * Источник ключевых кадров для нашего приёма: файл анимации (Epic Fight, Battle Arts, Weapons of Miracles,
 * Nightfall) и тайминги ударных окон, подобранные под эти кадры их авторами.
 */
public final class Clip {
    /** Сустав, к которому крепится хитбокс. ROOT — хитбокс фиксирован перед бойцом. */
    public enum J {
        ROOT, TOOL_R, TOOL_L, HAND_R, HAND_L, LEG_R, LEG_L, SHOULDER_R;

        public Joint get(HumanoidArmature a) {
            return switch (this) {
                case ROOT -> a.rootJoint;
                case TOOL_R -> a.toolR;
                case TOOL_L -> a.toolL;
                case HAND_R -> a.handR;
                case HAND_L -> a.handL;
                case LEG_R -> a.legR;
                case LEG_L -> a.legL;
                case SHOULDER_R -> a.shoulderR;
            };
        }

        public boolean isLeg() {
            return this == LEG_R || this == LEG_L;
        }
    }

    /** Ударное окно: [preDelay, contact] — хитбокс активен; recovery — можно действовать дальше. */
    public record Win(float start, float antic, float preDelay, float contact, float recovery, float end, J joint) {}

    public final String namespace;
    public final String path;
    public final float convert;
    public final List<Win> wins = new ArrayList<>();
    /** Скорость: basis — относительно скорости атаки (как у обычных ударов), иначе фиксированная. */
    public float speed = 1.0F;
    public boolean basis = false;

    private Clip(String namespace, String path, float convert) {
        this.namespace = namespace;
        this.path = path;
        this.convert = convert;
    }

    /** Один удар: те же параметры, что у конструктора AttackAnimation(convert, antic, preDelay, contact, recovery). */
    public static Clip of(String ns, String path, float convert, float antic, float preDelay, float contact, float recovery, J joint) {
        Clip c = new Clip(ns, path, convert);
        c.wins.add(new Win(0.0F, antic, preDelay, contact, recovery, Float.MAX_VALUE, joint));
        return c;
    }

    /** Несколько ударов за одну анимацию. */
    public static Clip multi(String ns, String path, float convert, Win... wins) {
        Clip c = new Clip(ns, path, convert);
        c.wins.addAll(List.of(wins));
        return c;
    }

    /** Анимация без ударов (уклонение, поза). */
    public static Clip pose(String ns, String path) {
        return new Clip(ns, path, 0.1F);
    }

    public static Win win(float start, float antic, float contact, float recovery, float end, J joint) {
        return new Win(start, antic, antic, contact, recovery, end, joint);
    }

    public Clip basis(float s) {
        this.speed = s;
        this.basis = true;
        return this;
    }

    public Clip fixed(float s) {
        this.speed = s;
        this.basis = false;
        return this;
    }

    /** Тот же файл и окна, но с фиксированной скоростью (для боевых искусств). */
    public Clip withFixedSpeed(float s) {
        Clip c = new Clip(namespace, path, convert);
        c.wins.addAll(wins);
        c.speed = s;
        c.basis = false;
        return c;
    }

    public ResourceLocation file() {
        return new ResourceLocation(namespace, "animmodels/animations/" + path + ".json");
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
