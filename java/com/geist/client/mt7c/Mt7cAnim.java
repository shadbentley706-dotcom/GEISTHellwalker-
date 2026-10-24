package com.geist.client.mt7c;

import com.geist.item.Mt7cItem;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Every MT-7C animation value as a pure function of the holder's state and the time, so the held tool, the arm
 * pose and the HUD all agree.
 *
 * Mode switch (12 ticks): the collar turns a full revolution; the old head rolls a quarter turn and sinks into the
 * collar, the new one rolls out of it and locks; the LED blinks amber. In first person the tool is lifted and
 * turned toward you, like checking it.
 * Charge: the spindle draws back into the collar, the collar spins up, the whole tool is pulled back and braced,
 * vibrating harder as the charge builds; the LED goes amber, then red, and blinks red at full power.
 * Thrust: the tool lunges forward in 2 ticks with the spindle slamming out, then recovers over ~12 ticks.
 * Vent: the flaps on the hood open, the tool sags a little, the LED blinks amber slowly; it closes up at the end.
 */
public final class Mt7cAnim {
    public static final float THRUST_TICKS = 14.0F, LAYER_TICKS = 6.0F;
    private static final float[] GREEN = {0.35F, 1.0F, 0.42F}, AMBER = {1.0F, 0.68F, 0.12F}, RED = {1.0F, 0.12F, 0.08F};

    private Mt7cAnim() {}

    /** All the values one frame needs. Lengths in model pixels, angles in radians. */
    public static final class Pose {
        /** The whole tool: shift along its own axis (+ = toward the head), shift up, tip (+ = head up), roll about its axis. */
        public float dx, dy, tilt, roll;
        /** Spindle slide (+ = out), collar turn. */
        public float mount, collar;
        /** Mode switch: 0..1 progress (-1 when not switching), and the heads involved. */
        public float switchK = -1.0F;
        public int fromMode, toMode;
        /** Vent flaps open 0..1. */
        public float flaps;
        /** LED colour. */
        public float r, g, b;
        /** 0..1: charging, thrusting, switching (for the arm pose). */
        public float charge, lunge;
    }

    public static float ease(float v) {
        v = Mth.clamp(v, 0.0F, 1.0F);
        return v * v * (3.0F - 2.0F * v);
    }

    private static boolean within(float s, float len) {
        return s >= 0.0F && s < len;
    }

    public static boolean charging(LivingEntity e) {
        return e.isUsingItem() && e.getUseItem().getItem() instanceof Mt7cItem;
    }

    /** Ticks charged (with the partial tick), or -1 when not charging. */
    public static float charge(@Nullable LivingEntity e, float pt) {
        return e != null && charging(e) ? e.getTicksUsingItem() + pt : -1.0F;
    }

    private static void led(Pose p, float[] c, float k) {
        p.r = c[0] * k;
        p.g = c[1] * k;
        p.b = c[2] * k;
    }

    /** Fills {@code p}. {@code charge} is ticks charged or -1; {@code t} is game time plus partial tick. */
    public static Pose pose(Pose p, @Nullable Mt7cClient.Anim a, int mode, float charge, float t) {
        p.dx = p.dy = p.tilt = p.roll = p.mount = p.collar = p.flaps = p.charge = p.lunge = 0.0F;
        p.switchK = -1.0F;
        p.fromMode = p.toMode = mode;
        led(p, GREEN, 0.85F + 0.15F * Mth.sin(t * 0.07F));

        if (charge >= 0.0F) {                                                    // charging the breach
            float c = Mth.clamp(charge / Mt7cItem.FULL_CHARGE, 0.0F, 1.0F), e = ease(c);
            p.charge = c;
            p.mount = -1.7F * e;
            p.collar = (float) ((0.12D * charge + 0.012D * charge * charge) % (Math.PI * 2.0D));
            p.dx = -3.5F * e + 0.25F * c * Mth.sin(t * 37.0F);
            p.dy = -0.6F * e + 0.2F * c * Mth.cos(t * 41.0F);
            p.tilt = 0.12F * e;
            if (c >= 1.0F) led(p, RED, Mth.sin(t * 2.2F) > 0.0F ? 1.0F : 0.25F);
            else led(p, new float[]{1.0F, Mth.lerp(c, AMBER[1], RED[1]), Mth.lerp(c, AMBER[2], RED[2])}, 1.0F);
        }
        if (a == null) return p;

        float s = t - a.thrustAt;                                                // the thrust
        if (within(s, THRUST_TICKS)) {
            boolean whiff = a.thrustBlocks == 0;
            float lunge = s < 2.0F ? ease(s / 2.0F) : 1.0F - ease((s - 2.0F) / (THRUST_TICKS - 2.0F));
            p.lunge = lunge;
            p.dx += (whiff ? 4.0F : 7.0F) * lunge;
            p.mount += 2.4F * (s < 1.5F ? ease(s / 1.5F) : Math.max(0.0F, 1.0F - (s - 1.5F) / 5.0F));
            p.tilt -= 0.15F * lunge;
            if (!whiff) p.roll += 0.08F * Mth.sin(s * 3.0F) * (float) Math.exp(-s / 4.0F);
            led(p, AMBER, 1.0F);
        }
        float l = t - a.layerAt;                                                 // each layer breaking: a jolt
        if (within(l, LAYER_TICKS)) {
            float k = (float) Math.exp(-l / 1.5F);
            p.dy += 0.4F * k * Mth.sin(l * 9.0F);
            p.roll += 0.05F * k * Mth.cos(l * 11.0F);
        }
        float v = t - a.ventAt;                                                  // the vent
        if (within(v, a.ventLen)) {
            float open = v < 4.0F ? ease(v / 4.0F) : v > a.ventLen - 8.0F ? 1.0F - ease((v - (a.ventLen - 8.0F)) / 8.0F) : 1.0F;
            p.flaps = open;
            p.dy -= 0.8F * open;
            p.tilt -= 0.06F * open;
            if (!within(s, THRUST_TICKS)) led(p, AMBER, Mth.sin(t * 0.5F) > 0.0F ? 1.0F : 0.35F);
        }
        float w = t - a.switchAt;                                                // the mode change
        if (within(w, Mt7cItem.SWITCH_TICKS)) {
            float k = w / Mt7cItem.SWITCH_TICKS;
            p.switchK = k;
            p.fromMode = a.fromMode;
            p.toMode = a.toMode;
            p.collar += (float) (Math.PI * 2.0D) * ease(k);
            float lift = Mth.sin((float) Math.PI * k);
            p.roll += 0.45F * lift;
            p.dy += 1.5F * lift;
            p.tilt += 0.15F * lift;
            led(p, AMBER, Mth.sin(t * 1.6F) > 0.0F ? 1.0F : 0.2F);
            if (k > 0.85F) led(p, GREEN, 1.0F);                                  // locked
        }
        return p;
    }
}
