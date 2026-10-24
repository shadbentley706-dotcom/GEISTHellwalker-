package com.geist.client;

import com.geist.GeistMod;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Ore Pulse ("Scan Mode"): a wireframe sphere pulses out through the stone; diamond, gold and ancient
 * debris within 16 blocks light up with glowing wireframe outlines, visible through walls, for 12 seconds.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class OrePulse {
    public static final int RADIUS = 16, REVEAL_TICKS = 240, COOLDOWN = 200;
    private static final float WAVE_SPEED = 0.7F;                       // blocks per tick
    /** Every ore type, rarest first. Uses the shared Forge ore tags, so modded ores are found too. */
    static final String[] NAMES = {"DEBRIS", "DIAMOND", "EMERALD", "GOLD", "LAPIS", "REDSTONE", "IRON", "COPPER", "COAL", "QUARTZ", "OTHER"};
    static final int[] COLORS = {0xE36BFF, 0x5FF5FF, 0x3CFF6A, 0xFFD54A, 0x3A6BFF, 0xFF3030, 0xD8AF93, 0xE0824F, 0xA0A0A0, 0xF0EDE6, 0xB0B0FF};
    @SuppressWarnings("unchecked")
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>[] TAGS = new net.minecraft.tags.TagKey[]{
            Tags.Blocks.ORES_NETHERITE_SCRAP, Tags.Blocks.ORES_DIAMOND, Tags.Blocks.ORES_EMERALD, Tags.Blocks.ORES_GOLD,
            Tags.Blocks.ORES_LAPIS, Tags.Blocks.ORES_REDSTONE, Tags.Blocks.ORES_IRON, Tags.Blocks.ORES_COPPER,
            Tags.Blocks.ORES_COAL, Tags.Blocks.ORES_QUARTZ, Tags.Blocks.ORES};
    /** Which ores the pulse shows (set in the Ore Filter). Coal and copper start hidden: they are everywhere. */
    public static final boolean[] FILTER = {true, true, true, true, true, true, true, false, false, true, true};

    private record Ore(BlockPos pos, int kind, float dist) {}

    private static final List<Ore> ORES = new ArrayList<>();
    private static final int[] COUNTS = new int[NAMES.length];
    private static long pulseStart = -100000L, readyAt;
    private static Vec3 origin = Vec3.ZERO;

    private OrePulse() {}

    public static float cooldownLeft(Minecraft mc) {
        return mc.level == null ? 0.0F : Math.max(0.0F, (readyAt - mc.level.getGameTime()) / 20.0F);
    }

    private static int kind(BlockState st) {
        if (!st.is(Tags.Blocks.ORES) && !st.is(Tags.Blocks.ORES_NETHERITE_SCRAP)) return -1;   // quick reject
        for (int i = 0; i < TAGS.length; i++) if (st.is(TAGS[i])) return i;
        return -1;
    }

    /** Fire the pulse (from the command wheel). */
    public static void pulse(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        long now = mc.level.getGameTime();
        if (now < readyAt) {
            mc.player.displayClientMessage(Component.translatable("message.geist.ore_cooldown",
                    String.format("%.0f", cooldownLeft(mc))).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ORES.clear();
        java.util.Arrays.fill(COUNTS, 0);
        BlockPos c = mc.player.blockPosition();
        origin = mc.player.position().add(0.0D, 1.0D, 0.0D);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int minY = mc.level.getMinBuildHeight(), maxY = mc.level.getMaxBuildHeight();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                int y = c.getY() + dy;
                if (y < minY || y >= maxY) continue;
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > RADIUS * RADIUS) continue;
                    m.set(c.getX() + dx, y, c.getZ() + dz);
                    int k = kind(mc.level.getBlockState(m));
                    if (k >= 0) {
                        ORES.add(new Ore(m.immutable(), k, (float) Math.sqrt(d2)));
                        COUNTS[k]++;
                    }
                }
            }
        }
        pulseStart = now;
        readyAt = now + COOLDOWN;
        mc.player.playSound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
        mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.4F);
        mc.player.displayClientMessage(Component.translatable("message.geist.ore_pulse", ORES.size()).withStyle(ChatFormatting.AQUA), true);
    }

    private static float age(Minecraft mc, float pt) {
        return mc.level == null ? 1.0E9F : mc.level.getGameTime() - pulseStart + pt;
    }

    /** Forget ores you have mined. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ORES.isEmpty() || mc.level.getGameTime() % 10 != 0) return;
        ORES.removeIf(o -> {
            if (kind(mc.level.getBlockState(o.pos())) >= 0) return false;
            COUNTS[o.kind()] = Math.max(0, COUNTS[o.kind()] - 1);
            return true;
        });
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !GeistState.hudActive(mc)) return;
        float t = age(mc, event.getPartialTick());
        if (t > REVEAL_TICKS) return;
        PoseStack ps = event.getPoseStack();
        float wave = t * WAVE_SPEED;
        BufferBuilder b = WorldLines.begin(event.getCamera().getPosition(), true, 2.5F);
        // the pulse: a wireframe sphere sweeping outward through the stone
        if (wave < RADIUS + 3) {
            float a = 0.8F * (1.0F - wave / (RADIUS + 3));
            int seg = 32;
            for (int lat = -3; lat <= 3; lat++) {
                double phi = lat * Math.PI / 8.0D, ry = Math.cos(phi) * wave, y = origin.y + Math.sin(phi) * wave;
                for (int i = 0; i < seg; i++) {
                    double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
                    WorldLines.line(ps, b, origin.x + Math.cos(a0) * ry, y, origin.z + Math.sin(a0) * ry,
                            origin.x + Math.cos(a1) * ry, y, origin.z + Math.sin(a1) * ry, Holo.NEON, a);
                }
            }
            for (int mer = 0; mer < 8; mer++) {
                double th = mer * Math.PI / 8.0D;
                for (int i = 0; i < seg; i++) {
                    double p0 = i * Math.PI * 2 / seg, p1 = (i + 1) * Math.PI * 2 / seg;
                    WorldLines.line(ps, b,
                            origin.x + Math.cos(th) * Math.cos(p0) * wave, origin.y + Math.sin(p0) * wave, origin.z + Math.sin(th) * Math.cos(p0) * wave,
                            origin.x + Math.cos(th) * Math.cos(p1) * wave, origin.y + Math.sin(p1) * wave, origin.z + Math.sin(th) * Math.cos(p1) * wave,
                            Holo.NEON, a * 0.6F);
                }
            }
        }
        // revealed ores: bright, pulsing wireframes; fading out over the last three seconds
        float fade = Mth.clamp((REVEAL_TICKS - t) / 60.0F, 0.0F, 1.0F);
        float pulse = 0.7F + 0.3F * Mth.sin(t * 0.3F);
        for (Ore o : ORES) {
            if (o.dist() > wave || !FILTER[o.kind()]) continue;
            float pop = Mth.clamp((wave - o.dist()) / 2.0F, 0.0F, 1.0F);   // flashes in as the wave passes
            AABB box = new AABB(o.pos()).inflate(0.004D);
            WorldLines.box(ps, b, box.inflate(0.03D), COLORS[o.kind()], 0.25F * fade * pop);
            WorldLines.box(ps, b, box, COLORS[o.kind()], fade * pulse * pop);
        }
        WorldLines.end();
    }

    /** Readout of what the pulse found (right side, under the other panels). */
    static void renderHud(GuiGraphics g, Minecraft mc, int sw, int sh, float pt, int topY) {
        float t = age(mc, pt);
        if (t > REVEAL_TICKS) return;
        Font font = mc.font;
        float fade = Mth.clamp((REVEAL_TICKS - t) / 30.0F, 0.0F, 1.0F);
        if (t < 8) g.fill(0, 0, sw, sh, Holo.argb(0.18F * (1.0F - t / 8.0F), Holo.NEON));   // flash
        List<Integer> rows = new ArrayList<>();
        for (int k = 0; k < NAMES.length; k++) if (FILTER[k] && COUNTS[k] > 0) rows.add(k);
        int w = 112, h = 22 + Math.max(1, rows.size()) * 9 + 6, x = sw - 10 - w, y = topY;
        g.fill(x, y, x + w, y + h, Holo.argb(0.5F * fade, 0x04121C));
        Holo.frame(g, x, y, x + w, y + h, 3, Holo.NEON, 0.9F * fade, 1.0F);
        Holo.glowText(g, font, "ORE PULSE // " + RADIUS + "M", x + 5, y + 4, 0.6F, Holo.NEON, fade);
        if (rows.isEmpty()) Holo.text(g, font, "NO SELECTED ORES NEARBY", x + 6, y + 15, 0.55F, Holo.argb(0.9F * fade, Holo.WHITE));
        for (int i = 0; i < rows.size(); i++) {
            int k = rows.get(i);
            Holo.glowText(g, font, "\u25C6 " + NAMES[k], x + 6, y + 14 + i * 9, 0.62F, COLORS[k], fade);
            String n = String.valueOf(COUNTS[k]);
            Holo.glowText(g, font, n, x + w - 8 - Holo.width(font, n, 0.7F), y + 14 + i * 9, 0.7F, Holo.WHITE, fade);
        }
        int bx = x + 5, by = y + h - 5, bw = w - 10;
        g.fill(bx, by, bx + bw, by + 2, Holo.argb(0.3F * fade, Holo.NEON));
        g.fill(bx, by, bx + Math.round(bw * (1.0F - t / REVEAL_TICKS)), by + 2, Holo.argb(fade, Holo.NEON));
    }
}
