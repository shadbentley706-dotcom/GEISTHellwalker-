package com.grapefruit.hellblazer.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> specialist: their whole file with the section (rank, cases, record), whenever it changes. */
public class SyncPacket {
    private final CompoundTag tag;

    public SyncPacket(CompoundTag tag) {
        this.tag = tag;
    }

    public static void encode(SyncPacket m, FriendlyByteBuf buf) {
        buf.writeNbt(m.tag);
    }

    public static SyncPacket decode(FriendlyByteBuf buf) {
        return new SyncPacket(buf.readNbt());
    }

    public static void handle(SyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.grapefruit.hellblazer.client.CaseData.apply(m.tag)));
        ctx.get().setPacketHandled(true);
    }
}
