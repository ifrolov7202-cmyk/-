package com.bamartial.network;

import com.bamartial.BAMartial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BAMartial.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(AnimPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AnimPacket::encode)
                .decoder(AnimPacket::new)
                .consumerMainThread(AnimPacket::handle)
                .add();
    }

    /** Проиграть анимацию Epic Fight на игроке у всех, кто его видит (только визуально). */
    public static void animate(ServerPlayer player, String animation) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new AnimPacket(player.getId(), animation));
    }
}
