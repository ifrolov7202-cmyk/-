package com.bamartial.network;

import com.bamartial.client.ClientUltraInstinct;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> игрок: сколько тиков осталось Ультра инстинкта (0 — выключен). Для экранного эффекта.
 */
public class UltraInstinctPacket {
    private final int ticks;

    public UltraInstinctPacket(int ticks) {
        this.ticks = ticks;
    }

    public UltraInstinctPacket(FriendlyByteBuf buf) {
        this.ticks = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ticks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ClientUltraInstinct.ticks = ticks;
        ClientUltraInstinct.total = Math.max(ticks, 1);
    }
}
