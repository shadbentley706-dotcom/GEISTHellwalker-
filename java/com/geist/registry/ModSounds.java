package com.geist.registry;

import com.geist.GeistMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Custom exo-frame and gauntlet sounds (assets/geist/sounds), synthesized by tools/gen_exo_sounds.py and gen_gauntlet_sounds.py. */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GeistMod.MODID);

    public static final RegistryObject<SoundEvent> EXO_COMPRESS = register("exo_compress");
    public static final RegistryObject<SoundEvent> EXO_LEAP = register("exo_leap");
    public static final RegistryObject<SoundEvent> EXO_LAND = register("exo_land");
    public static final RegistryObject<SoundEvent> EXO_STOMP = register("exo_stomp");
    public static final RegistryObject<SoundEvent> EXO_BOOT = register("exo_boot");
    public static final RegistryObject<SoundEvent> EXO_SHORT = register("exo_short");
    public static final RegistryObject<SoundEvent> EXO_REBOOT = register("exo_reboot");
    public static final RegistryObject<SoundEvent> EXO_FUEL = register("exo_fuel");
    public static final RegistryObject<SoundEvent> EXO_HISS = register("exo_hiss");

    // the gauntlet (tools/gen_gauntlet_sounds.py)
    public static final RegistryObject<SoundEvent> GAUNTLET_BOOT = register("gauntlet_boot");
    public static final RegistryObject<SoundEvent> GAUNTLET_WINDUP = register("gauntlet_windup");
    public static final RegistryObject<SoundEvent> GAUNTLET_PUNCH = register("gauntlet_punch");
    public static final RegistryObject<SoundEvent> GAUNTLET_HIT = register("gauntlet_hit");
    public static final RegistryObject<SoundEvent> GAUNTLET_WHIFF = register("gauntlet_whiff");
    public static final RegistryObject<SoundEvent> GAUNTLET_VENT = register("gauntlet_vent");
    public static final RegistryObject<SoundEvent> GAUNTLET_READY = register("gauntlet_ready");
    public static final RegistryObject<SoundEvent> GAUNTLET_THROW = register("gauntlet_throw");
    public static final RegistryObject<SoundEvent> GAUNTLET_IMPACT = register("gauntlet_impact");

    // the MT-7C multitool (tools/gen_mt7c_sounds.py)
    public static final RegistryObject<SoundEvent> MT7C_SWITCH = register("mt7c_switch");
    public static final RegistryObject<SoundEvent> MT7C_CHARGE = register("mt7c_charge");
    public static final RegistryObject<SoundEvent> MT7C_IMPACT = register("mt7c_impact");
    public static final RegistryObject<SoundEvent> MT7C_CRACK = register("mt7c_crack");
    public static final RegistryObject<SoundEvent> MT7C_VENT = register("mt7c_vent");
    public static final RegistryObject<SoundEvent> MT7C_READY = register("mt7c_ready");

    // the Zero Light bow (tools/gen_zerolight_sounds.py)
    public static final RegistryObject<SoundEvent> ZERO_DRAW = register("zero_draw");
    public static final RegistryObject<SoundEvent> ZERO_FIRE = register("zero_fire");
    public static final RegistryObject<SoundEvent> ZERO_HIT = register("zero_hit");
    public static final RegistryObject<SoundEvent> ZERO_SHOCKWAVE = register("zero_shockwave");
    public static final RegistryObject<SoundEvent> ZERO_RICOCHET = register("zero_ricochet");
    public static final RegistryObject<SoundEvent> ZERO_MODE = register("zero_mode");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(GeistMod.MODID, name)));
    }
}
