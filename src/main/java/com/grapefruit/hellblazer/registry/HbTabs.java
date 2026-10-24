package com.grapefruit.hellblazer.registry;

import com.grapefruit.hellblazer.HellblazerMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class HbTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HellblazerMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.geist_hellblazer"))
            .icon(() -> new ItemStack(HbItems.SIGIL.get()))
            .displayItems((params, out) -> {
                out.accept(HbItems.TRENCH_COAT.get());
                out.accept(HbItems.BLACK_TROUSERS.get());
            })
            .build());

    private HbTabs() {}
}
