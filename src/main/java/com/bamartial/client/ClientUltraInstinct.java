package com.bamartial.client;

/**
 * Состояние Ультра инстинкта на клиенте (без клиентских импортов — класс безопасен для сервера).
 */
public final class ClientUltraInstinct {
    /** Сколько тиков осталось; -1 — без ограничения (пока боец сам не выйдет), 0 — выключен. */
    public static volatile int ticks = 0;
    public static volatile int total = 1;

    public static boolean active() {
        return ticks != 0;
    }

    public static boolean unlimited() {
        return ticks < 0;
    }

    private ClientUltraInstinct() {}
}
