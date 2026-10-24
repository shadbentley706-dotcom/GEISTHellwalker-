package com.geist.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: something happened with a Zero Light.
 * MODE (a = the new mode), FIRE (a = what kind of arrow went, b = draw power 0..100), HIT (to the archer only:
 * a = how many times the arrow had bounced, b = its kind).
 */
public class ZeroAnimPacket {
    public static final int MODE = 0, FIRE = 1, HIT = 2;
    private final int entityId, event, a, b;

    public ZeroAnimPacket(int entityId, int event, int a, int b) {
        this.entityId = entityId;
        this.event = event;
        this.a = a;
        this.b = b;
    }

    public static void encode(ZeroAnimPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.event);
        buf.writeVarInt(msg.a);
        buf.writeVarInt(msg.b);
    }

    public static ZeroAnimPacket decode(FriendlyByteBuf buf) {
        return new ZeroAnimPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ZeroAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.geist.client.zero.ZeroClient.remote(msg.entityId, msg.event, msg.a, msg.b)));
        ctx.get().setPacketHandled(true);
    }
}
