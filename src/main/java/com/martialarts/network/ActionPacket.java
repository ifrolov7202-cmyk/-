package com.martialarts.network;

import com.martialarts.compat.MartialEpicFight;
import com.martialarts.skill.Skill;
import com.martialarts.skill.Skills;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент -> сервер: нажата клавиша техники или смены стиля.
 */
public class ActionPacket {
    public static final int CYCLE_STYLE = 100;

    private final int action;

    public ActionPacket(int action) {
        this.action = action;
    }

    public ActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !player.isAlive() || player.isSpectator()) return;

        if (action == CYCLE_STYLE) {
            Skills.cycleStyle(player);
            return;
        }
        Skill skill = Skill.byIndex(action);
        if (skill == null) return;
        if (!MartialEpicFight.isBattleMode(player)) {
            Skills.actionBar(player, Component.translatable("msg.martialarts.need_battle_mode").withStyle(ChatFormatting.YELLOW));
            return;
        }
        Skills.tryUse(player, skill);
    }
}
