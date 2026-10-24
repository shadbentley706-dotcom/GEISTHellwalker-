package com.geist.network;

import com.geist.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: the Z key was pressed while carrying the G.E.I.S.T. Visor but not wearing it. Puts the
 * visor on; a helmet that was being worn goes into the slot the visor came from.
 */
public class EquipVisorPacket {
    public static void encode(EquipVisorPacket msg, FriendlyByteBuf buf) {}

    public static EquipVisorPacket decode(FriendlyByteBuf buf) {
        return new EquipVisorPacket();
    }

    public static void handle(EquipVisorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) equip(p);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void equip(ServerPlayer p) {
        if (p.isSpectator() || !p.isAlive()) return;
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (head.is(ModItems.GEIST_VISOR.get())) return;
        if (!head.isEmpty() && EnchantmentHelper.hasBindingCurse(head) && !p.isCreative()) {
            p.displayClientMessage(Component.translatable("message.geist.visor_bound").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Inventory inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.items.size() && slot < 0; i++) {           // hotbar first, then the backpack
            if (inv.getItem(i).is(ModItems.GEIST_VISOR.get())) slot = i;
        }
        if (slot < 0 && inv.getItem(Inventory.SLOT_OFFHAND).is(ModItems.GEIST_VISOR.get())) slot = Inventory.SLOT_OFFHAND;
        if (slot < 0) return;
        ItemStack stack = inv.getItem(slot);
        ItemStack visor = stack.split(1);
        if (stack.isEmpty()) inv.setItem(slot, head.isEmpty() ? ItemStack.EMPTY : head);
        else if (!head.isEmpty() && !inv.add(head)) p.drop(head, false);
        p.setItemSlot(EquipmentSlot.HEAD, visor);                          // plays the equip sound
    }
}
