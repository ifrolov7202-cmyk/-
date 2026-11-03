package com.martialarts.network;

import com.martialarts.client.ClientData;
import com.martialarts.data.MartialData;
import com.martialarts.skill.Skill;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: состояние игрока для HUD.
 * ClientData не использует клиентские классы Minecraft, поэтому пакет безопасен для сервера.
 */
public class SyncPacket {
    private final float chi;
    private final float maxChi;
    private final int level;
    private final int xp;
    private final int xpNeeded;
    private final int style;
    private final int learnedMask;
    private final int combo;
    private final long guardEnd;
    private final long[] cooldownEnd;

    public SyncPacket(MartialData d) {
        this.chi = d.chi;
        this.maxChi = d.maxChi();
        this.level = d.level;
        this.xp = d.xp;
        this.xpNeeded = d.level >= MartialData.MAX_LEVEL ? 0 : d.xpForNext();
        this.style = d.style.id;
        this.learnedMask = d.learnedMask;
        this.combo = d.combo;
        this.guardEnd = d.guardEnd;
        this.cooldownEnd = d.cooldownEnd.clone();
    }

    public SyncPacket(FriendlyByteBuf buf) {
        this.chi = buf.readFloat();
        this.maxChi = buf.readFloat();
        this.level = buf.readVarInt();
        this.xp = buf.readVarInt();
        this.xpNeeded = buf.readVarInt();
        this.style = buf.readVarInt();
        this.learnedMask = buf.readVarInt();
        this.combo = buf.readVarInt();
        this.guardEnd = buf.readLong();
        int n = buf.readVarInt();
        this.cooldownEnd = new long[Skill.values().length];
        for (int i = 0; i < n; i++) {
            long v = buf.readLong();
            if (i < cooldownEnd.length) cooldownEnd[i] = v;
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(chi);
        buf.writeFloat(maxChi);
        buf.writeVarInt(level);
        buf.writeVarInt(xp);
        buf.writeVarInt(xpNeeded);
        buf.writeVarInt(style);
        buf.writeVarInt(learnedMask);
        buf.writeVarInt(combo);
        buf.writeLong(guardEnd);
        buf.writeVarInt(cooldownEnd.length);
        for (long v : cooldownEnd) buf.writeLong(v);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ClientData.received = true;
        ClientData.chi = chi;
        ClientData.maxChi = maxChi;
        ClientData.level = level;
        ClientData.xp = xp;
        ClientData.xpNeeded = xpNeeded;
        ClientData.style = style;
        ClientData.learnedMask = learnedMask;
        ClientData.combo = combo;
        ClientData.guardEnd = guardEnd;
        System.arraycopy(cooldownEnd, 0, ClientData.cooldownEnd, 0,
                Math.min(cooldownEnd.length, ClientData.cooldownEnd.length));
    }
}
