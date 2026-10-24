package com.grapefruit.hellblazer.registry;

import com.grapefruit.hellblazer.HellblazerMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The casebook's sounds (synthesized by tools/gen_sounds.py; listed in sounds.json). */
public final class HbSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HellblazerMod.MODID);

    public static final RegistryObject<SoundEvent> CASE_OPEN = reg("case_open");
    public static final RegistryObject<SoundEvent> CASE_CLOSE = reg("case_close");
    public static final RegistryObject<SoundEvent> CASE_CLICK = reg("case_click");
    public static final RegistryObject<SoundEvent> CASE_HOVER = reg("case_hover");
    public static final RegistryObject<SoundEvent> CASE_TAB = reg("case_tab");
    public static final RegistryObject<SoundEvent> CASE_TYPE = reg("case_type");
    public static final RegistryObject<SoundEvent> CASE_STAMP = reg("case_stamp");
    public static final RegistryObject<SoundEvent> CASE_DENY = reg("case_deny");
    public static final RegistryObject<SoundEvent> CASE_CLOSED = reg("case_closed");
    public static final RegistryObject<SoundEvent> CASE_FAILED = reg("case_failed");
    public static final RegistryObject<SoundEvent> RANK_UP = reg("rank_up");

    private HbSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HellblazerMod.MODID, name)));
    }
}
