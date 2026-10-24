package com.geist.client;

import com.geist.GeistMod;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Portal Syncer: look at a Nether Portal and the lens runs a diagnostic, showing the math and the exact
 * coordinates to build the linked portal in the other dimension. End portals and gateways report where
 * they lead.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class PortalSyncer {
    private static final int DIAG_TICKS = 26;
    private enum Kind { NETHER, END, EXIT, GATEWAY }

    @Nullable private static AABB portal;            // the whole portal (all connected portal blocks)
    @Nullable private static Kind kind;
    private static long lookStart;
    private static int anchorX, anchorY, anchorZ;      // bottom-centre portal block

    private PortalSyncer() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !GeistState.module(mc, GeistState.PORTAL)) { portal = null; return; }
        HitResult hit = mc.player.pick(16.0D, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) { portal = null; return; }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockState st = mc.level.getBlockState(pos);
        Kind k = st.is(Blocks.NETHER_PORTAL) ? Kind.NETHER
                : st.is(Blocks.END_PORTAL) ? (mc.level.dimension() == Level.END ? Kind.EXIT : Kind.END)
                : st.is(Blocks.END_GATEWAY) ? Kind.GATEWAY : null;
        if (k == null) { portal = null; return; }
        if (portal != null && kind == k && portal.inflate(0.5D).contains(net.minecraft.world.phys.Vec3.atCenterOf(pos))) return;   // same portal, keep going
        // a new portal: map all its connected blocks and start the diagnostic
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        seen.add(pos);
        int minX = pos.getX(), minY = pos.getY(), minZ = pos.getZ(), maxX = minX, maxY = minY, maxZ = minZ;
        while (!queue.isEmpty() && seen.size() < 600) {
            BlockPos p = queue.poll();
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && mc.level.getBlockState(n).is(st.getBlock())) { seen.add(n); queue.add(n); }
            }
        }
        portal = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        kind = k;
        anchorX = Mth.floor((minX + maxX + 1) / 2.0D);
        anchorY = minY;
        anchorZ = Mth.floor((minZ + maxZ + 1) / 2.0D);
        lookStart = mc.level.getGameTime();
        mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
    }

    private static float diag(Minecraft mc, float pt) {
        return Mth.clamp((mc.level.getGameTime() - lookStart + pt) / DIAG_TICKS, 0.0F, 1.0F);
    }

    /** A wireframe around the portal, with a scan line sweeping across it during the diagnostic. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (portal == null || mc.level == null || !GeistState.module(mc, GeistState.PORTAL)) return;
        PoseStack ps = event.getPoseStack();
        float d = diag(mc, event.getPartialTick());
        float time = mc.level.getGameTime() + event.getPartialTick();
        BufferBuilder b = WorldLines.begin(event.getCamera().getPosition(), false, 2.0F);
        AABB box = portal.inflate(0.02D);
        WorldLines.box(ps, b, box, Holo.NEON, 0.55F + 0.25F * Mth.sin(time * 0.3F));
        WorldLines.box(ps, b, box.inflate(0.05D), Holo.NEON, 0.2F);
        if (d < 1.0F) {
            double y = box.minY + (box.maxY - box.minY) * (0.5D + 0.5D * Math.sin(time * 0.5D));
            WorldLines.line(ps, b, box.minX, y, box.minZ, box.maxX, y, box.minZ, Holo.WHITE, 0.9F);
            WorldLines.line(ps, b, box.minX, y, box.maxZ, box.maxX, y, box.maxZ, Holo.WHITE, 0.9F);
            WorldLines.line(ps, b, box.minX, y, box.minZ, box.minX, y, box.maxZ, Holo.WHITE, 0.9F);
            WorldLines.line(ps, b, box.maxX, y, box.minZ, box.maxX, y, box.maxZ, Holo.WHITE, 0.9F);
        }
        WorldLines.end();
    }

    /** Is the portal panel showing (you are looking at a portal)? */
    static boolean active(Minecraft mc) {
        return portal != null && kind != null && GeistState.module(mc, GeistState.PORTAL);
    }

    static void render(GuiGraphics g, Minecraft mc, int sw, int sh, float pt, float time) {
        if (portal == null || kind == null || !GeistState.module(mc, GeistState.PORTAL)) return;
        Font font = mc.font;
        float d = diag(mc, pt);
        int w = 150, h = kind == Kind.NETHER ? 92 : 56;
        int x = sw / 2 + 26, y = sh / 2 - h / 2;
        if (x + w > sw - 4) x = sw / 2 - 26 - w;
        g.fill(x, y, x + w, y + h, Holo.argb(0.6F, 0x04121C));
        Holo.frame(g, x, y, x + w, y + h, 3, Holo.NEON, 0.9F, 1.0F);
        Holo.line(g, sw / 2 + 6, sh / 2, x, y + 8, Holo.argb(0.6F, Holo.NEON));
        String title = switch (kind) {
            case NETHER -> mc.level.dimension() == Level.NETHER ? "PORTAL SYNC // OVERWORLD LINK" : "PORTAL SYNC // NETHER LINK";
            case END -> "PORTAL SYNC // END PORTAL";
            case EXIT -> "PORTAL SYNC // EXIT PORTAL";
            case GATEWAY -> "PORTAL SYNC // END GATEWAY";
        };
        Holo.glowText(g, font, title, x + 5, y + 4, 0.6F, Holo.NEON, 1.0F);

        if (d < 1.0F) {                                                 // the diagnostic: streaming numbers and a progress bar
            Holo.text(g, font, "RUNNING VISUAL DIAGNOSTIC", x + 5, y + 15, 0.6F, Holo.argb(0.9F, Holo.WHITE));
            StringBuilder sb = new StringBuilder();
            long seed = (long) (time * 3) * 2654435761L;
            for (int i = 0; i < 26; i++) { seed = seed * 6364136223846793005L + 1442695040888963407L; sb.append("0123456789ABCDEF.\u00F7\u00D7".charAt((int) ((seed >>> 33) % 19))); }
            Holo.text(g, font, sb.toString(), x + 5, y + 25, 0.6F, Holo.argb(0.7F, Holo.NEON));
            int bw = w - 10;
            g.fill(x + 5, y + 36, x + 5 + bw, y + 38, Holo.argb(0.3F, Holo.NEON));
            g.fill(x + 5, y + 36, x + 5 + Math.round(bw * d), y + 38, Holo.argb(1.0F, Holo.NEON));
            Holo.text(g, font, Math.round(d * 100) + "%", x + 5, y + 42, 0.6F, Holo.argb(0.9F, Holo.WHITE));
            return;
        }
        switch (kind) {
            case NETHER -> netherLink(g, font, mc, x, y);
            case END -> {
                Holo.text(g, font, "DESTINATION  THE END", x + 5, y + 16, 0.65F, Holo.argb(1.0F, Holo.WHITE));
                Holo.text(g, font, "ARRIVAL PLATFORM", x + 5, y + 27, 0.6F, Holo.argb(0.9F, Holo.NEON));
                Holo.glowText(g, font, "100, 49, 0", x + 5, y + 35, 1.0F, Holo.WHITE, 1.0F);
                Holo.text(g, font, "NO LINKED PORTAL NEEDED", x + 5, y + 47, 0.55F, Holo.argb(0.85F, Holo.GREEN));
            }
            case EXIT -> {
                Holo.text(g, font, "DESTINATION  OVERWORLD", x + 5, y + 16, 0.65F, Holo.argb(1.0F, Holo.WHITE));
                Holo.text(g, font, "RETURNS YOU TO YOUR", x + 5, y + 28, 0.6F, Holo.argb(0.9F, Holo.NEON));
                Holo.text(g, font, "SPAWN POINT (BED / ANCHOR)", x + 5, y + 36, 0.6F, Holo.argb(0.9F, Holo.NEON));
                Holo.text(g, font, "NO LINKED PORTAL NEEDED", x + 5, y + 47, 0.55F, Holo.argb(0.85F, Holo.GREEN));
            }
            case GATEWAY -> {
                Holo.text(g, font, "SHORT-RANGE END TELEPORT", x + 5, y + 16, 0.65F, Holo.argb(1.0F, Holo.WHITE));
                Holo.text(g, font, "LINKS TO THE OUTER ISLANDS", x + 5, y + 28, 0.6F, Holo.argb(0.9F, Holo.NEON));
                Holo.text(g, font, "(~1000 BLOCKS FROM CENTRE)", x + 5, y + 36, 0.6F, Holo.argb(0.9F, Holo.NEON));
                Holo.text(g, font, "THROW AN ENDER PEARL IN", x + 5, y + 47, 0.55F, Holo.argb(0.85F, Holo.GREEN));
            }
        }
    }

    /** The math for a nether portal: x8 into the Overworld, /8 into the Nether, with Y kept in range. */
    private static void netherLink(GuiGraphics g, Font font, Minecraft mc, int x, int y) {
        boolean inNether = mc.level.dimension() == Level.NETHER;
        if (!inNether && mc.level.dimension() != Level.OVERWORLD) {
            Holo.text(g, font, "NO LINK: PORTALS ONLY JOIN", x + 5, y + 16, 0.6F, Holo.argb(0.9F, Holo.AMBER));
            Holo.text(g, font, "THE OVERWORLD AND THE NETHER", x + 5, y + 24, 0.6F, Holo.argb(0.9F, Holo.AMBER));
            return;
        }
        String op = inNether ? "\u00D7 8" : "\u00F7 8";
        double fx = inNether ? anchorX * 8.0D : anchorX / 8.0D, fz = inNether ? anchorZ * 8.0D : anchorZ / 8.0D;
        int tx = Mth.floor(fx), tz = Mth.floor(fz);
        int ty = inNether ? Mth.clamp(anchorY, -59, 315) : Mth.clamp(anchorY, 5, 118);
        String here = inNether ? "NETHER" : "OVERWORLD", there = inNether ? "OVERWORLD" : "NETHER";
        Holo.text(g, font, "SOURCE " + here + "  " + anchorX + ", " + anchorY + ", " + anchorZ, x + 5, y + 14, 0.55F, Holo.argb(0.9F, Holo.WHITE));
        int ly = y + 23;
        Holo.text(g, font, String.format("X  %d %s = %.2f \u2192 %d", anchorX, op, fx, tx), x + 5, ly, 0.6F, Holo.argb(1.0F, Holo.NEON));
        Holo.text(g, font, String.format("Y  %d \u2192 %d%s", anchorY, ty, ty != anchorY ? " (in range)" : ""), x + 5, ly + 8, 0.6F, Holo.argb(1.0F, Holo.NEON));
        Holo.text(g, font, String.format("Z  %d %s = %.2f \u2192 %d", anchorZ, op, fz, tz), x + 5, ly + 16, 0.6F, Holo.argb(1.0F, Holo.NEON));
        Holo.hline(g, x + 5, x + 145, ly + 26, Holo.argb(0.5F, Holo.NEON));
        Holo.text(g, font, "BUILD LINKED PORTAL AT (" + there + ")", x + 5, ly + 30, 0.55F, Holo.argb(0.95F, Holo.WHITE));
        Holo.glowText(g, font, tx + ", " + ty + ", " + tz, x + 5, ly + 39, 1.1F, Holo.WHITE, 1.0F);
        String tip = inNether ? "LINKS IF NO OTHER PORTAL WITHIN 128" : "LINKS IF NO OTHER PORTAL WITHIN 16";
        Holo.text(g, font, tip, x + 5, ly + 54, 0.5F, Holo.argb(0.85F, Holo.GREEN));
    }
}
