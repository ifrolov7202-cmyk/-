package com.martialarts.network;

import com.martialarts.MartialArts;
import com.martialarts.data.MartialCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "4";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MartialArts.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(SyncPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPacket::encode)
                .decoder(SyncPacket::new)
                .consumerMainThread(SyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(AnimPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AnimPacket::encode)
                .decoder(AnimPacket::new)
                .consumerMainThread(AnimPacket::handle)
                .add();
    }

    /** Проиграть анимацию на игроке — у него самого и у всех, кто его видит. */
    public static void animate(ServerPlayer player, String animation) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new AnimPacket(player.getId(), animation));
    }

    public static void sync(ServerPlayer player) {
        MartialCapability.get(player).ifPresent(data -> {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncPacket(data));
            data.dirty = false;
        });
    }
}
