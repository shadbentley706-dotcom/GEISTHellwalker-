package com.geist.network;

import com.geist.event.ExoEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the exo-frame leapt (LEAP) or came down hard (LANDED, with the drop height). */
public class ExoActionPacket {
    public static final int LEAP = 0, LANDED = 1;
    private final int action;
    private final float value;

    public ExoActionPacket(int action, float value) {
        this.action = action;
        this.value = value;
    }

    public static void encode(ExoActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeFloat(msg.value);
    }

    public static ExoActionPacket decode(FriendlyByteBuf buf) {
        return new ExoActionPacket(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(ExoActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (msg.action == LEAP) ExoEvents.leap(p);
            else if (msg.action == LANDED) ExoEvents.landed(p, msg.value);
        });
        ctx.get().setPacketHandled(true);
    }
}
