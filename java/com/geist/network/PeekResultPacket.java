package com.geist.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> client: the contents of a peeked container. */
public class PeekResultPacket {
    final BlockPos pos;
    final int size;
    final boolean sealed;
    final List<Integer> slots;
    final List<ItemStack> items;

    public PeekResultPacket(BlockPos pos, int size, boolean sealed, List<Integer> slots, List<ItemStack> items) {
        this.pos = pos;
        this.size = size;
        this.sealed = sealed;
        this.slots = slots;
        this.items = items;
    }

    public static void encode(PeekResultPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.size);
        buf.writeBoolean(msg.sealed);
        buf.writeVarInt(msg.items.size());
        for (int i = 0; i < msg.items.size(); i++) {
            buf.writeVarInt(msg.slots.get(i));
            buf.writeItem(msg.items.get(i));
        }
    }

    public static PeekResultPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int size = buf.readVarInt();
        boolean sealed = buf.readBoolean();
        int n = buf.readVarInt();
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            slots.add(buf.readVarInt());
            items.add(buf.readItem());
        }
        return new PeekResultPacket(pos, size, sealed, slots, items);
    }

    public static void handle(PeekResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.geist.client.ChestPeek.receive(msg.pos, msg.size, msg.sealed, msg.slots, msg.items)));
        ctx.get().setPacketHandled(true);
    }
}
