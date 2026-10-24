package com.grapefruit.hellblazer;

import com.grapefruit.hellblazer.network.HbNetwork;
import com.grapefruit.hellblazer.registry.HbItems;
import com.grapefruit.hellblazer.registry.HbSounds;
import com.grapefruit.hellblazer.registry.HbTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * GEIST: Hellblazer, by Grapefruit Company. The occult side of GEIST: a deniable, underfunded section that takes the
 * cases the rest of the agency won't touch. Its casebook (key K) - red, behind cracked glass - holds the files:
 * hunts, named targets, hauntings, cursed relics to recover, and places nobody should go. Its specialist wears a tan
 * trench coat open over an unbuttoned white shirt and a loose red tie, and black trousers.
 */
@Mod(HellblazerMod.MODID)
public class HellblazerMod {
    public static final String MODID = "geist_hellblazer";

    public HellblazerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        HbItems.ITEMS.register(modBus);
        HbSounds.SOUNDS.register(modBus);
        HbTabs.TABS.register(modBus);
        HbNetwork.register();
    }
}
