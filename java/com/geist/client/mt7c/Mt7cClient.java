package com.geist.client.mt7c;

import com.geist.GeistMod;
import com.geist.item.Mt7cItem;
import com.geist.network.GeistNetwork;
import com.geist.network.Mt7cActionPacket;
import com.geist.network.Mt7cAnimPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * The MT-7C on the client: the mode key, the animation state of everyone's tool (from the server's messages), the
 * charge hum, steam during the vent, the camera jolt of a breach, and quiet digging (block break and hit sounds
 * near you are turned right down while you hold it).
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class Mt7cClient {
    public static final KeyMapping MODE_KEY = new KeyMapping("key.geist.mt7c_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.geist");

    @Mod.EventBusSubscriber(modid = GeistMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(MODE_KEY);
        }
    }

    /** One holder's animation state. */
    public static final class Anim {
        public long switchAt = -1000L, thrustAt = -1000L, ventAt = -1000L, layerAt = -1000L;
        public int fromMode, toMode, thrustBlocks, thrustMode, ventLen = 1;
        boolean humming;
    }

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    /** The living thing whose model is being drawn right now (so the held tool knows whose it is). */
    @Nullable
    private static LivingEntity drawing;
    // camera: a damped dip plus decaying shake
    private static float dipOff, dipVel, trauma;
    private static long lastFrameNanos;

    private Mt7cClient() {}

    @Nullable
    public static Anim peek(int id) {
        return ANIMS.get(id);
    }

    private static Anim anim(int id) {
        return ANIMS.computeIfAbsent(id, k -> new Anim());
    }

    @Nullable
    public static LivingEntity drawing() {
        return drawing;
    }

    public static boolean holding(@Nullable Player p) {
        return p != null && p.getMainHandItem().getItem() instanceof Mt7cItem;
    }

    // ---------------------------------------------------------------- messages from the server

    public static void remote(int entityId, int event, int a, int b) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity e = level.getEntity(entityId);
        if (e == null) return;
        Anim an = anim(entityId);
        long now = level.getGameTime();
        boolean local = e == mc.player;
        switch (event) {
            case Mt7cAnimPacket.SWITCH -> {
                an.switchAt = now;
                an.fromMode = a;
                an.toMode = b;
            }
            case Mt7cAnimPacket.THRUST -> {
                an.thrustAt = now;
                an.thrustBlocks = a;
                an.thrustMode = b;
                if (local) {
                    dipVel -= a == 0 ? 25.0F : 55.0F;
                    trauma = Math.min(1.0F, trauma + (a == 0 ? 0.1F : 0.3F));
                }
            }
            case Mt7cAnimPacket.LAYER -> {
                an.layerAt = now;
                if (local) trauma = Math.min(1.0F, trauma + 0.08F + 0.02F * b);
            }
            case Mt7cAnimPacket.VENT -> {
                an.ventAt = now;
                an.ventLen = Math.max(1, a);
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- whose tool is being drawn

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        drawing = event.getEntity();
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        drawing = null;
    }

    // ---------------------------------------------------------------- every tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer lp = mc.player;
        ClientLevel level = mc.level;
        if (lp == null || level == null) return;
        while (MODE_KEY.consumeClick()) {
            if (mc.screen == null && holding(lp)) GeistNetwork.CHANNEL.sendToServer(new Mt7cActionPacket(Mt7cActionPacket.SWITCH));
        }
        if (mc.isPaused()) return;
        long now = level.getGameTime();
        for (Player pl : level.players()) {
            Anim a = ANIMS.get(pl.getId());
            if (pl.distanceToSqr(lp) > 40.0D * 40.0D) {
                if (a != null) a.humming = false;
                continue;
            }
            boolean charging = Mt7cAnim.charging(pl);
            if (charging) {
                if (a == null) a = anim(pl.getId());
                if (!a.humming) {                                               // the rising low hum
                    a.humming = true;
                    mc.getSoundManager().play(new Mt7cChargeSound(pl));
                }
            } else if (a != null) {
                a.humming = false;
            }
            if (a != null && holding(pl)) {                                     // the vent: compressed air out of the hood
                long v = now - a.ventAt;
                if (v >= 0 && v < a.ventLen * 0.6F && v % 2 == 0) {
                    Vec3 at = hand(pl);
                    level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, (level.random.nextDouble() - 0.5D) * 0.03D, 0.03D + 0.03D * level.random.nextDouble(),
                            (level.random.nextDouble() - 0.5D) * 0.03D);
                }
            }
        }
        if (now % 200 == 0) ANIMS.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    /** About where the tool's hood is for a player holding it (for the steam). */
    private static Vec3 hand(Player p) {
        double yaw = Math.toRadians(p.yBodyRot);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        Vec3 look = p.getViewVector(1.0F);
        Vec3 eye = p.getEyePosition();
        return new Vec3(eye.x + look.x * 0.55D + rx * 0.3D, eye.y - 0.35D + look.y * 0.4D, eye.z + look.z * 0.55D + rz * 0.3D);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ANIMS.clear();
        drawing = null;
        dipOff = dipVel = trauma = 0.0F;
    }

    // ---------------------------------------------------------------- quiet digging

    /** Break and hit sounds of blocks near you are turned right down while you hold the MT-7C. */
    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s == null || s.getSource() != SoundSource.BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!holding(p)) return;
        ResourceLocation loc = s.getLocation();
        String path = loc.getPath();
        boolean hit = path.endsWith(".hit"), brk = path.endsWith(".break");
        if (!hit && !brk) return;
        if (p.distanceToSqr(s.getX(), s.getY(), s.getZ()) > 64.0D) return;
        event.setSound(new SimpleSoundInstance(loc, SoundSource.BLOCKS, hit ? 0.06F : 0.28F, 0.85F, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.LINEAR, s.getX(), s.getY(), s.getZ(), false));
    }

    // ---------------------------------------------------------------- camera

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long t = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0F : Mth.clamp((t - lastFrameNanos) / 1.0E9F, 0.0F, 0.05F);
        lastFrameNanos = t;
        if (Minecraft.getInstance().isPaused()) return;
        dipVel += (-dipOff * 240.0F - dipVel * 20.0F) * dt;
        dipOff += dipVel * dt;
        trauma = Math.max(0.0F, trauma - dt * 2.2F);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float s = trauma * trauma;
        float pitch = dipOff, yaw = 0.0F, roll = 0.0F;
        if (s > 0.0001F) {
            double t = System.nanoTime() / 1.0E9D;
            pitch += (float) (Math.sin(t * 67.0D) + Math.sin(t * 39.0D + 1.1D)) * 1.2F * s;
            yaw += (float) Math.sin(t * 53.0D + 2.0D) * 1.0F * s;
            roll += (float) Math.sin(t * 47.0D + 0.4D) * 1.6F * s;
        }
        if (pitch != 0.0F) event.setPitch(event.getPitch() + pitch);
        if (yaw != 0.0F) event.setYaw(event.getYaw() + yaw);
        if (roll != 0.0F) event.setRoll(event.getRoll() + roll);
    }
}
