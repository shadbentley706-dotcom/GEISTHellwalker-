package com.geist.client.gauntlet;

import com.geist.item.GauntletItem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Every gauntlet animation value, as pure functions of the wearer's state and the time:
 * the arm angles for the third-person pose, the first-person hand offsets, the piston and vent-flap positions,
 * the body lean and the glow colour. One place, so the third-person player, the first-person hands, the trailing
 * motion blur and the inventory icon all agree.
 *
 * Timeline (ticks, 20 per second): wind-up 10 (hands rise, pistons compress); barrage: a punch every 4 or 5
 * ticks, alternating arms, each one snapping out in about 1 tick, holding, and pulling back; cool-down: the
 * arms recoil and sag, the vent flaps open and steam pours out while the metal glows hot, then fades.
 * Carry: both arms up under the mob; the wind-up cocks the throwing arm back behind the head while the other
 * points at the target and the shoulders turn; the throw whips the arm over the top in 3 ticks and follows
 * through across the body. The slam brings both arms down at once.
 */
public final class GauntletAnimator {
    public static final float GUARD_X = -1.2F, EXT_X = -1.62F, GUARD_Y = 0.35F, EXT_Y = 0.08F;
    /** How long a single punch takes from launch to guard again. */
    public static final float PUNCH_TICKS = 4.0F;
    /** Carrying: both arms straight up holding the mob. The throw and the slam take this long. */
    public static final float CARRY_X = -2.85F, THROW_TICKS = 14.0F, SLAM_TICKS = 11.0F;
    /** Guard: forearms raised and crossed in front of the face. Raising / lowering it, a block's recoil and a break's stagger take this long. */
    public static final float BLOCK_X = -1.95F, BLOCK_Y = 0.62F, GUARD_RAISE = 3.0F, RECOIL_TICKS = 10.0F, STAGGER_TICKS = 16.0F;
    private static final float CYAN_R = 0.25F, CYAN_G = 0.83F, CYAN_B = 1.0F;

    private GauntletAnimator() {}

    /** One arm's animation values (reused: render thread only). */
    public static final class Out {
        /** How strongly the pose replaces the vanilla arm swing (0..1). */
        public float w;
        /** Arm rotation targets (radians). */
        public float x, y, z;
        /** Shoulder shift forward (negative z, model pixels). */
        public float push;
        /** Piston compression: -0.45 over-extended, 0 rest, 1 fully compressed. */
        public float comp;
        /** Vent flap opening (radians). */
        public float flap;
        /** Punch extension 0..1, hit flash 0..1, overheat 0..1. */
        public float ext, flash, heat;
        /** First-person offsets (blocks, camera space) and extra rotation (degrees). */
        public float fpx, fpy, fpz, fpRoll, fpPitch;

        void reset() {
            w = x = y = z = push = comp = flap = ext = flash = heat = fpx = fpy = fpz = fpRoll = fpPitch = 0.0F;
        }
    }

    public static float ease(float v) {
        v = Mth.clamp(v, 0.0F, 1.0F);
        return v * v * (3.0F - 2.0F * v);
    }

    public static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    /** One punch's extension 0..1 for {@code s} ticks since it was thrown: snap out, hold, pull back. */
    public static float punchExt(float s) {
        if (s < 0.0F || s >= PUNCH_TICKS) return 0.0F;
        if (s < 1.2F) {
            float k = 1.0F - s / 1.2F;
            return 1.0F - k * k;
        }
        if (s < 1.8F) return 1.0F;
        return 1.0F - ease((s - 1.8F) / (PUNCH_TICKS - 1.8F));
    }

    /** How far the throw is wound up, 0..1 (full power after FULL_CHARGE_TICKS). */
    public static float chargeK(@Nullable GauntletClient.Anim a, float t) {
        if (a == null || a.carryId < 0 || a.chargeStart < 0L) return 0.0F;
        return Mth.clamp((t - a.chargeStart) / GauntletItem.FULL_CHARGE_TICKS, 0.0F, 1.0F);
    }

    private static boolean within(float s, float len) {
        return s >= 0.0F && s < len;
    }

    /** Is the gauntlet doing something that replaces the vanilla first-person hands? */
    public static boolean busy(@Nullable GauntletClient.Anim a, float t) {
        if (a == null) return false;
        return a.state != GauntletClient.IDLE || a.carryId >= 0 || within(t - a.throwAt, THROW_TICKS) || within(t - a.slamAt, SLAM_TICKS)
                || a.guardUp || within(t - a.guardDown, GUARD_RAISE) || within(t - a.guardBreakAt, STAGGER_TICKS);
    }

    /** The guard's heat now (0..100): the last reported value, bled off at the server's rate. */
    public static float guardHeat(@Nullable GauntletClient.Anim a, float t) {
        if (a == null) return 0.0F;
        float rate = a.guardUp ? GauntletItem.GUARD_COOL_UP : GauntletItem.GUARD_COOL_DOWN;
        return Math.max(0.0F, a.heat - Math.max(0.0F, t - a.heatAt) * rate);
    }

    /** How raised the guard is, 0..1. */
    public static float guardK(@Nullable GauntletClient.Anim a, float t) {
        if (a == null) return 0.0F;
        if (a.guardUp) return ease((t - a.guardStart) / GUARD_RAISE);
        return 1.0F - ease((t - a.guardDown) / GUARD_RAISE);
    }

    /** A block's recoil, 1 right after the hit fading to 0. */
    public static float recoil(@Nullable GauntletClient.Anim a, float t) {
        if (a == null) return 0.0F;
        float s = t - a.lastBlock;
        return within(s, RECOIL_TICKS) ? (float) Math.exp(-s / 2.5F) : 0.0F;
    }

    /** Body twist (radians): the shoulder of the punching arm goes forward. Negative turns the right shoulder forward. */
    public static float twist(@Nullable GauntletClient.Anim a, float t) {
        if (a == null) return 0.0F;
        float sign = a.throwSide == 0 ? 1.0F : -1.0F;                 // + turns the throwing shoulder back
        if (a.carryId >= 0) return 0.5F * sign * ease(chargeK(a, t));
        float tc = t - a.throwAt;
        if (within(tc, THROW_TICKS)) {
            return sign * (tc < 3.0F ? Mth.lerp(ease(tc / 3.0F), 0.5F, -0.4F) : Mth.lerp(ease((tc - 3.0F) / (THROW_TICKS - 3.0F)), -0.4F, 0.0F));
        }
        if (a.state != GauntletClient.BARRAGE) return 0.0F;
        return -0.2F * punchExt(t - a.punchAt[0]) + 0.2F * punchExt(t - a.punchAt[1]);
    }

    /** Forward lean of the whole body (radians). */
    public static float lean(@Nullable GauntletClient.Anim a, float t) {
        if (a == null) return 0.0F;
        float sb = t - a.guardBreakAt;
        if (within(sb, STAGGER_TICKS)) return -0.18F * (1.0F - ease(sb / STAGGER_TICKS));   // knocked off balance
        if (a.guardUp) return -0.1F * recoil(a, t) * (a.parried ? -1.0F : 1.0F) + 0.04F * guardK(a, t);
        if (a.carryId >= 0) return -0.08F * ease(chargeK(a, t));      // leaning back into the wind-up
        float tc = t - a.throwAt, ts = t - a.slamAt;
        if (within(tc, THROW_TICKS)) {
            return tc < 3.0F ? Mth.lerp(ease(tc / 3.0F), -0.08F, 0.28F) : Mth.lerp(ease((tc - 3.0F) / (THROW_TICKS - 3.0F)), 0.28F, 0.0F);
        }
        if (within(ts, SLAM_TICKS)) {
            return ts < 3.0F ? 0.32F * ease(ts / 3.0F) : 0.32F * (1.0F - ease((ts - 3.0F) / (SLAM_TICKS - 3.0F)));
        }
        float st = t - a.start;
        return switch (a.state) {
            case GauntletClient.WINDUP -> -0.05F * ease(st / GauntletItem.WINDUP_TICKS);
            case GauntletClient.BARRAGE -> 0.14F * ease(st / 3.0F);
            case GauntletClient.COOLDOWN -> 0.14F * (1.0F - ease(st / 36.0F));
            default -> 0.0F;
        };
    }

    /** How far the body sinks (blocks). */
    public static float lower(@Nullable GauntletClient.Anim a, float t) {
        if (a == null) return 0.0F;
        if (a.carryId >= 0) return 0.03F;                              // the weight
        float ts = t - a.slamAt;
        if (within(ts, SLAM_TICKS)) return 0.13F * (ts < 3.0F ? ease(ts / 3.0F) : 1.0F - ease((ts - 3.0F) / (SLAM_TICKS - 3.0F)));
        float st = t - a.start;
        return switch (a.state) {
            case GauntletClient.WINDUP -> 0.07F * ease(st / GauntletItem.WINDUP_TICKS);
            case GauntletClient.BARRAGE -> 0.05F;
            case GauntletClient.COOLDOWN -> 0.05F * (1.0F - ease(st / 30.0F));
            default -> 0.0F;
        };
    }

    /** Vanilla crouch for the squat of the wind-up (the pistons compress) and the start of a throw. */
    public static boolean crouch(@Nullable GauntletClient.Anim a, float t) {
        if (a == null) return false;
        if (within(t - a.slamAt, 5.0F)) return true;
        return a.state == GauntletClient.WINDUP && t - a.start < GauntletItem.WINDUP_TICKS;
    }

    // ---------------------------------------------------------------- the arm

    /** Fills {@code o} for one arm (side 0 = right, 1 = left) at time {@code t}. */
    public static void eval(@Nullable GauntletClient.Anim a, int side, float t, Out o) {
        o.reset();
        float out = side == 0 ? 1.0F : -1.0F;
        float breathe = 0.12F * Mth.sin(t * 0.1F + side * 3.1416F);
        o.comp = breathe;
        if (a == null) return;
        float st = t - a.start;
        float since = t - a.punchAt[side];
        float ext = punchExt(since), other = punchExt(t - a.punchAt[1 - side]);
        o.ext = ext;
        o.flash = since >= 0.0F && since < 8.0F ? (float) Math.exp(-since / 2.0F) : 0.0F;
        float heavy = t - a.lastHeavy;
        if (heavy >= 0.0F && heavy < 10.0F) o.flash = Math.max(o.flash, (float) Math.exp(-heavy / 3.0F));
        switch (a.state) {
            case GauntletClient.WINDUP -> {
                float e = ease(st / GauntletItem.WINDUP_TICKS);
                o.w = e;
                o.x = GUARD_X;
                o.y = -out * GUARD_Y;
                o.comp = Mth.lerp(e, breathe, 1.0F);
                o.heat = 0.15F * e;
                float rattle = st > 6.0F ? 0.012F * Mth.sin(t * 9.0F + side) : 0.0F;
                o.fpx = -out * 0.20F * e;
                o.fpy = 0.30F * e + rattle;
                o.fpz = -0.12F * e;
                o.fpRoll = -out * 6.0F * e;
            }
            case GauntletClient.BARRAGE -> {
                o.w = 1.0F;
                o.x = Mth.lerp(ext, GUARD_X + 0.10F * other, EXT_X);
                o.y = -out * Mth.lerp(ext, GUARD_Y, EXT_Y);
                o.z = out * 0.04F;
                o.push = -2.2F * ext;
                o.comp = Mth.lerp(ext, 0.55F, -0.4F);
                o.heat = Mth.clamp(st / GauntletItem.MAX_BARRAGE_TICKS, 0.0F, 1.0F) * 0.5F;
                o.fpx = -out * (0.22F + 0.10F * ext);
                o.fpy = 0.32F + 0.10F * ext - 0.04F * other;
                o.fpz = -0.15F - 0.62F * ext + 0.05F * other;
                o.fpRoll = -out * (6.0F + 10.0F * ext);
                o.fpPitch = -10.0F * ext;
            }
            case GauntletClient.COOLDOWN -> {
                float len = Math.max(a.cooldownLen, 1);
                float recoil = ease(st / 7.0F);
                o.w = st < 22.0F ? 1.0F : 1.0F - ease((st - 22.0F) / 38.0F);
                o.x = Mth.lerp(recoil, GUARD_X, 0.25F);
                o.y = -out * Mth.lerp(recoil, GUARD_Y, 0.0F);
                o.z = out * (0.30F + 0.06F * Mth.sin(st * 1.9F) * (float) Math.exp(-st / 26.0F));
                float calm = st > len - 12.0F ? ease((st - (len - 12.0F)) / 12.0F) : 0.0F;
                o.comp = Mth.lerp(calm, -0.45F, breathe);
                float open = st < 3.0F ? ease(st / 3.0F) : (st < len - 14.0F ? 1.0F : 1.0F - ease((st - (len - 14.0F)) / 14.0F));
                o.flap = 1.25F * open;
                o.heat = Mth.clamp(1.0F - st / len, 0.0F, 1.0F);
                float shake = (float) Math.exp(-st / 18.0F);
                o.fpx = out * 0.08F * recoil + 0.02F * Mth.sin(st * 2.3F + side) * shake;
                o.fpy = -0.42F * recoil + 0.02F * Mth.sin(st * 2.9F) * shake;
                o.fpz = 0.08F * recoil;
                o.fpRoll = out * 14.0F * recoil;
                o.fpPitch = 14.0F * recoil;
                float fade = o.w;                                     // as the pose lets go, so do the offsets
                o.fpx *= fade;
                o.fpy *= fade;
                o.fpz *= fade;
                o.fpRoll *= fade;
                o.fpPitch *= fade;
            }
            default -> { }
        }
        guard(a, side, t, o);                                         // the guard, carrying, the throw and the slam overlay everything
        carry(a, side, t, o);
        float bt = (t - a.bootStart) / GauntletClient.BOOT_TICKS;      // boot-up: the pistons run out one after the other
        if (bt >= 0.0F && bt < 1.0F) {
            o.comp = Math.max(o.comp, 1.0F - ease((bt - 0.2F - 0.15F * side) / 0.25F));
            o.flap = Math.max(o.flap, 0.6F * Mth.sin(bt * 3.1416F));
        }
    }

    /** Blends {@code o} towards a target pose with weight {@code k}. */
    private static void blend(Out o, float k, float x, float y, float z, float comp, float fpx, float fpy, float fpz, float roll, float pitch) {
        if (k <= 0.0F) return;
        o.x = Mth.lerp(k, o.x, x);
        o.y = Mth.lerp(k, o.y, y);
        o.z = Mth.lerp(k, o.z, z);
        o.push = Mth.lerp(k, o.push, 0.0F);
        o.comp = Mth.lerp(k, o.comp, comp);
        o.fpx = Mth.lerp(k, o.fpx, fpx);
        o.fpy = Mth.lerp(k, o.fpy, fpy);
        o.fpz = Mth.lerp(k, o.fpz, fpz);
        o.fpRoll = Mth.lerp(k, o.fpRoll, roll);
        o.fpPitch = Mth.lerp(k, o.fpPitch, pitch);
        o.w = Math.max(o.w, k);
    }

    /**
     * The guard: forearms up and crossed in front of the face, pistons loaded. A block knocks them back towards the
     * face (pistons absorb it); a parry snaps them forward; a break flings them apart and down.
     */
    private static void guard(GauntletClient.Anim a, int side, float t, Out o) {
        float out = side == 0 ? 1.0F : -1.0F;
        float k = guardK(a, t);
        float heatK = guardHeat(a, t) / GauntletItem.GUARD_HEAT_MAX;
        if (k > 0.0F) {
            float r = a.guardUp ? recoil(a, t) : 0.0F;
            float push = a.parried ? -r : r;                             // + toward the face, - shoved out (parry)
            float shake = heatK > 0.6F ? 0.012F * Mth.sin(t * 6.0F + side) * (heatK - 0.6F) / 0.4F : 0.0F;
            blend(o, k, BLOCK_X + 0.28F * push + shake, -out * BLOCK_Y, out * 0.05F, a.parried ? Mth.lerp(r, 0.9F, -0.45F) : Mth.lerp(r, 0.9F, 1.0F),
                    -out * 0.28F, 0.32F - 0.04F * push + shake, -0.02F + 0.1F * push, -out * 38.0F, 18.0F);
            o.flash = Math.max(o.flash, r);
            o.heat = Math.max(o.heat, 0.85F * heatK);
        }
        float sb = t - a.guardBreakAt;
        if (within(sb, STAGGER_TICKS)) {
            float w = sb < 8.0F ? 1.0F : 1.0F - (sb - 8.0F) / (STAGGER_TICKS - 8.0F);
            float e = ease(sb / 4.0F);
            blend(o, w, Mth.lerp(e, BLOCK_X, 0.35F), 0.0F, out * 0.6F * e, -0.45F,
                    out * 0.25F * e, Mth.lerp(e, 0.32F, -0.4F), 0.05F, out * 25.0F * e, -10.0F * e);
            o.flap = Math.max(o.flap, 1.1F * (1.0F - sb / STAGGER_TICKS));
            o.heat = Math.max(o.heat, 1.0F - sb / (STAGGER_TICKS * 3.0F));
        }
    }

    /**
     * Carrying overhead (both arms up, pistons loaded), the wind-up (the throwing arm cocks back behind the head,
     * the other points at the target), the throw (the arm whips over the top in 3 ticks and follows through across
     * the body) and the slam (both arms down at once).
     */
    private static void carry(GauntletClient.Anim a, int side, float t, Out o) {
        float out = side == 0 ? 1.0F : -1.0F;
        boolean thrower = side == a.throwSide;
        if (a.carryId >= 0) {
            float in = ease((t - a.carryStart) / 6.0F);
            float c = ease(chargeK(a, t));
            float strain = 0.015F * Mth.sin(t * 1.7F + side * 2.0F) * (1.0F - c);   // the mob wriggles
            if (thrower) {
                blend(o, in, Mth.lerp(c, CARRY_X, -3.45F) + strain, 0.0F, out * Mth.lerp(c, 0.12F, 0.32F), Mth.lerp(c, 0.8F, 1.0F),
                        -out * 0.12F * (1.0F - c), Mth.lerp(c, 0.55F, 0.85F) + strain, Mth.lerp(c, -0.05F, 0.35F), 0.0F, 25.0F + 10.0F * c);
            } else {
                blend(o, in, Mth.lerp(c, CARRY_X, -1.5F) + strain, -out * 0.3F * c, out * 0.12F * (1.0F - c), 0.8F,
                        -out * Mth.lerp(c, 0.12F, 0.05F), Mth.lerp(c, 0.55F, 0.12F) + strain, Mth.lerp(c, -0.05F, -0.28F), -out * 8.0F * c, 25.0F * (1.0F - c));
            }
            o.heat = Math.max(o.heat, 0.2F * c);
            return;
        }
        float tc = t - a.throwAt;
        if (within(tc, THROW_TICKS)) {
            float k = tc < 8.0F ? 1.0F : 1.0F - (tc - 8.0F) / (THROW_TICKS - 8.0F);
            if (thrower) {
                if (tc < 3.0F) {                                          // the whip
                    float e = ease(tc / 3.0F);
                    blend(o, k, Mth.lerp(e, -3.45F, -0.75F), -out * 0.35F * e, out * Mth.lerp(e, 0.32F, 0.05F), -0.45F,
                            -out * 0.42F * e, Mth.lerp(e, 0.85F, -0.12F), Mth.lerp(e, 0.35F, -0.6F), -out * 12.0F * e, Mth.lerp(e, 35.0F, -20.0F));
                } else {                                                  // follow-through
                    float e = ease((tc - 3.0F) / (THROW_TICKS - 3.0F));
                    blend(o, k, Mth.lerp(e, -0.75F, -0.2F), -out * Mth.lerp(e, 0.35F, 0.1F), out * 0.05F, Mth.lerp(e, -0.45F, 0.0F),
                            -out * Mth.lerp(e, 0.42F, 0.1F), Mth.lerp(e, -0.12F, -0.2F), Mth.lerp(e, -0.6F, -0.1F), -out * 12.0F * (1.0F - e), Mth.lerp(e, -20.0F, 0.0F));
                }
                o.flash = Math.max(o.flash, tc < 4.0F ? 1.0F : 0.0F);
            } else {                                                      // the pointing arm swings down and back
                float e = ease(tc / 4.0F);
                blend(o, k, Mth.lerp(e, -1.5F, 0.45F), 0.0F, out * 0.2F * e, 0.0F,
                        -out * 0.05F, Mth.lerp(e, 0.12F, -0.35F), Mth.lerp(e, -0.28F, -0.05F), 0.0F, 0.0F);
            }
            return;
        }
        float ts = t - a.slamAt;
        if (within(ts, SLAM_TICKS)) {
            float k = ts < 6.0F ? 1.0F : 1.0F - (ts - 6.0F) / (SLAM_TICKS - 6.0F);
            if (ts < 3.0F) {
                float e = ease(ts / 3.0F);
                blend(o, k, Mth.lerp(e, CARRY_X, -0.7F), 0.0F, out * 0.1F, -0.45F,
                        -out * 0.12F, Mth.lerp(e, 0.55F, -0.3F), Mth.lerp(e, -0.05F, -0.55F), 0.0F, Mth.lerp(e, 25.0F, -15.0F));
            } else {
                float e = ease((ts - 3.0F) / (SLAM_TICKS - 3.0F));
                blend(o, k, Mth.lerp(e, -0.7F, -0.2F), 0.0F, out * 0.1F, Mth.lerp(e, -0.45F, 0.0F),
                        -out * 0.12F * (1.0F - e), Mth.lerp(e, -0.3F, -0.1F), Mth.lerp(e, -0.55F, -0.1F), 0.0F, -15.0F * (1.0F - e));
            }
            o.flash = Math.max(o.flash, ts < 4.0F ? 1.0F : 0.0F);
        }
    }

    /** Slides the piston rods and swings the vent flaps to match both arms' values. */
    public static void animate(GauntletModel m, @Nullable GauntletClient.Anim a, float t) {
        Out o = TMP;
        m.resetMoving();
        eval(a, 0, t, o);
        float r = -1.8F * o.comp, rf = o.flap;
        m.rodFR.y += r;
        m.rodBR.y += r;
        m.flapR.xRot = rf;
        eval(a, 1, t, o);
        float l = -1.8F * o.comp;
        m.rodFL.y += l;
        m.rodBL.y += l;
        m.flapL.xRot = o.flap;
    }

    private static final Out TMP = new Out();

    // ---------------------------------------------------------------- the light

    /**
     * {r, g, b, segmentA, segmentB}: the glow colour times its brightness, and the two alternating brightnesses
     * of the spine segments (they chase each other up and down the back).
     * Idle: steady cyan. Wind-up: climbs and whitens. Barrage: white-hot flashes on every punch, the spine racing.
     * Cool-down: glowing orange-red, cooling back to cyan.
     */
    public static float[] glow(@Nullable GauntletClient.Anim a, float t) {
        float k = 0.78F + 0.12F * Mth.sin(t * 0.08F), white = 0.0F, heat = 0.0F, speed = 0.3F, flash = 0.0F;
        if (a != null) {
            float st = t - a.start;
            flash = Math.max(flashOf(a, 0, t), flashOf(a, 1, t));
            switch (a.state) {
                case GauntletClient.WINDUP -> {
                    float e = ease(st / GauntletItem.WINDUP_TICKS);
                    float n = Mth.frac(Mth.sin(t * 7.13F) * 9631.2F);
                    k = (0.45F + 0.55F * e) * (n > 0.35F * (1.0F - e) ? 1.0F : 0.4F);
                    white = 0.5F * e;
                    speed = 0.7F;
                }
                case GauntletClient.BARRAGE -> {
                    k = 0.85F + 0.7F * flash;
                    white = 0.45F + 0.5F * flash;
                    speed = 1.0F;
                }
                case GauntletClient.COOLDOWN -> {
                    heat = Mth.clamp(1.0F - st / Math.max(a.cooldownLen, 1), 0.0F, 1.0F);
                    float n = Mth.frac(Mth.sin(t * 9.7F) * 4371.9F);
                    k = 0.55F + 0.45F * heat - (heat > 0.7F && n > 0.8F ? 0.3F : 0.0F);
                }
                default -> { }
            }
            heat = Math.max(heat, 0.8F * guardHeat(a, t) / GauntletItem.GUARD_HEAT_MAX);
            float rb = recoil(a, t);
            if (rb > 0.0F) {                                           // a block: the lights flare
                k += 0.5F * rb;
                white = Math.max(white, rb);
            }
            float heavy = t - a.lastHeavy;
            if (heavy >= 0.0F && heavy < 8.0F) {
                float f = (float) Math.exp(-heavy / 2.5F);
                k += 0.6F * f;
                white = Math.max(white, f);
            }
            float bt = (t - a.bootStart) / GauntletClient.BOOT_TICKS;
            if (bt >= 0.0F && bt < 1.0F && bt < 0.7F) {                // boot: lights stutter on
                float n = Mth.frac(Mth.sin(t * 7.13F) * 9631.2F);
                k *= n > 0.5F + 0.5F * (0.7F - bt) ? 1.0F : 0.08F;
            }
        }
        float r = Mth.lerp(white, CYAN_R, 0.9F), g = Mth.lerp(white, CYAN_G, 0.97F), b = Mth.lerp(white, CYAN_B, 1.0F);
        r = Mth.lerp(heat, r, 1.0F);
        g = Mth.lerp(heat, g, 0.4F);
        b = Mth.lerp(heat, b, 0.1F);
        float ph = t * speed;
        float segA = (0.55F + 0.45F * Mth.sin(ph)) * (1.0F - 0.5F * heat);
        float segB = (0.55F + 0.45F * Mth.sin(ph + 3.1416F)) * (1.0F - 0.5F * heat);
        return new float[]{r * k, g * k, b * k, segA, segB};
    }

    private static float flashOf(GauntletClient.Anim a, int side, float t) {
        float s = t - a.punchAt[side];
        return s >= 0.0F && s < 8.0F ? (float) Math.exp(-s / 2.0F) : 0.0F;
    }
}
