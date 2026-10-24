package com.geist.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> the holder and everyone tracking them: something happened to their MT-7C.
 * SWITCH (a = old mode, b = new mode), THRUST (a = blocks the breach will take, b = mode; 0 blocks = a whiff),
 * LAYER (a = which layer of the breach just broke, b = blocks in it), VENT (a = recovery ticks).
 */
public class Mt7cAnimPacket {
    public static final int SWITCH = 0, THRUST = 1, LAYER = 2, VENT = 3;
    private final int entityId, event, a, b;

    public Mt7cAnimPacket(int entityId, int event, int a, int b) {
        this.entityId = entityId;
        this.event = event;
        this.a = a;
        this.b = b;
    }

    public static void encode(Mt7cAnimPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.event);
        buf.writeVarInt(msg.a);
        buf.writeVarInt(msg.b);
    }

    public static Mt7cAnimPacket decode(FriendlyByteBuf buf) {
        return new Mt7cAnimPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(Mt7cAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.geist.client.mt7c.Mt7cClient.remote(msg.entityId, msg.event, msg.a, msg.b)));
        ctx.get().setPacketHandled(true);
    }
}
