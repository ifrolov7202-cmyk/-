package com.bamartial.skills;

/**
 * Рукопашные стили боя и их боевые искусства (Combat Art, клавиша Battle Arts Z).
 */
public enum StyleKind {
    //          стиль              боевое искусство       +урон рукой
    BOXING     ("boxer",           "crushing_uppercut",   2.0f),
    KARATE     ("karateka",        "kiai",                3.0f),
    MUAY_THAI  ("nak_muay",        "flying_knee",         2.5f),
    KUNG_FU    ("kung_fu_master",  "chi_palm",            2.0f),
    TAEKWONDO  ("taekwondoin",     "tornado_kick",        2.0f),
    JUDO       ("judoka",          "ippon",               2.5f);

    public final String styleId;
    public final String artId;
    public final float unarmedBonus;

    StyleKind(String styleId, String artId, float unarmedBonus) {
        this.styleId = styleId;
        this.artId = artId;
        this.unarmedBonus = unarmedBonus;
    }
}
