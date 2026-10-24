package com.geist.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the true local difficulty where the visor wearer stands (sent once a second). */
public class DifficultyPacket {
    private final float local, clamped;

    public DifficultyPacket(float local, float clamped) {
        this.local = local;
        this.clamped = clamped;
    }

    public static void encode(DifficultyPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.local);
        buf.writeFloat(msg.clamped);
    }

    public static DifficultyPacket decode(FriendlyByteBuf buf) {
        return new DifficultyPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(DifficultyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.geist.client.GeistState.setDifficulty(msg.local, msg.clamped)));
        ctx.get().setPacketHandled(true);
    }
}
