package com.geist.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> nearby clients: play another player's exo-frame leap or three-point landing. */
public class ExoAnimPacket {
    public static final int LEAP = 0, LAND = 1;
    private final int entityId, anim;
    private final float strength;

    public ExoAnimPacket(int entityId, int anim, float strength) {
        this.entityId = entityId;
        this.anim = anim;
        this.strength = strength;
    }

    public static void encode(ExoAnimPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.anim);
        buf.writeFloat(msg.strength);
    }

    public static ExoAnimPacket decode(FriendlyByteBuf buf) {
        return new ExoAnimPacket(buf.readVarInt(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(ExoAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.geist.client.exo.ExoClient.remoteAnim(msg.entityId, msg.anim, msg.strength)));
        ctx.get().setPacketHandled(true);
    }
}
