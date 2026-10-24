package com.geist;

import com.geist.network.GeistNetwork;
import com.geist.registry.ModCreativeTabs;
import com.geist.registry.ModEntities;
import com.geist.registry.ModItems;
import com.geist.registry.ModSounds;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** G.E.I.S.T. - Global Environmental Intelligence & Surveillance Tactical. */
@Mod(GeistMod.MODID)
public class GeistMod {
    public static final String MODID = "geist";

    public GeistMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        GeistNetwork.register();
        // the exo-frame's custom arm poses must exist before Minecraft draws its first player
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.geist.client.exo.ExoPoses.bootstrap());
        // ...and so must the gauntlet's
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.geist.client.gauntlet.GauntletPoses.bootstrap());
        // ...and the MT-7C's
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.geist.client.mt7c.Mt7cPoses.bootstrap());
    }
}
