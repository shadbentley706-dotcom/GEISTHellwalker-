package com.geist.event;

import com.geist.GeistMod;
import com.geist.entity.SmokeCloudEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * In the smoke nobody can be found: a mob inside a smoke cloud cannot pick a target, and a mob outside
 * cannot target someone hiding inside one.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID)
public final class SmokeEvents {
    private SmokeEvents() {}

    private static boolean inSmoke(LivingEntity e) {
        for (SmokeCloudEntity c : e.level().getEntitiesOfClass(SmokeCloudEntity.class, e.getBoundingBox().inflate(8.0D))) {
            if (c.strength() > 0.2F && c.area().intersects(e.getBoundingBox())) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        if (target == null || event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob)) return;
        if (inSmoke(event.getEntity()) || inSmoke(target)) event.setCanceled(true);
    }
}
