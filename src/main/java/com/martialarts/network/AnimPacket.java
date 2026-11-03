package com.martialarts.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиенты: проиграть анимацию на игроке (видят он сам и все, кто рядом).
 */
public class AnimPacket {
    private final int entityId;
    private final String animation;

    public AnimPacket(int entityId, String animation) {
        this.entityId = entityId;
        this.animation = animation;
    }

    public AnimPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.animation = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(animation, 64);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // клиентский класс загружается только на клиенте
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.martialarts.client.ClientAnimations.play(entityId, animation));
    }
}
