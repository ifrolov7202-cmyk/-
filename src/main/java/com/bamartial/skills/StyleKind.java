package com.bamartial.skills;

/**
 * Рукопашные стили боя и их боевые искусства (Combat Art, клавиша Battle Arts Z).
 */
public enum StyleKind {
    //          стиль                  боевое искусство       +урон рукой
    BOXING     ("boxer",               "crushing_uppercut",   2.0f),
    KARATE     ("karateka",            "kiai",                3.0f),
    MUAY_THAI  ("nak_muay",            "flying_knee",         2.5f),
    KUNG_FU    ("kung_fu_master",      "chi_palm",            2.0f),
    TAEKWONDO  ("taekwondoin",         "tornado_kick",        2.0f),
    JUDO       ("judoka",              "ippon",               2.5f),
    /** Все школы в одном стиле: связка из 12 ударов разных школ и все боевые искусства. */
    MASTER     ("martial_arts_master", "way_of_the_master",   3.0f);

    public final String styleId;
    public final String artId;
    public final float unarmedBonus;

    StyleKind(String styleId, String artId, float unarmedBonus) {
        this.styleId = styleId;
        this.artId = artId;
        this.unarmedBonus = unarmedBonus;
    }

    /** Книга стиля не попадается в сундуках и у торговца — только собирается из книг других стилей. */
    public boolean craftOnly() {
        return this == MASTER;
    }
}
