package com.martialarts.skill;

import net.minecraft.world.entity.player.Player;

/** Боевые искусства работают голыми руками. */
public final class Fighting {
    private Fighting() {}

    public static boolean bareHanded(Player player) {
        return player.getMainHandItem().isEmpty();
    }
}
