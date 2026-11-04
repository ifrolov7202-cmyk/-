package com.bamartial.client;

/**
 * Состояние Ультра инстинкта на клиенте (без клиентских импортов — класс безопасен для сервера).
 */
public final class ClientUltraInstinct {
    public static volatile int ticks = 0;
    public static volatile int total = 1;

    private ClientUltraInstinct() {}
}
