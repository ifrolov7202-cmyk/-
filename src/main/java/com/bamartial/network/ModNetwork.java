package com.bamartial.network;

import com.bamartial.BAMartial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "4";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BAMartial.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(AnimPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AnimPacket::encode)
                .decoder(AnimPacket::new)
                .consumerMainThread(AnimPacket::handle)
                .add();
        CHANNEL.messageBuilder(UltraInstinctPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UltraInstinctPacket::encode)
                .decoder(UltraInstinctPacket::new)
                .consumerMainThread(UltraInstinctPacket::handle)
                .add();
        CHANNEL.messageBuilder(RushPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RushPacket::encode)
                .decoder(RushPacket::new)
                .consumerMainThread(RushPacket::handle)
                .add();
    }

    /** Сообщить игроку, сколько тиков осталось Ультра инстинкта (для экранного эффекта). */
    public static void syncUltraInstinct(ServerPlayer player, int ticks) {
        if (player.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new UltraInstinctPacket(ticks));
    }

    /** Клиентский эффект на игроке у всех, кто его видит (остаточный образ и т.п.). */
    public static void fx(ServerPlayer player, String effect) {
        if (player.connection == null) return;
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new AnimPacket(player.getId(), effect));
    }
}
