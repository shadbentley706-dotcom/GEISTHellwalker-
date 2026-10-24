package com.geist.client;

import com.geist.GeistMod;
import com.geist.entity.SmokeCloudEntity;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * How smoke looks on this client. Without tactical vision: a thick wall of dark smoke. With the visor and
 * HUD on: the smoke is almost invisible, the cloud's edge is a faint neon frame, and everyone blinded inside
 * glows with a neon outline.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class SmokeVision {
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.20F, 0.20F, 0.22F), 4.0F);
    private static final DustParticleOptions MID = new DustParticleOptions(new Vector3f(0.32F, 0.32F, 0.34F), 3.2F);
    private static int blindedNearby;

    private SmokeVision() {}

    static boolean tactical(Minecraft mc) {
        return GeistState.hudActive(mc);
    }

    /** Called every client tick by each smoke cloud. */
    public static void cloudTick(SmokeCloudEntity cloud) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RandomSource r = mc.level.random;
        AABB a = cloud.area();
        float s = cloud.strength();
        if (tactical(mc)) {                                            // the visor sees through: only a few faint wisps
            if (r.nextInt(6) == 0) {
                mc.level.addParticle(ParticleTypes.SMOKE, Mth.lerp(r.nextDouble(), a.minX, a.maxX), a.minY + r.nextDouble() * 3.0D,
                        Mth.lerp(r.nextDouble(), a.minZ, a.maxZ), 0.0D, 0.01D, 0.0D);
            }
            return;
        }
        int n = Math.round(14 * s);                                    // a thick, rolling wall of smoke
        for (int i = 0; i < n; i++) {
            double x = Mth.lerp(r.nextDouble(), a.minX, a.maxX), y = a.minY + 0.5D + r.nextDouble() * (SmokeCloudEntity.HEIGHT - 0.5D);
            double z = Mth.lerp(r.nextDouble(), a.minZ, a.maxZ);
            mc.level.addParticle(r.nextBoolean() ? DARK : MID, x, y, z, 0.0D, 0.0D, 0.0D);
        }
        if (r.nextInt(3) == 0) {
            mc.level.addParticle(ParticleTypes.LARGE_SMOKE, Mth.lerp(r.nextDouble(), a.minX, a.maxX), a.minY + 0.2D,
                    Mth.lerp(r.nextDouble(), a.minZ, a.maxZ), 0.0D, 0.02D, 0.0D);
        }
    }

    /** Neon outlines on everyone blinded inside a cloud, and a faint frame around each cloud. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        blindedNearby = 0;
        if (mc.level == null || mc.player == null || !tactical(mc)) return;
        PoseStack ps = event.getPoseStack();
        float time = mc.level.getGameTime() + event.getPartialTick();
        BufferBuilder b = null;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof SmokeCloudEntity cloud) || cloud.distanceToSqr(mc.player) > 64.0D * 64.0D) continue;
            if (b == null) b = WorldLines.begin(event.getCamera().getPosition(), true, 2.5F);
            float s = cloud.strength();
            AABB area = cloud.area();
            WorldLines.box(ps, b, area, Holo.NEON, 0.18F * s);
            for (LivingEntity le : mc.level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (le == mc.player || !le.isAlive()) continue;
                blindedNearby++;
                int c = EntityScanner.classify(le);
                float pulse = 0.65F + 0.35F * Mth.sin(time * 0.4F + le.getId());
                AABB bb = le.getBoundingBox().inflate(0.06D);
                WorldLines.box(ps, b, bb.inflate(0.06D), c, 0.3F * s);     // soft glow
                WorldLines.box(ps, b, bb, c, pulse * s);
            }
        }
        if (b != null) WorldLines.end();
    }

    static void renderHud(GuiGraphics g, Minecraft mc, int sw, float time) {
        if (blindedNearby <= 0) return;
        Font font = mc.font;
        String s = "SMOKE MATRIX // " + blindedNearby + " BLINDED";
        float a = 0.75F + 0.25F * Mth.sin(time * 0.3F);
        Holo.glowText(g, font, s, sw / 2.0F - Holo.width(font, s, 0.7F) / 2.0F, 56, 0.7F, Holo.NEON, a);
    }
}
