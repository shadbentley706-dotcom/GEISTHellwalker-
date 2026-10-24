package com.geist.client.exo;

import com.geist.item.ExoFrameItem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Moves the exo-frame's pistons and picks its status-light colour.
 * Spine pistons nudge up and down while idle; the leg pistons compress for the leap, extend in the air,
 * slam shut on landing, pulse with every footstep, and extend one after another during the boot-up.
 * Lights: cyan online, amber low, slow red blink when empty, flickering blue-white when shorted.
 */
public final class ExoAnimator {
    private ExoAnimator() {}

    private static float ease(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    public static void animate(ExoModel m, @Nullable LivingEntity e, ItemStack exo, float pt) {
        m.resetPistons();
        long now = now();
        float t = now + pt;
        boolean active = ExoFrameItem.active(exo, now);
        float idle = active ? 0.8F : 0.12F;
        float spineR = Mth.sin(t * 0.11F) * idle, spineL = Mth.sin(t * 0.11F + (float) Math.PI) * idle;
        float spineC = 0.0F, cR = 0.0F, cL = 0.0F;      // compression 0..1 (negative = over-extended)
        ExoClient.Anim a = e != null ? ExoClient.peek(e.getId()) : null;
        if (a != null) {
            float since = t - a.start;
            switch (a.type) {
                case ExoClient.COMPRESS -> {
                    float k = ease(since / ExoFrameItem.COMPRESS_TICKS);
                    cR = cL = k;
                    spineC = k;
                }
                case ExoClient.AIRBORNE -> {
                    cR = cL = -0.3F;
                    spineC = -0.4F;
                }
                case ExoClient.LANDING -> {
                    float k = since / ExoFrameItem.LANDING_TICKS;
                    float c = k < 0.3F ? 1.0F : 1.0F - ease((k - 0.3F) / 0.7F);
                    cR = cL = c;
                    spineC = c;
                }
                default -> { }
            }
            float step = t - a.lastStep;                     // the planted leg's piston takes the weight
            if (step >= 0.0F && step < 5.0F) {
                float pulse = 0.5F * (1.0F - step / 5.0F);
                if (a.rightStep) cR = Math.max(cR, pulse);
                else cL = Math.max(cL, pulse);
                spineC = Math.max(spineC, pulse * 0.4F);
            }
            float b = (t - a.bootStart) / ExoClient.BOOT_TICKS;
            if (b >= 0.0F && b < 1.0F) {                     // boot: everything starts closed and opens in sequence
                spineC = Math.max(spineC, 1.0F - ease((b - 0.15F) / 0.2F));
                cR = Math.max(cR, 1.0F - ease((b - 0.3F) / 0.2F));
                cL = Math.max(cL, 1.0F - ease((b - 0.45F) / 0.2F));
            }
        }
        m.thighRodR.y -= 2.2F * cR;
        m.thighRodL.y -= 2.2F * cL;
        m.spineRodR.y += spineR - 1.4F * spineC;
        m.spineRodL.y += spineL - 1.4F * spineC;
    }

    /** {r, g, b} already multiplied by brightness, for the glowing parts. */
    public static float[] glow(@Nullable LivingEntity e, ItemStack exo, float pt) {
        long now = now();
        float t = now + pt;
        float r, g, b, k;
        if (ExoFrameItem.shorted(exo)) {
            float n = Mth.frac(Mth.sin(t * 12.9898F) * 43758.547F);
            r = 0.6F; g = 0.8F; b = 1.0F;
            k = n > 0.72F ? 0.95F : 0.04F;                       // erratic flicker
        } else if (ExoFrameItem.power(exo) <= 0) {
            r = 1.0F; g = 0.12F; b = 0.08F;
            k = 0.1F + 0.4F * Math.max(0.0F, Mth.sin(t * 0.18F));
        } else if (ExoFrameItem.rebooting(exo, now)) {
            r = 0.25F; g = 0.83F; b = 1.0F;
            k = 0.25F + 0.6F * Math.abs(Mth.sin(t * 0.55F));
        } else if (ExoFrameItem.power(exo) < ExoFrameItem.MAX_POWER * ExoFrameItem.LOW_POWER) {
            r = 1.0F; g = 0.6F; b = 0.12F;
            k = 0.85F;
        } else {
            r = 0.25F; g = 0.83F; b = 1.0F;
            k = 0.88F + 0.12F * Mth.sin(t * 0.08F);
        }
        ExoClient.Anim a = e != null ? ExoClient.peek(e.getId()) : null;
        if (a != null) {
            float bt = (t - a.bootStart) / ExoClient.BOOT_TICKS;
            if (bt >= 0.0F && bt < 1.0F) {                       // boot: lights stutter on, then hold
                float n = Mth.frac(Mth.sin(t * 7.13F) * 9631.2F);
                k *= bt < 0.7F ? (n > 0.5F + 0.5F * (0.7F - bt) ? 1.0F : 0.08F) : 1.0F;
            }
        }
        return new float[]{r * k, g * k, b * k};
    }
}
