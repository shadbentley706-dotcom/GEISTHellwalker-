package com.geist.client.zero;

import com.geist.GeistMod;
import com.geist.item.ZeroLightItem;
import com.geist.network.GeistNetwork;
import com.geist.network.ZeroActionPacket;
import com.geist.network.ZeroAnimPacket;
import com.geist.registry.ModSounds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * The Zero Light on the client: the mode key, each archer's shot timing (for the string snap), the zoom while
 * drawing (a bow only zooms for the vanilla bow by itself), and the local hit marker.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class ZeroClient {
    public static final KeyMapping MODE_KEY = new KeyMapping("key.geist.zero_light_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.geist");

    @Mod.EventBusSubscriber(modid = GeistMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(MODE_KEY);
        }
    }

    public static final class Anim {
        public long fireAt = -1000L, modeAt = -1000L;
        public int fireKind, firePower, mode;
    }

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    /** The local archer's last hit: when, after how many bounces, what kind of arrow. */
    private static long hitAt = -1000L;
    private static int hitBounces, hitKind;

    private ZeroClient() {}

    @Nullable
    public static Anim peek(int id) {
        return ANIMS.get(id);
    }

    public static long hitAt() { return hitAt; }
    public static int hitBounces() { return hitBounces; }
    public static int hitKind() { return hitKind; }

    /** The Zero Light a player holds (main hand first), or null. */
    @Nullable
    public static net.minecraft.world.item.ItemStack held(@Nullable Player p) {
        if (p == null) return null;
        if (p.getMainHandItem().getItem() instanceof ZeroLightItem) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof ZeroLightItem) return p.getOffhandItem();
        return null;
    }

    public static boolean drawing(@Nullable LivingEntity e) {
        return e != null && e.isUsingItem() && e.getUseItem().getItem() instanceof ZeroLightItem;
    }

    public static void remote(int entityId, int event, int a, int b) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity e = level.getEntity(entityId);
        if (e == null) return;
        Anim an = ANIMS.computeIfAbsent(entityId, k -> new Anim());
        long now = level.getGameTime();
        switch (event) {
            case ZeroAnimPacket.MODE -> {
                an.modeAt = now;
                an.mode = a;
            }
            case ZeroAnimPacket.FIRE -> {
                an.fireAt = now;
                an.fireKind = a;
                an.firePower = b;
            }
            case ZeroAnimPacket.HIT -> {
                if (e == mc.player) {
                    hitAt = now;
                    hitBounces = a;
                    hitKind = b;
                    mc.player.playSound(ModSounds.ZERO_HIT.get(), 0.35F, 1.9F + 0.1F * a);   // a crisp confirm tick
                }
            }
            default -> { }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        while (MODE_KEY.consumeClick()) {
            if (mc.screen == null && held(p) != null) GeistNetwork.CHANNEL.sendToServer(new ZeroActionPacket(ZeroActionPacket.MODE));
        }
        if (mc.level.getGameTime() % 200 == 0) ANIMS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    /** Drawing zooms in a little, like a bow (vanilla only does this for its own bow). */
    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        Player p = event.getPlayer();
        if (!drawing(p)) return;
        float f = Mth.clamp(p.getTicksUsingItem() / (float) ZeroLightItem.DRAW_TICKS, 0.0F, 1.0F);
        float effects = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        event.setNewFovModifier(event.getNewFovModifier() * Mth.lerp(effects, 1.0F, 1.0F - f * f * 0.15F));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ANIMS.clear();
        hitAt = -1000L;
    }
}
