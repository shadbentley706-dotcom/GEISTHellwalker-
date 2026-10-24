package com.geist.client.gauntlet;

import com.geist.GeistMod;
import com.geist.client.Holo;
import com.geist.item.GauntletItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The gauntlet's HUD: a small status card (barrage state with a bar, throw readiness, hunger warning, hit
 * count) and, around the crosshair, the wind-up and the remaining barrage time while they run.
 */
public final class GauntletHud {
    public static final IGuiOverlay OVERLAY = (gui, g, pt, w, h) -> render(g, pt, w, h);
    private static final int W = 128;

    private GauntletHud() {}

    @Mod.EventBusSubscriber(modid = GeistMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("gauntlet_hud", OVERLAY);
        }
    }

    private static int cardHeight() {
        return Holo.lineH(Holo.SMALL) * 6 + 15;
    }

    /**
     * Top of the status card (bottom left) while the gauntlet is worn, else the screen height. With the exo-frame on
     * too, the card sits on top of the exo-frame's panel instead of over it.
     */
    public static int panelTop(Minecraft mc, int sw, int sh) {
        if (mc.player == null || GauntletItem.worn(mc.player).isEmpty()) return sh;
        int bottom = sw / 2 - 95 < 8 + W + 4 ? sh - 66 : sh - 8;
        int exoTop = com.geist.client.exo.ExoHud.panelTop(mc, sw, sh);
        if (exoTop < sh) bottom = Math.min(bottom, exoTop - 4);
        return bottom - cardHeight();
    }

    private static void render(GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui || GauntletItem.worn(p).isEmpty()) return;
        Font font = mc.font;
        long now = mc.level.getGameTime();
        float t = now + pt;
        GauntletClient.Anim a = GauntletClient.local();
        int state = a == null ? GauntletClient.IDLE : a.state;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // ---- what the barrage is doing
        String status;
        int col;
        float frac;
        boolean food = p.getAbilities().instabuild || p.getFoodData().getFoodLevel() >= GauntletItem.START_FOOD;
        switch (state) {
            case GauntletClient.WINDUP -> {
                status = "SPOOLING";
                col = Holo.WHITE;
                frac = Mth.clamp((t - a.start) / GauntletItem.WINDUP_TICKS, 0.0F, 1.0F);
            }
            case GauntletClient.BARRAGE -> {
                status = "FIRING";
                col = Holo.NEON;
                frac = 1.0F - Mth.clamp((t - a.start) / GauntletItem.MAX_BARRAGE_TICKS, 0.0F, 1.0F);
            }
            case GauntletClient.COOLDOWN -> {
                status = "VENTING";
                col = Holo.AMBER;
                frac = Mth.clamp((t - a.start) / Math.max(a.cooldownLen, 1), 0.0F, 1.0F);
            }
            default -> {
                long left = GauntletClient.barrageReadyAt() - now;
                if (GauntletClient.carrying()) {
                    status = "HANDS FULL";
                    col = 0x708090;
                    frac = 0.0F;
                } else if (left > 0) {
                    status = "RECHARGING";
                    col = Holo.AMBER;
                    frac = 1.0F - Mth.clamp(left / (float) Math.max(a != null ? a.cooldownLen : 60, 1), 0.0F, 1.0F);
                } else if (!food) {
                    status = "NEED FOOD";
                    col = Holo.RED;
                    frac = p.getFoodData().getFoodLevel() / (float) GauntletItem.START_FOOD;
                } else if (!p.getMainHandItem().isEmpty()) {
                    status = "HAND FULL";
                    col = 0x708090;
                    frac = 1.0F;
                } else {
                    status = "READY";
                    col = Holo.GREEN;
                    frac = 1.0F;
                }
            }
        }

        // ---- the card
        int lh = Holo.lineH(Holo.SMALL);
        int h = cardHeight();
        int x = 8, y = panelTop(mc, sw, sh);
        Holo.card(g, x, y, x + W, y + h, col, 1.0F);
        int ry = Holo.cardTitle(g, font, "GAUNTLET MK-I", x, y, x + W, col, 1.0F);
        Holo.say(g, font, "BARRAGE", x + 5, ry, Holo.SMALL, Holo.argb(0.85F, Holo.WHITE));
        Holo.sayRight(g, font, status, x + W - 5, ry, Holo.SMALL, Holo.argb(1.0F, col));
        ry += lh;
        Holo.bar(g, x + 5, ry, W - 10, 3, frac, Holo.argb(0.95F, col), Holo.argb(0.5F, 0x1A3442));
        ry += 6;
        long throwLeft = GauntletClient.throwReadyAt() - now;
        boolean carrying = a != null && a.carryId >= 0;
        float power = GauntletAnimator.chargeK(a, t);
        boolean throwFood = p.getAbilities().instabuild || p.getFoodData().getFoodLevel() >= GauntletItem.THROW_MIN_FOOD;
        String ts;
        int tc;
        if (carrying && a.chargeStart >= 0L) {
            ts = "WIND-UP " + Math.round(power * 100) + "%";
            tc = power >= 1.0F ? Holo.GREEN : Holo.WHITE;
        } else if (carrying) {
            ts = String.format("HOLDING %.0fS", Math.max(0.0F, (GauntletItem.MAX_CARRY_TICKS - (t - a.carryStart)) / 20.0F));
            tc = Holo.NEON;
        } else if (throwLeft > 0) {
            ts = String.format("%.1fS", throwLeft / 20.0F);
            tc = Holo.AMBER;
        } else {
            ts = throwFood ? "READY" : "NEED FOOD";
            tc = throwFood ? Holo.GREEN : Holo.RED;
        }
        Holo.say(g, font, "GRAB", x + 5, ry, Holo.SMALL, Holo.argb(0.85F, Holo.WHITE));
        Holo.sayRight(g, font, ts, x + W - 5, ry, Holo.SMALL, Holo.argb(1.0F, tc));
        ry += lh;
        // the guard: state and heat
        float heat = GauntletAnimator.guardHeat(a, t) / GauntletItem.GUARD_HEAT_MAX;
        long guardLeft = GauntletClient.guardReadyAt() - now;
        boolean guardUp = a != null && a.guardUp;
        float sinceBlock = a == null ? 999.0F : t - a.lastBlock;
        String gs;
        int gc;
        if (guardUp && a.parried && sinceBlock < 20.0F) {
            gs = "PARRY!";
            gc = Holo.WHITE;
        } else if (guardUp) {
            gs = heat > 0.7F ? "OVERHEATING" : "UP";
            gc = heat > 0.7F ? Holo.RED : Holo.NEON;
        } else if (guardLeft > 0) {
            gs = String.format("BROKEN %.1fS", guardLeft / 20.0F);
            gc = Holo.RED;
        } else if (heat > 0.05F) {
            gs = "COOLING";
            gc = Holo.AMBER;
        } else {
            gs = "READY";
            gc = Holo.GREEN;
        }
        Holo.say(g, font, "GUARD", x + 5, ry, Holo.SMALL, Holo.argb(0.85F, Holo.WHITE));
        Holo.sayRight(g, font, gs, x + W - 5, ry, Holo.SMALL, Holo.argb(1.0F, gc));
        ry += lh;
        int heatCol = heat > 0.7F ? Holo.RED : heat > 0.4F ? Holo.AMBER : Holo.NEON;
        Holo.bar(g, x + 5, ry, W - 10, 2, heat, Holo.argb(0.95F, heatCol), Holo.argb(0.5F, 0x1A3442));
        ry += 4;
        int hits = a == null ? 0 : a.hits, punches = a == null ? 0 : a.punches;
        Holo.say(g, font, "HITS", x + 5, ry, Holo.SMALL, Holo.argb(0.85F, Holo.WHITE));
        Holo.sayRight(g, font, hits + " / " + punches, x + W - 5, ry, Holo.SMALL, Holo.argb(0.95F, Holo.WHITE));
        ry += lh;
        String hint = carrying ? "HOLD USE: THROW  /  ATTACK: SLAM"
                : guardUp ? "BLOCKING FRONT  /  RELEASE TO LOWER"
                : state == GauntletClient.IDLE
                ? "[" + GauntletClient.BARRAGE_KEY.getTranslatedKeyMessage().getString() + "] BARRAGE / USE: GUARD / SNEAK+USE: GRAB"
                : "RELEASE TO STOP";
        Holo.say(g, font, Holo.fit(font, hint, Holo.SMALL, W - 10), x + 5, ry, Holo.SMALL, Holo.argb(0.55F, Holo.WHITE));

        // ---- around the crosshair
        float cx = sw / 2.0F, cy = sh / 2.0F;
        if (state == GauntletClient.WINDUP || state == GauntletClient.BARRAGE) {
            int bw = 44;
            int bx = Math.round(cx - bw / 2.0F), by = Math.round(cy + 14);
            Holo.bar(g, bx, by, bw, 2, frac, Holo.argb(0.95F, col), Holo.argb(0.45F, 0x041820));
            Holo.sayCentered(g, font, state == GauntletClient.WINDUP ? "SPOOLING" : "BARRAGE", cx, by + 4, Holo.SMALL, Holo.argb(0.95F, col));
        } else if (carrying) {                                        // throw power: fills over a second, white-hot when full
            int bw = 54;
            int bx = Math.round(cx - bw / 2.0F), by = Math.round(cy + 14);
            boolean full = power >= 1.0F;
            int pc = full ? (Mth.sin(t * 1.2F) > 0.0F ? Holo.WHITE : Holo.GREEN) : Holo.NEON;
            Holo.bar(g, bx, by, bw, 3, power, Holo.argb(0.95F, pc), Holo.argb(0.45F, 0x041820));
            for (int i = 1; i < 4; i++) Holo.vline(g, bx + bw * i / 4, by, by + 3, Holo.argb(0.5F, 0x041820));
            String label = a.chargeStart >= 0L ? (full ? "FULL POWER // RELEASE" : "WINDING UP") : "HOLD USE TO THROW";
            Holo.sayCentered(g, font, label, cx, by + 5, Holo.SMALL, Holo.argb(0.95F, pc));
        } else if (guardUp || (a != null && t - a.guardBreakAt < 30.0F)) {
            guardReticle(g, font, a, cx, cy, t, heat, heatCol, sinceBlock);
        } else if (state == GauntletClient.COOLDOWN) {
            float left = Math.max(0.0F, (a.cooldownLen - (t - a.start)) / 20.0F);
            Holo.sayCentered(g, font, String.format("VENTING %.1fS", left), cx, cy + 16, Holo.SMALL, Holo.argb(0.95F, Holo.AMBER));
        }
        RenderSystem.disableBlend();
    }

    /** Two brackets either side of the crosshair (the raised forearms), coloured by heat; the block / parry / break callouts. */
    private static void guardReticle(GuiGraphics g, Font font, GauntletClient.Anim a, float cx, float cy, float t, float heat, int heatCol, float sinceBlock) {
        float broke = t - a.guardBreakAt;
        if (broke >= 0.0F && broke < 30.0F) {
            float fade = Mth.clamp((30.0F - broke) / 10.0F, 0.0F, 1.0F);
            float jitter = broke < 8.0F ? (Mth.frac(Mth.sin(t * 13.0F) * 91.3F) - 0.5F) * 4.0F : 0.0F;
            Holo.sayCentered(g, font, "GUARD BROKEN", cx + jitter, cy + 14, Holo.MEDIUM, Holo.argb(fade, Holo.RED));
            return;
        }
        float k = GauntletAnimator.guardK(a, t);
        float r = GauntletAnimator.recoil(a, t);
        int gap = Math.round(9 + 3 * (1.0F - k) - 2 * r), arm = 7;
        int c = Holo.argb(0.9F * k, r > 0.2F ? Holo.WHITE : heatCol);
        int x0 = Math.round(cx), y0 = Math.round(cy);
        for (int sgn = -1; sgn <= 1; sgn += 2) {                        // [  +  ]
            int bx = x0 + sgn * gap;
            Holo.vline(g, bx, y0 - arm, y0 + arm, c);
            Holo.hline(g, Math.min(bx, bx - sgn * 3), Math.max(bx, bx - sgn * 3), y0 - arm, c);
            Holo.hline(g, Math.min(bx, bx - sgn * 3), Math.max(bx, bx - sgn * 3), y0 + arm, c);
        }
        int bw = 2 * gap - 4;                                          // heat under the brackets
        Holo.bar(g, x0 - bw / 2, y0 + arm + 3, bw, 2, heat, Holo.argb(0.9F * k, heatCol), Holo.argb(0.4F * k, 0x041820));
        if (sinceBlock >= 0.0F && sinceBlock < 16.0F) {
            float fade = Mth.clamp((16.0F - sinceBlock) / 6.0F, 0.0F, 1.0F);
            String label = a.parried ? "PARRY!" : a.blockKind == 3 ? "BLAST BLOCKED" : a.blockKind == 2 ? "BOOM BLOCKED"
                    : a.blockKind == 1 ? "DEFLECTED" : "BLOCKED";
            int lc = a.parried ? Holo.WHITE : a.blockKind >= 2 ? Holo.AMBER : Holo.NEON;
            Holo.sayCentered(g, font, label, cx, y0 + arm + 8, a.parried ? Holo.MEDIUM : Holo.SMALL, Holo.argb(fade, lc));
        }
    }
}
