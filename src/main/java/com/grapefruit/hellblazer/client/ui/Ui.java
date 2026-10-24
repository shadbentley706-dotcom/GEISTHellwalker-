package com.grapefruit.hellblazer.client.ui;


import com.grapefruit.hellblazer.registry.HbSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small immediate-mode toolkit for the casebook's pages (from GEIST): every frame the pages lay out their clickable areas as
 * they draw them; clicks are matched against the last frame's areas (the topmost wins), hover amounts ease in and
 * out per area, and buttons sound.
 */
public final class Ui {
    public static final float S = Hud.SMALL, M = Hud.MEDIUM, L = Hud.LARGE;

    public record Area(int x0, int y0, int x1, int y1, String id, Runnable action) {}

    public final Font font;
    final List<Area> areas = new ArrayList<>();
    private final Map<String, Float> glow = new HashMap<>();
    public int mx, my;
    /** Screen time (ticks, with the partial tick). */
    public float t;
    /** Set by a page while text is still decrypting (the terminal ticks a typewriter for it). */
    public boolean typing;
    private String over, lastOver;

    public Ui(Font font) {
        this.font = font;
    }

    public void begin(int mouseX, int mouseY, float time) {
        mx = mouseX;
        my = mouseY;
        t = time;
        areas.clear();
        typing = false;
    }

    /** A clickable area; returns whether the mouse is over it. */
    public boolean area(int x0, int y0, int x1, int y1, String id, Runnable action) {
        areas.add(new Area(x0, y0, x1, y1, id, action));
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    /** 0..1: how lit this area is (eases toward 1 while the mouse is over it). */
    public float lit(String id) {
        Float f = glow.get(id);
        return f == null ? 0.0F : f;
    }

    /** After drawing: work out what the mouse is over, ease the glows, tick a sound when it moves onto something new. */
    public void end(float dt) {
        over = null;
        for (int i = areas.size() - 1; i >= 0; i--) {
            Area a = areas.get(i);
            if (a.action != null && mx >= a.x0 && mx < a.x1 && my >= a.y0 && my < a.y1) {
                over = a.id;
                break;
            }
        }
        if (over != null && !over.equals(lastOver)) play(HbSounds.CASE_HOVER.get(), 1.0F + Math.floorMod(over.hashCode(), 7) * 0.02F, 0.22F);
        lastOver = over;
        float k = Math.min(1.0F, dt * 0.45F);
        for (Area a : areas) glow.putIfAbsent(a.id, 0.0F);
        glow.replaceAll((id, v) -> v + ((id.equals(over) ? 1.0F : 0.0F) - v) * k);
    }

    /** Run the topmost area under (x, y); false if there is none. */
    public boolean click(double x, double y) {
        for (int i = areas.size() - 1; i >= 0; i--) {
            Area a = areas.get(i);
            if (a.action != null && x >= a.x0 && x < a.x1 && y >= a.y0 && y < a.y1) {
                a.action.run();
                return true;
            }
        }
        return false;
    }

    public void play(SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    /** A button with cut corners: it lights up under the mouse; a disabled one buzzes when pressed. */
    public void button(GuiGraphics g, int x0, int y0, int x1, int y1, String label, String id, boolean enabled, int accent, float alpha, Runnable action) {
        area(x0, y0, x1, y1, id, () -> {
            if (enabled) {
                play(HbSounds.CASE_CLICK.get(), 1.0F, 0.6F);
                action.run();
            } else {
                play(HbSounds.CASE_DENY.get(), 1.2F, 0.4F);
            }
        });
        float h = lit(id);
        int col = enabled ? accent : Grim.DIM;
        g.fill(x0, y0, x1, y1, Grim.a(alpha * (0.16F + 0.3F * h), col));
        Hud.frame(g, x0, y0, x1 - 1, y1 - 1, 2, col, alpha * (0.6F + 0.4F * h), 1.0F);
        if (h > 0.05F) g.fill(x0 + 2, y1 - 2, x0 + 2 + Math.round((x1 - x0 - 4) * h), y1 - 1, Grim.a(alpha, col));
        int lh = Hud.lineH(S);
        String shown = Hud.fit(font, label, S, x1 - x0 - 6);
        Hud.sayCentered(g, font, shown, (x0 + x1) / 2.0F, (y0 + y1) / 2.0F - lh / 2.0F + 1, S, Grim.a(alpha, enabled ? Grim.WHITE : Grim.GREY));
    }

    public int lh() {
        return Hud.lineH(S);
    }

    public void say(GuiGraphics g, String s, float x, float y, int rgb, float alpha) {
        Hud.say(g, font, s, x, y, S, Grim.a(alpha, rgb));
    }

    public void sayRight(GuiGraphics g, String s, float right, float y, int rgb, float alpha) {
        Hud.sayRight(g, font, s, right, y, S, Grim.a(alpha, rgb));
    }

    public void sayCentered(GuiGraphics g, String s, float cx, float y, int rgb, float alpha) {
        Hud.sayCentered(g, font, s, cx, y, S, Grim.a(alpha, rgb));
    }

    public int width(String s) {
        return Hud.textW(font, s, S);
    }
}
