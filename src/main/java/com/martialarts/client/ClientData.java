package com.martialarts.client;

import com.martialarts.skill.Skill;

/**
 * Копия данных игрока на клиенте (только для отображения HUD).
 * Здесь намеренно нет импортов клиентских классов — класс загружается и на сервере.
 */
public final class ClientData {
    public static boolean received = false;
    public static float chi = 100;
    public static float maxChi = 100;
    public static int level = 1;
    public static int xp = 0;
    public static int xpNeeded = 45;
    public static int style = 0;
    public static int learnedMask = 0;
    public static int combo = 0;
    public static long guardEnd = 0;
    public static final long[] cooldownEnd = new long[Skill.values().length];

    private ClientData() {}
}
