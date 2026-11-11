package com.bamartial.network;

import com.bamartial.skills.Techniques;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент -> сервер: атака голой рукой зажата (начать серию ударов), отпущена (закончить, после 5+ ударов —
 * завершающим ударом) или серия прервана (меню, предмет в руке — без завершающего удара).
 */
public class RushPacket {
    public static final byte RELEASE = 0;
    public static final byte HOLD = 1;
    public static final byte CANCEL = 2;

    private final byte action;

    public RushPacket(byte action) {
        this.action = action;
    }

    public RushPacket(FriendlyByteBuf buf) {
        this.action = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        switch (action) {
            case HOLD -> Techniques.startRush(player);
            case RELEASE -> Techniques.stopRush(player, true);
            default -> Techniques.stopRush(player, false);
        }
    }
}
