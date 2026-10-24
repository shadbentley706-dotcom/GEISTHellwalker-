package com.geist.client;

import com.geist.GeistMod;
import com.geist.network.EquipVisorPacket;
import com.geist.network.GeistNetwork;
import com.geist.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Client state: is the visor worn, is the holographic HUD on (Z), the boot-up animation, and server data.
 * Without the visor on, Z, X and C do nothing - except that Z puts the visor on if you are carrying it.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class GeistState {
    public static final KeyMapping TOGGLE = new KeyMapping("key.geist.toggle_hud", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.geist");
    /** Hold X to open the command wheel. */
    public static final KeyMapping WHEEL = new KeyMapping("key.geist.wheel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.geist");
    /** Hold C to zoom; scroll while zoomed to change the magnification. */
    public static final KeyMapping ZOOM = new KeyMapping("key.geist.zoom", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.geist");
    public static final int BOOT_TICKS = 40;

    /** HUD modules, toggled from the command wheel. */
    public static final int SCAN = 0, DEEP = 1, PEEK = 2, NAV = 3, PORTAL = 4, ORE = 5,
            VITALS = 6, CHRONO = 7, TRAJECTORY = 8, FILTER = 9;
    /** ORE (Ore Pulse) and FILTER (Ore Filter) are actions, not toggles; their slots are unused. */
    public static final boolean[] MODULES = {true, true, true, true, true, true, true, true, true, true};

    public static boolean hudEnabled = true;
    public static int bootTicks;
    /** Game time the HUD last booted (the key and colour guide shows for a few seconds after). */
    public static long bootStart = -1000L;
    private static boolean wasWearing;
    public static float localDifficulty = -1.0F, clampedDifficulty = -1.0F;

    private GeistState() {}

    @Mod.EventBusSubscriber(modid = GeistMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
            event.register(WHEEL);
            event.register(ZOOM);
        }
    }

    public static boolean wearing(Minecraft mc) {
        return mc.player != null && mc.player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GEIST_VISOR.get());
    }

    /** The full holographic HUD with upgraded abilities. */
    public static boolean hudActive(Minecraft mc) {
        return hudEnabled && wearing(mc);
    }

    /** 0 = just started booting, 1 = fully online. */
    public static float boot(float partialTick) {
        return bootTicks <= 0 ? 1.0F : Math.max(0.0F, 1.0F - (bootTicks - partialTick) / BOOT_TICKS);
    }

    /** Is the HUD on and this module enabled? */
    public static boolean module(Minecraft mc, int id) {
        return hudActive(mc) && MODULES[id];
    }

    public static void setDifficulty(float local, float clamped) {
        localDifficulty = local;
        clampedDifficulty = clamped;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { wasWearing = false; return; }
        boolean wear = wearing(mc);
        if (wear && !wasWearing && hudEnabled) startBoot(mc);      // putting the visor on boots the HUD
        wasWearing = wear;
        if (bootTicks > 0) bootTicks--;
        while (WHEEL.consumeClick()) {
            if (!wear) continue;                                       // no visor on: the key does nothing
            if (!hudEnabled) {
                mc.player.displayClientMessage(Component.translatable("message.geist.wheel_offline").withStyle(ChatFormatting.GRAY), true);
            } else if (mc.screen == null) {
                mc.setScreen(new CommandWheelScreen());
                mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 1.8F);
            }
        }
        while (TOGGLE.consumeClick()) {
            if (!wear) {
                // carrying the visor: Z puts it on (and the HUD boots). Not carrying it: nothing happens.
                if (carrying(mc) && !mc.player.isSpectator()) {
                    hudEnabled = true;
                    GeistNetwork.CHANNEL.sendToServer(new EquipVisorPacket());
                }
                continue;
            }
            hudEnabled = !hudEnabled;
            if (hudEnabled) startBoot(mc);
            else {
                mc.player.displayClientMessage(Component.translatable("message.geist.hud_off").withStyle(ChatFormatting.DARK_AQUA), true);
                mc.player.playSound(SoundEvents.BEACON_DEACTIVATE, 0.4F, 1.8F);
            }
        }
    }

    /** Is the visor somewhere in the inventory (not worn)? */
    private static boolean carrying(Minecraft mc) {
        net.minecraft.world.entity.player.Inventory inv = mc.player.getInventory();
        for (net.minecraft.world.item.ItemStack s : inv.items) if (s.is(ModItems.GEIST_VISOR.get())) return true;
        return inv.offhand.get(0).is(ModItems.GEIST_VISOR.get());
    }

    private static void startBoot(Minecraft mc) {
        bootTicks = BOOT_TICKS;
        bootStart = mc.level != null ? mc.level.getGameTime() : 0L;
        mc.player.displayClientMessage(Component.translatable("message.geist.hud_on").withStyle(ChatFormatting.AQUA), true);
        mc.player.playSound(SoundEvents.BEACON_ACTIVATE, 0.4F, 1.9F);
    }
}
