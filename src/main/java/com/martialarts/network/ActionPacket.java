package com.martialarts.network;

import com.martialarts.skill.Skill;
import com.martialarts.skill.Skills;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент -> сервер: игрок нажал клавишу техники или смены стиля.
 * forward/strafe — направление движения (W/S и A/D) для уклонения.
 */
public class ActionPacket {
    public static final int CYCLE_STYLE = 100;

    private final int action;
    private final float forward;
    private final float strafe;

    public ActionPacket(int action, float forward, float strafe) {
        this.action = action;
        this.forward = forward;
        this.strafe = strafe;
    }

    public ActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readVarInt();
        this.forward = buf.readFloat();
        this.strafe = buf.readFloat();
    }

    public static ActionPacket skill(Skill skill, float forward, float strafe) {
        return new ActionPacket(skill.ordinal(), forward, strafe);
    }

    public static ActionPacket cycleStyle() {
        return new ActionPacket(CYCLE_STYLE, 0, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeFloat(forward);
        buf.writeFloat(strafe);
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
            float f = Float.isFinite(forward) ? Math.max(-1, Math.min(1, forward)) : 0;
            float s = Float.isFinite(strafe) ? Math.max(-1, Math.min(1, strafe)) : 0;
            Skills.tryUse(player, skill, f, s);
        }
    }
}
