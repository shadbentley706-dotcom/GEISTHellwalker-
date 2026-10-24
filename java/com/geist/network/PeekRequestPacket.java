package com.geist.network;

import com.geist.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server: "what's in the container I'm looking at?" Only answered for visor wearers, within
 * 12 blocks, a few times a second. Unopened loot chests are reported as sealed, so peeking never
 * generates (spoils) their loot.
 */
public class PeekRequestPacket {
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private final BlockPos pos;

    public PeekRequestPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PeekRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PeekRequestPacket decode(FriendlyByteBuf buf) {
        return new PeekRequestPacket(buf.readBlockPos());
    }

    public static void handle(PeekRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GEIST_VISOR.get())) return;
            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();
            Long last = LAST.get(player.getUUID());
            if (last != null && now - last < 5) return;                         // at most 4 answers a second
            LAST.put(player.getUUID(), now);
            BlockPos pos = msg.pos;
            if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > 12.5D * 12.5D) return;
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container container)) return;
            boolean sealed = be instanceof RandomizableContainerBlockEntity && be.saveWithoutMetadata().contains("LootTable");
            Container view = container;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock chest) {                  // show both halves of a double chest
                Container both = ChestBlock.getContainer(chest, state, level, pos, true);
                if (both != null) view = both;
            }
            List<Integer> slots = new ArrayList<>();
            List<ItemStack> items = new ArrayList<>();
            if (!sealed) {
                for (int i = 0; i < view.getContainerSize(); i++) {
                    ItemStack s = view.getItem(i);
                    if (!s.isEmpty()) { slots.add(i); items.add(s.copy()); }
                }
            }
            GeistNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PeekResultPacket(pos, view.getContainerSize(), sealed, slots, items));
        });
        ctx.get().setPacketHandled(true);
    }
}
