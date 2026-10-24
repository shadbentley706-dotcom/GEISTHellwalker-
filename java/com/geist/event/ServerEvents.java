package com.geist.event;

import com.geist.GeistMod;
import com.geist.network.DifficultyPacket;
import com.geist.network.GeistNetwork;
import com.geist.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Sends each visor wearer the real local difficulty once a second (the client cannot work it out itself). */
@Mod.EventBusSubscriber(modid = GeistMod.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GEIST_VISOR.get())) return;
        DifficultyInstance d = player.level().getCurrentDifficultyAt(player.blockPosition());
        GeistNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DifficultyPacket(d.getEffectiveDifficulty(), d.getSpecialMultiplier()));
    }
}
