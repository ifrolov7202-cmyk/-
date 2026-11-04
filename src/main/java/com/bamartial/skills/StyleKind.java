package com.bamartial.skills;

/**
 * Рукопашные стили боя и их боевые искусства (Combat Art, клавиша Battle Arts Z).
 */
public enum StyleKind {
    //          стиль              боевое искусство       +урон рукой  анимация
    BOXING     ("boxer",           "crushing_uppercut",   2.0f,       "sig_boxing"),
    KARATE     ("karateka",        "kiai",                3.0f,       "sig_karate"),
    MUAY_THAI  ("nak_muay",        "flying_knee",         2.5f,       "sig_muay_thai"),
    KUNG_FU    ("kung_fu_master",  "chi_palm",            2.0f,       "sig_kung_fu"),
    TAEKWONDO  ("taekwondoin",     "tornado_kick",        2.0f,       "sig_taekwondo"),
    JUDO       ("judoka",          "ippon",               2.5f,       "sig_judo");

    public final String styleId;
    public final String artId;
    public final float unarmedBonus;
    public final String animation;

    StyleKind(String styleId, String artId, float unarmedBonus, String animation) {
        this.styleId = styleId;
        this.artId = artId;
        this.unarmedBonus = unarmedBonus;
        this.animation = animation;
    }
}
