package com.bamartial.network;

import com.bamartial.skills.Techniques;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент -> сервер: атака голой рукой зажата (начать серию ударов) или отпущена (закончить).
 */
public class RushPacket {
    private final boolean holding;

    public RushPacket(boolean holding) {
        this.holding = holding;
    }

    public RushPacket(FriendlyByteBuf buf) {
        this.holding = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(holding);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) Techniques.setRush(player, holding);
    }
}
