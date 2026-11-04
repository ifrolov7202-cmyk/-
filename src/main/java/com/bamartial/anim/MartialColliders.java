package com.bamartial.anim;

import com.bamartial.BAMartial;
import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.collider.MultiOBBCollider;
import yesman.epicfight.api.collider.OBBCollider;
import yesman.epicfight.gameasset.ColliderPreset;

/**
 * Хитбоксы приёмов. Координаты модели Epic Fight: Y — вверх, -Z — вперёд от бойца.
 * FIST и KICK крепятся к руке/ноге и следуют за ударом; остальные — фиксированы перед бойцом (корень скелета).
 */
public final class MartialColliders {
    /** Кулак (крепится к руке). */
    public static final Collider FIST = reg("fist", new MultiOBBCollider(3, 0.45D, 0.45D, 0.45D, 0.0D, 0.0D, 0.0D));
    /** Нога (крепится к голени, смещён к стопе). */
    public static final Collider KICK = reg("kick", new MultiOBBCollider(4, 0.45D, 0.45D, 0.45D, 0.0D, 0.6D, 0.0D));
    /** Удар перед собой. */
    public static final Collider STRIKE = reg("strike", new OBBCollider(0.5D, 0.5D, 0.65D, 0.0D, 1.15D, -1.0D));
    /** Рывок / летящее колено. */
    public static final Collider LUNGE = reg("lunge", new OBBCollider(0.7D, 0.7D, 1.0D, 0.0D, 1.0D, -1.1D));
    /** Удар сверху в прыжке. */
    public static final Collider DIVE = reg("dive", new OBBCollider(0.8D, 0.8D, 0.9D, 0.0D, 0.5D, -0.9D));
    /** Тяжёлый удар / захват. */
    public static final Collider HEAVY = reg("heavy", new OBBCollider(0.75D, 0.75D, 1.0D, 0.0D, 1.1D, -1.25D));
    /** Ударная волна (киай, ладонь Ци): ~5 блоков вперёд. */
    public static final Collider WAVE = reg("wave", new OBBCollider(1.1D, 0.9D, 2.4D, 0.0D, 1.1D, -2.6D));
    /** Вращение ногами вокруг себя. */
    public static final Collider SPIN = reg("spin", new OBBCollider(2.0D, 0.9D, 2.0D, 0.0D, 1.0D, 0.0D));
    /** Серия кулаков в упор. */
    public static final Collider BARRAGE = reg("barrage", new OBBCollider(0.55D, 0.55D, 0.7D, 0.0D, 1.2D, -0.95D));

    private MartialColliders() {}

    private static Collider reg(String name, Collider collider) {
        return ColliderPreset.registerCollider(new ResourceLocation(BAMartial.MODID, name), collider);
    }
}
