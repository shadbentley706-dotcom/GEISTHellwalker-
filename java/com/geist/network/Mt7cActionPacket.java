package com.geist.network;

import com.geist.event.Mt7cEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the MT-7C mode key was pressed (SWITCH: reconfigure the head to the next mode). */
public class Mt7cActionPacket {
    public static final int SWITCH = 0;
    private final int action;

    public Mt7cActionPacket(int action) {
        this.action = action;
    }

    public static void encode(Mt7cActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
    }

    public static Mt7cActionPacket decode(FriendlyByteBuf buf) {
        return new Mt7cActionPacket(buf.readVarInt());
    }

    public static void handle(Mt7cActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && msg.action == SWITCH) Mt7cEvents.switchMode(p);
        });
        ctx.get().setPacketHandled(true);
    }
}
