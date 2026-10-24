package com.geist.registry;

import com.geist.GeistMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GeistMod.MODID);

    /** The tab icon is an animated neon circuit chip. */
    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.geist"))
            .icon(() -> new ItemStack(ModItems.GEIST_ICON.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.GEIST_VISOR.get());
                output.accept(ModItems.SMOKE_PELLET.get());
                output.accept(com.geist.item.ExoFrameItem.charged());
                output.accept(ModItems.EXO_FRAME.get());
                output.accept(ModItems.GAUNTLET.get());
                output.accept(ModItems.MT7C.get());
                output.accept(ModItems.ZERO_LIGHT.get());
                output.accept(ModItems.EXO_CORE.get());
                output.accept(ModItems.EXO_LEG_FRAME.get());
                output.accept(ModItems.COPPER_ROD.get());
            })
            .build());
}
