package com.bamartial.mixin;

import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * На выделенном сервере (и при игре по сети) Epic Fight 20.14.16 при входе шлёт серверу пакет со списком анимаций.
 * С большим набором аддонов (Nightfall, Weapons of Miracles, Battle Arts, Annoying Villagers) он около 90 КБ, а ванильный
 * предел пакета от клиента 32 КБ, и игрока сразу выкидывает («Payload may not be larger than 32767 bytes»).
 * Поднимаем предел до 1 МБ, как у пакетов от сервера к клиенту. В одиночной игре пакеты не кодируются, там ничего не меняется.
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public class ServerboundCustomPayloadMixin {
    @ModifyConstant(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", constant = @Constant(intValue = 32767), require = 0)
    private int ba_martialarts$biggerPayload(int vanilla) {
        return 1048576;
    }
}
