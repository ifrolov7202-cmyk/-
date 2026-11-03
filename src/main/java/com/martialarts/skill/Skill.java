package com.martialarts.skill;

import net.minecraft.network.chat.Component;

/**
 * Техники. Все вызываются клавишей врождённого навыка Epic Fight (с бинтами в руке):
 *   просто        — особая техника стиля (15 ур.)
 *   + Shift       — шквал ударов (10 ур.)
 *   + бег         — вихревой удар ногой (5 ур.)
 *   если нужная ещё не открыта — сокрушительный удар.
 * Порядок (ordinal) используется в массиве перезарядок и в сети.
 */
public enum Skill {
    //            id               Ци  перезарядка(тики) уровень
    POWER_STRIKE("power_strike",  25,  60,               1),
    SPIN_KICK   ("spin_kick",     35,  100,              5),
    FLURRY      ("flurry",        60,  300,              10),
    SIGNATURE   ("signature",     50,  240,              15);

    public final String id;
    public final int chiCost;
    public final int cooldown;
    public final int requiredLevel;

    Skill(String id, int chiCost, int cooldown, int requiredLevel) {
        this.id = id;
        this.chiCost = chiCost;
        this.cooldown = cooldown;
        this.requiredLevel = requiredLevel;
    }

    public Component displayName() {
        return Component.translatable("skill.martialarts." + id);
    }

    public static Skill byIndex(int index) {
        Skill[] all = values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    /** Какую технику вызывает клавиша навыка в текущем положении игрока. */
    public static Skill choose(boolean sneaking, boolean sprinting, int level) {
        Skill wanted;
        if (sneaking) wanted = FLURRY;
        else if (sprinting) wanted = SPIN_KICK;
        else wanted = SIGNATURE;
        return level >= wanted.requiredLevel ? wanted : POWER_STRIKE;
    }
}
