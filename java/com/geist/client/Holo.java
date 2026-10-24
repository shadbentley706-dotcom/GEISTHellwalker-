package com.geist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Drawing helpers for the holographic HUD: neon lines with glow, chamfered frames, arcs and scaled text. */
public final class Holo {
    public static final int NEON = 0x3FD3FF, GREEN = 0x4CFF7A, RED = 0xFF3B3B, BLUE = 0x3FA9FF, AMBER = 0xFFC14D, WHITE = 0xE8F6FF;

    private Holo() {}

    public static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static void hline(GuiGraphics g, int x0, int x1, int y, int c) {
        if (x1 < x0) { int t = x0; x0 = x1; x1 = t; }
        g.fill(x0, y, x1 + 1, y + 1, c);
    }

    public static void vline(GuiGraphics g, int x, int y0, int y1, int c) {
        if (y1 < y0) { int t = y0; y0 = y1; y1 = t; }
        g.fill(x, y0, x + 1, y1 + 1, c);
    }

    /** Any-angle line, pixel by pixel. */
    public static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        for (int n = 0; n < 2000; n++) {
            g.fill(x0, y0, x0 + 1, y0 + 1, c);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    /** A rectangle with 45-degree cut corners; {@code p} (0..1) draws it in from the corners during boot. */
    public static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int cut, int rgb, float alpha, float p) {
        int c = argb(alpha, rgb), glow = argb(alpha * 0.25F, rgb);
        for (int pass = 0; pass < 2; pass++) {
            int col = pass == 0 ? glow : c, o = pass == 0 ? 1 : 0;
            int a0 = x0 - o, b0 = y0 - o, a1 = x1 + o, b1 = y1 + o;
            int topLen = (int) ((a1 - a0 - 2 * cut) * p), sideLen = (int) ((b1 - b0 - 2 * cut) * p);
            int mx = (a0 + a1) / 2, my = (b0 + b1) / 2;
            hline(g, mx - topLen / 2, mx + topLen / 2, b0, col);
            hline(g, mx - topLen / 2, mx + topLen / 2, b1, col);
            vline(g, a0, my - sideLen / 2, my + sideLen / 2, col);
            vline(g, a1, my - sideLen / 2, my + sideLen / 2, col);
            if (p >= 1.0F) {
                line(g, a0, b0 + cut, a0 + cut, b0, col); line(g, a1 - cut, b0, a1, b0 + cut, col);
                line(g, a0, b1 - cut, a0 + cut, b1, col); line(g, a1 - cut, b1, a1, b1 - cut, col);
            }
        }
    }

    /** A circular arc from {@code a0} to {@code a1} degrees (0 = up, clockwise). */
    public static void arc(GuiGraphics g, float cx, float cy, float r, float a0, float a1, int c) {
        int steps = Math.max(8, (int) (Math.abs(a1 - a0) / 360.0F * 6.3F * r));
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / steps);
            int x = Math.round((float) (cx + Math.sin(a) * r)), y = Math.round((float) (cy - Math.cos(a) * r));
            g.fill(x, y, x + 1, y + 1, c);
        }
    }

    public static void text(GuiGraphics g, Font font, String s, float x, float y, float scale, int color) {
        if ((color >>> 24) < 6) return;              // nearly invisible (and Minecraft would draw it fully opaque)
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, s, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Text with a soft neon glow behind it. */
    public static void glowText(GuiGraphics g, Font font, String s, float x, float y, float scale, int rgb, float alpha) {
        int glow = argb(alpha * 0.3F, rgb);
        text(g, font, s, x - 0.5F, y, scale, glow);
        text(g, font, s, x + 0.5F, y, scale, glow);
        text(g, font, s, x, y, scale, argb(alpha, rgb));
    }

    public static int width(Font font, String s, float scale) {
        return Math.round(font.width(s) * scale);
    }

    // ---------------------------------------------------------------- sharp HUD text (v11)
    // Text drawn at in-between sizes (0.55, 0.6 ...) lands between screen pixels and turns blurry. These
    // helpers snap the size and position to whole screen pixels and add a dark shadow, so HUD text stays
    // sharp and readable over bright sky at every GUI scale.

    /** Text sizes: small labels, normal values, big headline numbers. */
    public static final float SMALL = 0.5F, MEDIUM = 0.75F, LARGE = 1.0F;

    private static double guiScale() {
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        return s > 0.0D ? s : 1.0D;
    }

    /**
     * The size actually used: the nearest one that maps each font pixel to a whole number of screen pixels,
     * and never smaller than two screen pixels per font pixel once the GUI scale allows it (one is too small
     * to read comfortably).
     */
    public static float px(float size) {
        double gs = guiScale();
        long min = gs >= 2.0D ? 2L : 1L;
        return (float) (Math.max(min, Math.round(size * gs)) / gs);
    }

    /** Snap a GUI coordinate to the screen pixel grid. */
    public static float snap(float v) {
        double gs = guiScale();
        return (float) (Math.round(v * gs) / gs);
    }

    public static int textW(Font font, String s, float size) {
        return Mth.ceil(font.width(s) * px(size));
    }

    /** Height of one line of text at this size, including a little spacing. */
    public static int lineH(float size) {
        return Mth.ceil(9.0F * px(size));
    }

    /** Sharp text with a shadow. {@code argb} carries the alpha. */
    public static void say(GuiGraphics g, Font font, String s, float x, float y, float size, int argb) {
        if ((argb >>> 24) < 6 || s.isEmpty()) return;
        float k = px(size);
        g.pose().pushPose();
        g.pose().translate(snap(x), snap(y), 0.0F);
        g.pose().scale(k, k, 1.0F);
        g.drawString(font, s, 0, 0, argb, true);
        g.pose().popPose();
    }

    public static void sayCentered(GuiGraphics g, Font font, String s, float cx, float y, float size, int argb) {
        say(g, font, s, cx - textW(font, s, size) / 2.0F, y, size, argb);
    }

    public static void sayRight(GuiGraphics g, Font font, String s, float right, float y, float size, int argb) {
        say(g, font, s, right - textW(font, s, size), y, size, argb);
    }

    /** Shorten text with an ellipsis so it fits in {@code maxW}. */
    public static String fit(Font font, String s, float size, int maxW) {
        if (textW(font, s, size) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && textW(font, t + "…", size) > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    /** A HUD card: a dark glass panel (dark enough to read text over bright sky) with a neon frame. */
    public static void card(GuiGraphics g, int x0, int y0, int x1, int y1, int accentRgb, float alpha) {
        g.fill(x0, y0, x1, y1, argb(0.62F * alpha, 0x03121B));
        frame(g, x0, y0, x1, y1, 3, accentRgb, 0.85F * alpha, 1.0F);
    }

    /** A card's title: small accent-coloured caps and a thin rule under it. Returns the y where content starts. */
    public static int cardTitle(GuiGraphics g, Font font, String title, int x0, int y0, int x1, int accentRgb, float alpha) {
        say(g, font, title, x0 + 4, y0 + 3, SMALL, argb(alpha, accentRgb));
        int ry = y0 + 3 + lineH(SMALL);
        hline(g, x0 + 3, x1 - 3, ry, argb(0.35F * alpha, accentRgb));
        return ry + 3;
    }

    /** A thin bar: {@code f} 0..1 filled. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int fillArgb, int backArgb) {
        g.fill(x, y, x + w, y + h, backArgb);
        g.fill(x, y, x + Math.round(w * Mth.clamp(f, 0.0F, 1.0F)), y + h, fillArgb);
    }
}
