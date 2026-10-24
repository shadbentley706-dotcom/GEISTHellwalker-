package com.geist.registry;

import com.geist.GeistMod;
import com.geist.item.GeistVisorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GeistMod.MODID);

    public static final RegistryObject<Item> GEIST_VISOR = ITEMS.register("geist_visor",
            () -> new GeistVisorItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> SMOKE_PELLET = ITEMS.register("smoke_pellet",
            () -> new com.geist.item.SmokePelletItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> EXO_FRAME = ITEMS.register("exo_frame",
            () -> new com.geist.item.ExoFrameItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> GAUNTLET = ITEMS.register("gauntlet",
            () -> new com.geist.item.GauntletItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> MT7C = ITEMS.register("mt7c",
            () -> new com.geist.item.Mt7cItem(new Item.Properties().durability(com.geist.item.Mt7cItem.DURABILITY).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> ZERO_LIGHT = ITEMS.register("zero_light",
            () -> new com.geist.item.ZeroLightItem(new Item.Properties().durability(com.geist.item.ZeroLightItem.DURABILITY).rarity(Rarity.EPIC)));

    // the experimental parts it is built from
    public static final RegistryObject<Item> COPPER_ROD = ITEMS.register("copper_rod",
            () -> new com.geist.item.PrototypePartItem(new Item.Properties(), "copper_rod"));
    public static final RegistryObject<Item> EXO_LEG_FRAME = ITEMS.register("exo_leg_frame",
            () -> new com.geist.item.PrototypePartItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "exo_leg_frame"));
    public static final RegistryObject<Item> EXO_CORE = ITEMS.register("exo_core",
            () -> new com.geist.item.PrototypePartItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "exo_core"));

    /** The creative tab's animated icon (not an item you can get). */
    public static final RegistryObject<Item> GEIST_ICON = ITEMS.register("geist_icon", () -> new Item(new Item.Properties()));
}
