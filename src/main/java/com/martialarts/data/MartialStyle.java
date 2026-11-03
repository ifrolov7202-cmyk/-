package com.martialarts.data;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Стили боевых искусств. У каждого — свой бонус к удару голой рукой и особенность.
 *
 * BOXING    — быстрая регенерация Ци, мощные добивания в комбо.
 * KARATE    — самый сильный базовый удар и «Сокрушительный удар» x1.5.
 * MUAY_THAI — добивание замедляет врага, игрок почти не отлетает от ударов, вихревой удар сильнее.
 * KUNG_FU   — длинный рывок, большой радиус вихревого удара, урон от падения вдвое меньше.
 * TAEKWONDO — вихревой удар сильнее и шире, добивание — удар ногой в голову.
 * JUDO      — окно парирования длиннее, парирование бросает врага, добивание — бросок через бедро.
 */
public enum MartialStyle {
    NONE(0, 0xAAAAAA, 0.0f),
    BOXING(1, 0xFF5555, 1.0f),
    KARATE(2, 0xFFFFFF, 1.5f),
    MUAY_THAI(3, 0xFFAA00, 1.25f),
    KUNG_FU(4, 0x55FF55, 1.0f),
    TAEKWONDO(5, 0x5599FF, 1.0f),
    JUDO(6, 0xCC77FF, 1.25f);

    public final int id;
    public final int color;
    public final float unarmedBonus;

    MartialStyle(int id, int color, float unarmedBonus) {
        this.id = id;
        this.color = color;
        this.unarmedBonus = unarmedBonus;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Название стиля, окрашенное в цвет стиля. */
    public MutableComponent displayName() {
        return Component.translatable("style.martialarts." + key()).withStyle(st -> st.withColor(color));
    }

    public static MartialStyle byId(int id) {
        for (MartialStyle s : values()) {
            if (s.id == id) return s;
        }
        return NONE;
    }
}
