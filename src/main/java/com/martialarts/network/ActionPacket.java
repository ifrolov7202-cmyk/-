package com.martialarts.network;

import com.martialarts.skill.Skill;
import com.martialarts.skill.Skills;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент -> сервер: игрок нажал клавишу техники или смены стиля.
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

    public static ActionPacket skill(Skill skill) {
        return new ActionPacket(skill.ordinal());
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
        if (skill != null) {
            Skills.tryUse(player, skill);
        }
    }
}
