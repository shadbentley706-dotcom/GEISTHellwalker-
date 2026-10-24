package com.grapefruit.hellblazer.client.ui;

import com.grapefruit.hellblazer.HellblazerMod;
import com.grapefruit.hellblazer.occult.CaseType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The casebook's drawing kit: a screen of dark red glass (scanlines, a slow refresh band, grain, a vignette) with a
 * crack running through it from two impacts - radial splits, branches and the spider-web rings between them, shadowed
 * and catching the light - panels with registration corners, text that bleeds in out of scratched glyphs, word wrap,
 * little pixel icons for each kind of case, the rubber stamp, the sigil, and 3D pictures of items and creatures.
 */
public final class Grim {
    public static final int BG = 0x0B0304, PANEL = 0x170607, LINE = 0x4A1418, RED = 0xE0242E, RED_D = 0x8A1820, BLOOD = 0x6E0A10,
            EMBER = 0xFF6A3C, BONE = 0xEFE3D2, GREY = 0xA8968F, DIM = 0x6A5450, WHITE = 0xF6EEE6, GOLD = 0xD8A84A, GREEN = 0x9BD47A;
    public static final ResourceLocation SIGIL = new ResourceLocation(HellblazerMod.MODID, "textures/gui/sigil.png");
    private static final String GLYPHS = "#%&@*+=<>?/\\|†‡ΨΩ∆ҖӜ0123456789XZ";
    /** Crack networks, by seed: segments {x0, y0, x1, y1, reveal} in 0..1 screen fractions. */
    private static final Map<Long, List<float[]>> CRACKS = new HashMap<>();

    private Grim() {}

    public static int a(float alpha, int rgb) {
        return Hud.argb(alpha, rgb);
    }

    public static float ease(float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
    }

    public static float easeInOut(float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    public static float hash(int a, int b) {
        int n = a * 73856093 ^ b * 19349663;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xFFFF) / 65535.0F;
    }

    // ================================================================ glass

    /** The screen: dark red glass, a faint grid, scanlines, a slow refresh band, grain and a vignette. */
    public static void screen(GuiGraphics g, int x0, int y0, int x1, int y1, float t, float alpha) {
        g.drawManaged(() -> glass(g, x0, y0, x1, y1, t, alpha));
    }

    private static void glass(GuiGraphics g, int x0, int y0, int x1, int y1, float t, float alpha) {
        g.fill(x0, y0, x1, y1, a(0.95F * alpha, BG));
        g.fillGradient(x0, y0, x1, y1, a(0.10F * alpha, BLOOD), a(0.22F * alpha, BLOOD));
        int gc = a(0.045F * alpha, RED);
        for (int x = x0 + 8; x < x1; x += 16) g.fill(x, y0, x + 1, y1, gc);
        for (int y = y0 + 8; y < y1; y += 16) g.fill(x0, y, x1, y + 1, gc);
        int h = y1 - y0;
        float band = (t * 1.3F) % (h + 60) - 30;
        int b0 = Math.max(y0, (int) (y0 + band - 30)), b1 = Math.min(y1, (int) (y0 + band)), b2 = Math.min(y1, (int) (y0 + band + 4));
        if (b0 < b1) g.fillGradient(x0, b0, x1, b1, a(0.0F, RED), a(0.06F * alpha, RED));
        if (Math.max(y0, b1) < b2) g.fillGradient(x0, Math.max(y0, b1), x1, b2, a(0.07F * alpha, BONE), a(0.0F, BONE));
        int sl = a(0.2F * alpha, 0x000000);
        for (int y = y0; y < y1; y += 2) g.fill(x0, y, x1, y + 1, sl);
        int seed = (int) (t * 0.5F);
        for (int i = 0; i < 46; i++) {
            float r = hash(seed, i);
            int x = x0 + (int) (hash(i, seed) * (x1 - x0)), y = y0 + (int) (r * (y1 - y0));
            g.fill(x, y, x + 1, y + 1, a(0.12F * alpha * hash(i * 3, seed), BONE));
        }
        vignette(g, x0, y0, x1, y1, alpha);
    }

    public static void vignette(GuiGraphics g, int x0, int y0, int x1, int y1, float alpha) {
        int d = Math.max(8, Math.min(x1 - x0, y1 - y0) / 6);
        g.fillGradient(x0, y0, x1, y0 + d, a(0.6F * alpha, 0x000000), a(0.0F, 0x000000));
        g.fillGradient(x0, y1 - d, x1, y1, a(0.0F, 0x000000), a(0.65F * alpha, 0x000000));
        for (int i = 0; i < 8; i++) {
            float k = (8 - i) / 8.0F;
            int w = d / 8 + 1;
            int c = a(0.08F * alpha * k, 0x000000);
            g.fill(x0 + i * w, y0, x0 + (i + 1) * w, y1, c);
            g.fill(x1 - (i + 1) * w, y0, x1 - i * w, y1, c);
        }
    }

    // ================================================================ the crack

    private static List<float[]> crackSet(long seed) {
        return CRACKS.computeIfAbsent(seed, s -> {
            List<float[]> out = new ArrayList<>();
            RandomSource r = RandomSource.create(s);
            float[][] impacts = {{0.74F + r.nextFloat() * 0.1F, 0.16F + r.nextFloat() * 0.12F}, {0.12F + r.nextFloat() * 0.12F, 0.72F + r.nextFloat() * 0.14F}};
            for (int k = 0; k < impacts.length; k++) {
                float ix = impacts[k][0], iy = impacts[k][1];
                int arms = k == 0 ? 12 + r.nextInt(3) : 7 + r.nextInt(3);
                float reach = k == 0 ? 1.0F : 0.45F;
                List<List<float[]>> rays = new ArrayList<>();
                for (int i = 0; i < arms; i++) {
                    double ang = i * Math.PI * 2.0D / arms + (r.nextDouble() - 0.5D) * 0.4D;
                    List<float[]> ray = new ArrayList<>();
                    walk(out, ray, r, ix, iy, ang, (0.25F + r.nextFloat() * 0.55F) * reach, 0, 0.0F);
                    rays.add(ray);
                }
                // the web: jagged rings joining neighbouring rays
                int rings = k == 0 ? 3 : 1;
                for (int q = 0; q < rings; q++) {
                    float rr = (0.035F + q * 0.05F + r.nextFloat() * 0.015F) * (k == 0 ? 1.0F : 0.8F);
                    for (int i = 0; i < rays.size(); i++) {
                        float[] p0 = nearest(rays.get(i), ix, iy, rr), p1 = nearest(rays.get((i + 1) % rays.size()), ix, iy, rr);
                        if (p0 == null || p1 == null || r.nextFloat() < 0.25F) continue;
                        float mx = (p0[0] + p1[0]) / 2.0F + (r.nextFloat() - 0.5F) * 0.012F, my = (p0[1] + p1[1]) / 2.0F + (r.nextFloat() - 0.5F) * 0.012F;
                        out.add(new float[]{p0[0], p0[1], mx, my, rr / reach * 0.7F});
                        out.add(new float[]{mx, my, p1[0], p1[1], rr / reach * 0.7F});
                    }
                }
                // shattered at the impact itself
                for (int i = 0; i < 18; i++) {
                    double ang = r.nextDouble() * Math.PI * 2.0D;
                    float l = 0.006F + r.nextFloat() * 0.018F;
                    out.add(new float[]{ix, iy, ix + (float) Math.cos(ang) * l, iy + (float) Math.sin(ang) * l, 0.0F});
                }
            }
            return out;
        });
    }

    private static void walk(List<float[]> out, List<float[]> ray, RandomSource r, float x, float y, double ang, float len, int depth, float start) {
        float travelled = 0.0F;
        ray.add(new float[]{x, y});
        while (travelled < len && x > -0.02F && x < 1.02F && y > -0.02F && y < 1.02F) {
            float step = 0.018F + r.nextFloat() * 0.03F;
            ang += (r.nextDouble() - 0.5D) * 0.5D;
            float nx = x + (float) Math.cos(ang) * step, ny = y + (float) Math.sin(ang) * step;
            out.add(new float[]{x, y, nx, ny, Math.min(1.0F, start + travelled)});
            x = nx;
            y = ny;
            travelled += step;
            ray.add(new float[]{x, y});
            if (depth < 2 && r.nextFloat() < 0.12F) {
                walk(out, new ArrayList<>(), r, x, y, ang + (r.nextBoolean() ? 0.7D : -0.7D) * (0.6D + r.nextDouble() * 0.6D), len * 0.35F, depth + 1,
                        start + travelled);
            }
        }
    }

    private static float[] nearest(List<float[]> ray, float ix, float iy, float rr) {
        float[] best = null;
        float bd = Float.MAX_VALUE;
        for (float[] p : ray) {
            float d = Math.abs((float) Math.hypot(p[0] - ix, p[1] - iy) - rr);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return bd < 0.03F ? best : null;
    }

    /** The crack across a screen: `grow` 0..1 runs it out from the impacts (the screen being struck). */
    public static void cracks(GuiGraphics g, int x0, int y0, int x1, int y1, long seed, float grow, float alpha) {
        if (alpha <= 0.01F || grow <= 0.0F) return;
        int w = x1 - x0, h = y1 - y0;
        int dark = a(0.62F * alpha, 0x000000), lite = a(0.22F * alpha, 0xFFD9D2), red = a(0.16F * alpha, RED);
        List<float[]> set = crackSet(seed);
        // thousands of one-pixel fills: batched into one draw instead of one draw each
        g.drawManaged(() -> {
            for (float[] s : set) {
                if (s[4] > grow) continue;
                int ax = x0 + Math.round(s[0] * w), ay = y0 + Math.round(s[1] * h), bx = x0 + Math.round(s[2] * w), by = y0 + Math.round(s[3] * h);
                ax = Mth.clamp(ax, x0, x1 - 1);
                bx = Mth.clamp(bx, x0, x1 - 1);
                ay = Mth.clamp(ay, y0, y1 - 1);
                by = Mth.clamp(by, y0, y1 - 1);
                Hud.line(g, ax, ay, bx, by, dark);
                Hud.line(g, ax + 1, ay, bx + 1, by, lite);
                Hud.line(g, ax, ay + 1, bx, by + 1, red);
            }
        });
    }

    // ================================================================ panels

    /** A dark glass panel with a thin frame and registration corners. */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int accent, float alpha) {
        g.fill(x0, y0, x1, y1, a(0.72F * alpha, PANEL));
        int c = a(0.35F * alpha, accent);
        g.fill(x0, y0, x1, y0 + 1, c);
        g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c);
        g.fill(x1 - 1, y0, x1, y1, c);
        corners(g, x0, y0, x1, y1, 4, a(0.9F * alpha, accent));
    }

    public static void corners(GuiGraphics g, int x0, int y0, int x1, int y1, int len, int c) {
        g.fill(x0, y0, x0 + len, y0 + 1, c);
        g.fill(x0, y0, x0 + 1, y0 + len, c);
        g.fill(x1 - len, y0, x1, y0 + 1, c);
        g.fill(x1 - 1, y0, x1, y0 + len, c);
        g.fill(x0, y1 - 1, x0 + len, y1, c);
        g.fill(x0, y1 - len, x0 + 1, y1, c);
        g.fill(x1 - len, y1 - 1, x1, y1, c);
        g.fill(x1 - 1, y1 - len, x1, y1, c);
    }

    /** A section header: a small tag, the title, a rule to the right edge. Returns the height used. */
    public static int header(GuiGraphics g, Font font, String title, int x0, int y, int x1, int rgb, float alpha) {
        int lh = Hud.lineH(Hud.SMALL);
        g.fill(x0, y + 1, x0 + 2, y + lh - 1, a(alpha, rgb));
        Hud.say(g, font, title, x0 + 5, y, Hud.SMALL, a(alpha, rgb));
        int tx = x0 + 9 + Hud.textW(font, title, Hud.SMALL);
        if (tx < x1) g.fill(tx, y + lh / 2, x1, y + lh / 2 + 1, a(0.3F * alpha, rgb));
        return lh + 2;
    }

    // ================================================================ text

    /** Text that bleeds in out of scratched glyphs: `revealed` characters are plain, the next few churn. */
    public static String bleed(String s, float revealed, int seed) {
        if (revealed >= s.length()) return s;
        StringBuilder out = new StringBuilder(s.length());
        int shown = Math.max(0, (int) revealed);
        Minecraft mc = Minecraft.getInstance();
        int churn = (int) (mc.level != null ? mc.level.getGameTime() : 0);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (i < shown || ch == ' ') out.append(ch);
            else if (i < shown + 6) out.append(GLYPHS.charAt((int) (hash(i + seed, churn) * GLYPHS.length()) % GLYPHS.length()));
            else out.append(' ');
        }
        return out.toString();
    }

    /** Title text with a red fringe, like a picture on a failing tube. */
    public static void fringe(GuiGraphics g, Font font, String s, float x, float y, float size, int rgb, float alpha) {
        Hud.say(g, font, s, x - 0.7F, y, size, a(0.35F * alpha, 0xFF1020));
        Hud.say(g, font, s, x + 0.5F, y, size, a(0.18F * alpha, 0xFFE0D0));
        Hud.say(g, font, s, x, y, size, a(alpha, rgb));
    }

    /** Words wrapped to a width. */
    public static List<String> wrap(Font font, String s, float size, int maxW) {
        List<String> out = new ArrayList<>();
        for (String para : s.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String w : para.split(" ")) {
                String next = line.length() == 0 ? w : line + " " + w;
                if (Hud.textW(font, next, size) > maxW && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(w);
                } else {
                    line = new StringBuilder(next);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    // ================================================================ little pictures

    /** A pixel icon from rows of '#' and '.', each cell s by s. */
    public static void glyph(GuiGraphics g, String[] rows, int x, int y, int s, int argb) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) == '#') g.fill(x + c * s, y + r * s, x + (c + 1) * s, y + (r + 1) * s, argb);
            }
        }
    }

    private static final String[][] TYPE_ICONS = {
            {"..#.#..", ".#####.", "##.#.##", "#######", ".#.#.#.", ".#...#.", "..###.."},       // HUNT: a skull
            {"...#...", "..###..", ".#.#.#.", "#######", ".#.#.#.", "..###..", "...#..."},       // TARGET: the mark on the door
            {"..###..", ".#####.", "#.#.#.#", "#######", "#######", "#.#.#.#", "#.#.#.#"},       // HAUNTING: a spirit
            {"..###..", ".#...#.", ".#...#.", "#######", "#.###.#", "#..#..#", "#######"},       // RELIC: a box to put it in
            {".##....", "#..#...", "#..#...", ".##....", "...##..", "....##.", ".....##"}};      // INVESTIGATE: a glass

    public static String[] typeIcon(CaseType t) {
        return TYPE_ICONS[t.ordinal()];
    }

    public static int typeColor(CaseType t) {
        return switch (t) {
            case HUNT -> RED;
            case TARGET -> GOLD;
            case HAUNTING -> 0xB9D4E4;
            case RELIC -> 0xC07AE0;
            case INVESTIGATE -> BONE;
        };
    }

    /** Danger: five little marks, the first `level` lit. */
    public static void threat(GuiGraphics g, int x, int y, int level, int s, float alpha) {
        for (int i = 0; i < 5; i++) {
            boolean on = i < level;
            int c = on ? a(alpha, level >= 4 ? RED : level >= 3 ? EMBER : GOLD) : a(0.22F * alpha, GREY);
            g.fill(x + i * (s + 1), y, x + i * (s + 1) + s, y + s, c);
        }
    }

    /** A segmented bar. */
    public static void progress(GuiGraphics g, int x, int y, int w, int h, float f, int rgb, float alpha) {
        g.fill(x, y, x + w, y + h, a(0.5F * alpha, 0x000000));
        int fill = Math.round(w * Mth.clamp(f, 0.0F, 1.0F));
        g.fill(x, y, x + fill, y + h, a(0.85F * alpha, rgb));
        for (int sx = x + 4; sx < x + w; sx += 4) g.fill(sx, y, sx + 1, y + h, a(0.45F * alpha, 0x000000));
        g.fill(x, y + h, x + w, y + h + 1, a(0.25F * alpha, rgb));
    }

    /** The sigil (the tab icon's own art), tinted, w by h GUI pixels. */
    public static void sigil(GuiGraphics g, float x, float y, float w, float h, int rgb, float alpha) {
        if (alpha <= 0.01F) return;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F, Mth.clamp(alpha, 0.0F, 1.0F));
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(w / 128.0F, h / 256.0F, 1.0F);
        g.blit(SIGIL, 0, 0, 0.0F, 0.0F, 128, 256, 128, 256);
        g.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * A rubber stamp slamming down: it drops from big and blurred to its size in a few ticks, rotated, double-ruled.
     * t: ticks since it was stamped.
     */
    public static void stamp(GuiGraphics g, Font font, String text, float cx, float cy, float t, int rgb, float alpha) {
        if (t < 0.0F) return;
        float drop = Mth.clamp(t / 4.0F, 0.0F, 1.0F);
        float scale = 1.0F + (1.0F - ease(drop)) * 1.6F;
        float al = alpha * (t < 4.0F ? 0.5F + 0.5F * drop : 1.0F) * (1.0F - Mth.clamp((t - 50.0F) / 15.0F, 0.0F, 1.0F));
        if (al <= 0.01F) return;
        float size = Hud.px(1.5F);
        int tw = Math.round(font.width(text) * size), th = Math.round(9 * size);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 300.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-9.0F));
        g.pose().scale(scale, scale, 1.0F);
        int w = tw / 2 + 6, h = th / 2 + 4;
        int c = a(al, rgb);
        g.fill(-w, -h, w, -h + 2, c);
        g.fill(-w, h - 2, w, h, c);
        g.fill(-w, -h, -w + 2, h, c);
        g.fill(w - 2, -h, w, h, c);
        g.fill(-w + 3, -h + 3, w - 3, -h + 4, c);
        g.fill(-w + 3, h - 4, w - 3, h - 3, c);
        g.pose().pushPose();
        g.pose().translate(-tw / 2.0F, -th / 2.0F + 1, 0.0F);
        g.pose().scale(size, size, 1.0F);
        g.drawString(font, text, 0, 0, c, false);
        g.pose().popPose();
        for (int i = 0; i < 14; i++) {
            float hx = hash(i, 77) * 2 * w - w, hy = hash(77, i) * 2 * h - h;
            g.fill((int) hx, (int) hy, (int) hx + 2, (int) hy + 1, a(al * 0.8F, PANEL));
        }
        g.pose().popPose();
    }

    // ================================================================ 3D pictures

    /** An item as a turning 3D model, size GUI pixels across, centred on (cx, cy). */
    public static void item3d(GuiGraphics g, ItemStack s, float cx, float cy, float size, float yaw, float pitch) {
        if (s.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 150.0F);
        g.pose().scale(size, -size, size);
        g.pose().mulPose(Axis.XP.rotationDegrees(pitch));
        g.pose().mulPose(Axis.YP.rotationDegrees(yaw));
        Lighting.setupFor3DItems();
        mc.getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, g.pose(), g.bufferSource(), mc.level, 0);
        g.flush();
        Lighting.setupFor3DItems();
        g.pose().popPose();
    }

    /** A creature standing in a box (the case photo), turned a little toward the mouse. */
    public static void entity(GuiGraphics g, LivingEntity e, int cx, int bottom, int boxH, float mx, float my) {
        float big = Math.max(e.getBbHeight(), e.getBbWidth() * 1.2F);
        int scale = Math.max(4, Math.round(boxH * 0.78F / Math.max(0.4F, big)));
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, bottom, scale, cx - mx, Mth.clamp(bottom - boxH * 0.7F - my, -40.0F, 40.0F), e);
    }
}
