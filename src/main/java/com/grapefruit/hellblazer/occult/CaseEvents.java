package com.grapefruit.hellblazer.occult;

import com.grapefruit.hellblazer.HellblazerMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** What the world does to the casebook: kills count toward files, named figures going down close theirs, and the clock runs every second. */
@Mod.EventBusSubscriber(modid = HellblazerMod.MODID)
public final class CaseEvents {
    private CaseEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 11) return;
        Cases.tick(p);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim instanceof Player) return;
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        CompoundTag pd = victim.getPersistentData();
        if (pd.hasUUID(Cases.TARGET_OWNER) && victim.level() instanceof ServerLevel sl) {
            // a named figure down: it counts if its specialist did it, or was close enough to see it done
            ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(pd.getUUID(Cases.TARGET_OWNER));
            if (owner != null && owner.level() == victim.level() && (owner == killer || owner.distanceToSqr(victim) < 48.0D * 48.0D)) {
                Cases.onTargetDown(owner, pd.getInt(Cases.TARGET_CASE));
            }
        }
        if (killer != null && killer.isAlive()) Cases.onKill(killer, victim);
    }
}
