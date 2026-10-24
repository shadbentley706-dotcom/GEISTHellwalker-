package com.grapefruit.hellblazer.registry;

import com.grapefruit.hellblazer.HellblazerMod;
import com.grapefruit.hellblazer.item.OutfitItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HbItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HellblazerMod.MODID);

    /** The creative tab's icon (the sigil). Not in the tab itself. */
    public static final RegistryObject<Item> SIGIL = ITEMS.register("sigil", () -> new Item(new Item.Properties()));
    /** The tan trench coat, worn open, with the white shirt (unbuttoned) and the red tie as part of it. */
    public static final RegistryObject<Item> TRENCH_COAT = ITEMS.register("trench_coat", () -> new OutfitItem(ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> BLACK_TROUSERS = ITEMS.register("black_trousers", () -> new OutfitItem(ArmorItem.Type.LEGGINGS, new Item.Properties()));

    private HbItems() {}
}
