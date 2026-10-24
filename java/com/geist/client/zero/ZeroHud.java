package com.geist.client.zero;

import com.geist.GeistMod;
import com.geist.client.Holo;
import com.geist.item.ZeroLightItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Zero Light readout, up and left of the crosshair (clear of the visor's trajectory readout below it): mode,
 * energy cell (with the pulse cost marked), what the mode will do right now; a draw gauge right of the crosshair
 * while drawing; and a hit marker when an arrow lands (RICOCHET xN after bounces, PULSE for a shockwave).
 */
public final class ZeroHud {
    public static final IGuiOverlay OVERLAY = (gui, g, pt, w, h) -> render(g, pt, w, h);
    private static final int CYAN = 0x4DE1FF, WHITE = 0xE8F6FF, VIOLET = 0xC77DFF, AMBER = 0xFFB02E, DIM = 0x24343C;

    private ZeroHud() {}

    @Mod.EventBusSubscriber(modid = GeistMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("zero_light_hud", OVERLAY);
        }
    }

    private static int modeColor(int mode) {
        return mode == ZeroLightItem.SHOCKWAVE ? WHITE : mode == ZeroLightItem.RICOCHET ? VIOLET : CYAN;
    }

    private static void render(GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui) return;
        float cx = sw / 2.0F, cy = sh / 2.0F;
        long now = mc.level.getGameTime();
        float t = now + pt;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        hitMarker(g, mc.font, cx, cy, t);
        ItemStack bow = ZeroClient.held(p);
        if (bow == null) {
            RenderSystem.disableBlend();
            return;
        }
        Font font = mc.font;
        int mode = ZeroLightItem.mode(bow), energy = ZeroLightItem.energy(bow);
        int col = modeColor(mode);
        int lh = Holo.lineH(Holo.SMALL);
        float right = cx - 16, y = cy - 6 - (3 * lh + 6);
        Holo.sayRight(g, font, "ZERO LIGHT // " + ZeroLightItem.MODE_NAMES[mode], right, y, Holo.SMALL, Holo.argb(0.95F, col));
        y += lh;

        // energy cell: 10 cells, the pulse cost marked
        int cells = 10, cw = 5, gap = 1, w = cells * (cw + gap) - gap;
        int x0 = Math.round(right) - w, yy = Math.round(y) + 1;
        int lit = energy * cells / ZeroLightItem.ENERGY_MAX;
        for (int i = 0; i < cells; i++) {
            int c = i < lit ? (energy >= ZeroLightItem.SHOCK_COST ? CYAN : AMBER) : DIM;
            g.fill(x0 + i * (cw + gap), yy, x0 + i * (cw + gap) + cw, yy + 3, Holo.argb(i < lit ? 0.95F : 0.6F, c));
        }
        int mark = x0 + ZeroLightItem.SHOCK_COST * cells / ZeroLightItem.ENERGY_MAX * (cw + gap) - 1;
        g.fill(mark, yy - 1, mark + 1, yy + 4, Holo.argb(0.8F, WHITE));
        y += 6;

        // what the mode will do right now
        String status;
        int sc;
        if (mode == ZeroLightItem.SHOCKWAVE) {
            long wait = ZeroLightItem.shockReady(bow) - now;
            if (energy < ZeroLightItem.SHOCK_COST) { status = "LOW ENERGY // STANDARD SHOT"; sc = AMBER; }
            else if (wait > 0) { status = String.format("PULSE RECHARGING %.1fS", wait / 20.0F); sc = AMBER; }
            else { status = "PULSE ARMED // FULL DRAW"; sc = WHITE; }
        } else if (mode == ZeroLightItem.RICOCHET) {
            status = ZeroLightItem.MAX_BOUNCES + " BOUNCES // 75% DMG";
            sc = VIOLET;
        } else {
            status = "PRECISION";
            sc = CYAN;
        }
        Holo.sayRight(g, font, status, right, y, Holo.SMALL, Holo.argb(0.9F, sc));
        y += lh;
        Holo.sayRight(g, font, "[" + ZeroClient.MODE_KEY.getTranslatedKeyMessage().getString() + "] MODE", right, y, Holo.SMALL, Holo.argb(0.4F, 0xC8D8E0));

        // the draw: a short upright gauge right of the crosshair, filling upward, flashing at full draw
        if (ZeroClient.drawing(p)) {
            float k = ZeroLightItem.power(p.getTicksUsingItem() + pt);
            int gx = Math.round(cx) + 9, gy0 = Math.round(cy) - 8, gh = 16;
            boolean full = k >= 1.0F;
            int bc = full ? (Mth.sin(t * 1.4F) > 0.0F ? WHITE : col) : col;
            g.fill(gx, gy0, gx + 2, gy0 + gh, Holo.argb(0.45F, 0x041820));
            int filled = Math.round(gh * k);
            g.fill(gx, gy0 + gh - filled, gx + 2, gy0 + gh, Holo.argb(0.95F, bc));
        }
        RenderSystem.disableBlend();
    }

    /** An X round the crosshair when an arrow lands; RICOCHET xN or PULSE under it. */
    private static void hitMarker(GuiGraphics g, Font font, float cx, float cy, float t) {
        float s = t - ZeroClient.hitAt();
        if (s < 0.0F || s >= 14.0F) return;
        float a = Mth.clamp((14.0F - s) / 6.0F, 0.0F, 1.0F);
        int bounces = ZeroClient.hitBounces(), kind = ZeroClient.hitKind();
        int c = Holo.argb(a, bounces > 0 ? VIOLET : kind == ZeroLightItem.SHOCKWAVE ? WHITE : CYAN);
        int x = Math.round(cx), y = Math.round(cy);
        int in = 4 + Math.round(s * 0.3F), out = in + 4;
        for (int i = 0; i < 4; i++) {                                            // four short diagonal strokes
            int sx = (i & 1) == 0 ? 1 : -1, sy = (i & 2) == 0 ? 1 : -1;
            Holo.line(g, x + sx * in, y + sy * in, x + sx * out, y + sy * out, c);
        }
        String label = bounces > 0 ? "RICOCHET x" + bounces : kind == ZeroLightItem.SHOCKWAVE ? "PULSE" : "";
        if (!label.isEmpty()) Holo.say(g, font, label, cx + 14, cy - 4, Holo.SMALL, c);
    }
}
