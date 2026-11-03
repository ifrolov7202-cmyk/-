package com.martialarts.skill;

import net.minecraft.network.chat.Component;

/**
 * Активные техники. Порядок (ordinal) используется в сетевых пакетах и в массиве перезарядок.
 */
public enum Skill {
    //            id               Ци  перезарядка(тики) уровень  пустая рука
    DASH        ("dash",          15,  20,               1,       false),
    POWER_STRIKE("power_strike",  25,  60,               2,       true),
    GUARD       ("guard",         10,  30,               3,       true),
    SPIN_KICK   ("spin_kick",     35,  100,              5,       true),
    FLURRY      ("flurry",        60,  300,              10,      true);

    public final String id;
    public final int chiCost;
    public final int cooldown;
    public final int requiredLevel;
    public final boolean needsEmptyHand;

    Skill(String id, int chiCost, int cooldown, int requiredLevel, boolean needsEmptyHand) {
        this.id = id;
        this.chiCost = chiCost;
        this.cooldown = cooldown;
        this.requiredLevel = requiredLevel;
        this.needsEmptyHand = needsEmptyHand;
    }

    public Component displayName() {
        return Component.translatable("skill.martialarts." + id);
    }

    public static Skill byIndex(int index) {
        Skill[] all = values();
        return index >= 0 && index < all.length ? all[index] : null;
    }
}
