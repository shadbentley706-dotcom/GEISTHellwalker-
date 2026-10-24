package com.geist.client.exo;

import com.geist.GeistMod;
import com.geist.client.Holo;
import com.geist.event.ExoEvents;
import com.geist.item.ExoFrameItem;
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
 * The exo-frame's HUD: a status panel (power, fuel, hydraulics, status, a little leg schematic), the
 * boot-up sequence, LEAP READY under the crosshair, speed lines on launch, the impact readout on landing,
 * static when shorted and a red pulse when the suit is dead weight.
 */
public final class ExoHud {
    public static final IGuiOverlay OVERLAY = (gui, g, pt, w, h) -> render(g, pt, w, h);
    private static final int REDSTONE = 0xFF3B3B, COPPER = 0xE8925A;

    private ExoHud() {}

    /** Top of the status panel (bottom left) while the exo-frame is worn, else the screen height. */
    public static int panelTop(Minecraft mc, int sw, int sh) {
        if (mc.player == null || ExoFrameItem.worn(mc.player).isEmpty()) return sh;
        int w = 122, h = 60, x = 8;
        return sw / 2 - 95 < x + w + 4 ? sh - h - 66 : sh - h - 8;
    }

    @Mod.EventBusSubscriber(modid = GeistMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("exo_hud", OVERLAY);
        }
    }

    private static void render(GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui) return;
        ItemStack exo = ExoFrameItem.worn(p);
        if (exo.isEmpty()) return;
        Font font = mc.font;
        long now = mc.level.getGameTime();
        float t = now + pt;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        boolean shorted = ExoFrameItem.shorted(exo), dead = ExoFrameItem.power(exo) <= 0;

        if (dead && !shorted) {                                       // dead weight: a slow red pulse at the edges
            float a = 0.10F + 0.08F * Mth.sin(t * 0.15F);
            for (int i = 0; i < 12; i++) {
                int c = Holo.argb(a * (1.0F - i / 12.0F), 0xB01010);
                g.fill(i, 0, i + 1, sh, c);
                g.fill(sw - 1 - i, 0, sw - i, sh, c);
            }
        }
        if (shorted) shortStatic(g, font, sw, sh, t);
        speedLines(g, sw, sh, t);
        panel(g, font, p, exo, sw, sh, now, t);
        crosshairInfo(g, font, p, exo, sw, sh, now, t);
        boot(g, font, exo, sw, sh, now, t);
        RenderSystem.disableBlend();
    }

    // ---------------------------------------------------------------- the status panel

    private static void panel(GuiGraphics g, Font font, LocalPlayer p, ItemStack exo, int sw, int sh, long now, float t) {
        int w = 122, h = 60;
        int x = 8, y = sh - h - 8;
        if (sw / 2 - 95 < x + w + 4) y = sh - h - 66;                  // narrow screens: clear the hotbar
        boolean shorted = ExoFrameItem.shorted(exo), rebooting = ExoFrameItem.rebooting(exo, now);
        int power = ExoFrameItem.power(exo);
        float frac = power / (float) ExoFrameItem.MAX_POWER;
        boolean dead = power <= 0, low = frac < ExoFrameItem.LOW_POWER;
        int accent = shorted ? 0x9CC8FF : dead ? 0xFF3B3B : low ? Holo.AMBER : Holo.NEON;
        float flick = shorted ? (Mth.frac(Mth.sin(t * 9.1F) * 437.5F) > 0.25F ? 1.0F : 0.35F) : 1.0F;

        g.fill(x, y, x + w, y + h, Holo.argb(0.35F, 0x041820));
        Holo.frame(g, x, y, x + w, y + h, 4, accent, 0.8F * flick, 1.0F);
        Holo.glowText(g, font, "EXO-FRAME MK-I", x + 5, y + 4, 0.6F, accent, flick);
        Holo.text(g, font, "PROTOTYPE", x + 70, y + 4, 0.5F, Holo.argb(0.9F, Holo.AMBER));
        Holo.hline(g, x + 4, x + 86, y + 11, Holo.argb(0.4F, accent));

        // power: ten segments
        Holo.text(g, font, "PWR", x + 5, y + 15, 0.55F, Holo.argb(0.85F, accent));
        int segs = 10, lit = Mth.ceil(frac * segs);
        for (int i = 0; i < segs; i++) {
            int sx = x + 22 + i * 5;
            int c = i < lit ? (dead ? 0xFF3B3B : low ? Holo.AMBER : Holo.NEON) : 0x1A3442;
            g.fill(sx, y + 15, sx + 4, y + 19, Holo.argb(i < lit ? flick : 0.6F, c));
        }
        Holo.text(g, font, Math.round(frac * 100) + "%", x + 74, y + 15, 0.55F, Holo.argb(0.95F, Holo.WHITE));

        // fuel carried
        int[] fuel = ExoFrameItem.fuelCarried(p);
        Holo.text(g, font, "FUEL", x + 5, y + 24, 0.55F, Holo.argb(0.85F, accent));
        Holo.text(g, font, "◆ RS " + fuel[0], x + 24, y + 24, 0.55F, Holo.argb(1.0F, REDSTONE));
        Holo.text(g, font, "◆ CU " + fuel[1], x + 52, y + 24, 0.55F, Holo.argb(1.0F, COPPER));

        // hydraulics
        String hyd;
        int hc;
        int phase = ExoClient.phase();
        long sinceLaunch = now - ExoClient.lastLaunch();
        if (!ExoFrameItem.active(exo, now)) { hyd = "OFFLINE"; hc = 0x708090; }
        else if (phase == ExoClient.COMPRESS) { hyd = "PRESSURIZING"; hc = Holo.AMBER; }
        else if (phase == ExoClient.AIRBORNE) { hyd = "EXTENDED"; hc = Holo.WHITE; }
        else if (phase == ExoClient.LANDING) { hyd = "ABSORBING"; hc = Holo.AMBER; }
        else if (sinceLaunch < ExoFrameItem.LEAP_COOLDOWN) { hyd = "RECHARGING"; hc = Holo.AMBER; }
        else if (!p.getAbilities().instabuild && power < ExoFrameItem.LEAP_COST) { hyd = "NO CHARGE"; hc = 0xFF5B4B; }
        else { hyd = "LEAP READY"; hc = Holo.NEON; }
        Holo.text(g, font, "HYD", x + 5, y + 33, 0.55F, Holo.argb(0.85F, accent));
        Holo.glowText(g, font, hyd, x + 22, y + 33, 0.55F, hc, 1.0F);
        if (sinceLaunch < ExoFrameItem.LEAP_COOLDOWN && phase == ExoClient.NONE) {
            float k = sinceLaunch / (float) ExoFrameItem.LEAP_COOLDOWN;
            g.fill(x + 22, y + 39, x + 22 + Math.round(60 * k), y + 40, Holo.argb(0.9F, Holo.AMBER));
        }

        // status
        String st;
        int sc;
        if (shorted) { st = "SHORTED // DRY LAND"; sc = 0x9CC8FF; }
        else if (rebooting) {
            float k = 1.0F - (ExoFrameItem.rebootUntil(exo) - now) / (float) ExoFrameItem.REBOOT_TICKS;
            st = "REBOOTING " + Math.round(Mth.clamp(k, 0.0F, 1.0F) * 100) + "%"; sc = Holo.NEON;
        }
        else if (dead) { st = "DEAD WEIGHT"; sc = 0xFF3B3B; }
        else if (low) { st = "LOW POWER"; sc = Holo.AMBER; }
        else { st = "ONLINE"; sc = Holo.GREEN; }
        float blink = dead || shorted ? (0.55F + 0.45F * Mth.sin(t * 0.5F)) : 1.0F;
        Holo.text(g, font, "SYS", x + 5, y + 44, 0.55F, Holo.argb(0.85F, accent));
        Holo.glowText(g, font, st, x + 22, y + 44, 0.55F, sc, blink);
        Holo.text(g, font, "STR I  MINE x" + ExoEvents.MINING_BOOST, x + 5, y + 52, 0.45F,
                Holo.argb(ExoFrameItem.active(exo, now) ? 0.75F : 0.25F, Holo.WHITE));

        schematic(g, font, x + 94, y + 14, exo, now, accent, flick);
    }

    /** Two little legs: hip, knee and ankle actuators, with the piston level between them. */
    private static void schematic(GuiGraphics g, Font font, int x, int y, ItemStack exo, long now, int accent, float flick) {
        boolean active = ExoFrameItem.active(exo, now);
        int phase = ExoClient.phase();
        float c = phase == ExoClient.COMPRESS || phase == ExoClient.LANDING ? 1.0F : 0.0F;
        for (int leg = 0; leg < 2; leg++) {
            int lx = x + leg * 12;
            Holo.vline(g, lx + 3, y, y + 38, Holo.argb(0.45F, accent));
            for (int j = 0; j < 3; j++) {
                int jy = y + j * 16;
                int jc = active ? accent : 0x506070;
                g.fill(lx + 1, jy, lx + 6, jy + 5, Holo.argb((active ? 0.9F : 0.5F) * flick, jc));
                g.fill(lx + 2, jy + 1, lx + 5, jy + 4, Holo.argb(0.9F, 0x041820));
            }
            int pistonTop = y + 6, pistonLen = Math.round(9 - 5 * c);      // piston between hip and knee
            g.fill(lx + 6, pistonTop, lx + 8, pistonTop + pistonLen, Holo.argb(0.9F, COPPER));
        }
        Holo.text(g, font, "R   L", x - 1, y + 40, 0.45F, Holo.argb(0.6F, accent));
    }

    // ---------------------------------------------------------------- around the crosshair

    private static void crosshairInfo(GuiGraphics g, Font font, LocalPlayer p, ItemStack exo, int sw, int sh, long now, float t) {
        float cx = sw / 2.0F, cy = sh / 2.0F;
        int phase = ExoClient.phase();
        if (phase == ExoClient.COMPRESS) {                            // pressurizing bar
            float k = Mth.clamp((t - ExoClient.phaseStart()) / ExoFrameItem.COMPRESS_TICKS, 0.0F, 1.0F);
            g.fill(Math.round(cx - 20), Math.round(cy + 14), Math.round(cx - 20 + 40 * k), Math.round(cy + 16), Holo.argb(0.95F, Holo.AMBER));
            String s = "PRESSURIZING";
            Holo.glowText(g, font, s, cx - Holo.width(font, s, 0.55F) / 2.0F, cy + 19, 0.55F, Holo.AMBER, 1.0F);
        } else if (phase == ExoClient.NONE && p.isSprinting() && p.onGround() && ExoClient.leapReady(p, exo, now)) {
            float a = 0.6F + 0.4F * Mth.sin(t * 0.4F);                // sprinting and ready: tell them they can leap
            int c = Holo.argb(a, Holo.NEON);
            Holo.line(g, Math.round(cx - 4), Math.round(cy + 17), Math.round(cx), Math.round(cy + 13), c);
            Holo.line(g, Math.round(cx), Math.round(cy + 13), Math.round(cx + 4), Math.round(cy + 17), c);
            String s = "[SPACE] LEAP";
            Holo.text(g, font, s, cx - Holo.width(font, s, 0.5F) / 2.0F, cy + 20, 0.5F, c);
        }
        float since = t - ExoClient.landedAt();                        // impact readout
        if (since >= 0.0F && since < 45.0F) {
            float a = Mth.clamp((45.0F - since) / 12.0F, 0.0F, 1.0F);
            float drop = ExoClient.lastDrop();
            boolean over = drop > ExoEvents.SAFE_FALL;
            String s = over ? String.format("SHOCK DAMPENED -50%% // %.1fM", drop) : String.format("IMPACT ABSORBED // %.1fM", drop);
            int col = over ? Holo.AMBER : Holo.NEON;
            float w = Holo.width(font, s, 0.7F);
            float jitter = since < 6.0F ? (Mth.frac(Mth.sin(t * 13.0F) * 91.3F) - 0.5F) * 4.0F : 0.0F;
            g.fill(Math.round(cx - w / 2 - 4), Math.round(cy + 26), Math.round(cx + w / 2 + 4), Math.round(cy + 37), Holo.argb(0.35F * a, 0x041820));
            Holo.glowText(g, font, s, cx - w / 2.0F + jitter, cy + 28, 0.7F, col, a);
        }
    }

    /** Streaks rushing past the edges of the screen as you launch. */
    private static void speedLines(GuiGraphics g, int sw, int sh, float t) {
        float since = t - ExoClient.lastLaunch();
        if (since < 0.0F || since > 14.0F) return;
        float a = 1.0F - since / 14.0F;
        float cx = sw / 2.0F, cy = sh / 2.0F, r = (float) Math.sqrt(cx * cx + cy * cy);
        java.util.Random rnd = new java.util.Random(ExoClient.lastLaunch() * 7919L);
        for (int i = 0; i < 36; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0D;
            float r1 = r * (0.62F + 0.3F * rnd.nextFloat()) - since * 6.0F, r0 = r1 - r * (0.1F + 0.15F * rnd.nextFloat()) * a;
            Holo.line(g, Math.round(cx + (float) Math.cos(ang) * r0), Math.round(cy + (float) Math.sin(ang) * r0),
                    Math.round(cx + (float) Math.cos(ang) * r1), Math.round(cy + (float) Math.sin(ang) * r1),
                    Holo.argb(0.5F * a, 0xE8F6FF));
        }
    }

    /** Blue-white static crawling in from the edges while the suit is shorted. */
    private static void shortStatic(GuiGraphics g, Font font, int sw, int sh, float t) {
        java.util.Random rnd = new java.util.Random((long) (t * 3.0F));
        for (int i = 0; i < 40; i++) {
            boolean vertical = rnd.nextBoolean();
            int len = 6 + rnd.nextInt(30), c = Holo.argb(0.15F + 0.35F * rnd.nextFloat(), rnd.nextBoolean() ? 0x9CC8FF : 0xFFFFFF);
            if (vertical) {
                int x = rnd.nextBoolean() ? rnd.nextInt(18) : sw - 1 - rnd.nextInt(18), y = rnd.nextInt(Math.max(1, sh - len));
                g.fill(x, y, x + 1, y + len, c);
            } else {
                int y = rnd.nextBoolean() ? rnd.nextInt(14) : sh - 1 - rnd.nextInt(14), x = rnd.nextInt(Math.max(1, sw - len));
                g.fill(x, y, x + len, y + 1, c);
            }
        }
        if (Mth.frac(Mth.sin(t * 5.3F) * 113.7F) > 0.2F) {
            String s = "⚠ SYSTEM SHORT // REACH DRY LAND ⚠";
            float w = Holo.width(font, s, 0.8F);
            Holo.glowText(g, font, s, sw / 2.0F - w / 2.0F, 32, 0.8F, 0x9CC8FF, 1.0F);
        }
    }

    // ---------------------------------------------------------------- boot-up sequence

    private static void boot(GuiGraphics g, Font font, ItemStack exo, int sw, int sh, long now, float t) {
        float since = t - ExoClient.bootLocal();
        int total = ExoClient.BOOT_TICKS + 20;
        if (since < 0.0F || since > total) return;
        int power = Math.round(100.0F * ExoFrameItem.power(exo) / ExoFrameItem.MAX_POWER);
        boolean ok = power > 0 && !ExoFrameItem.shorted(exo);
        String[] lines = {
                "G.E.I.S.T. EXO-FRAME MK-I // FW 11.0",
                "HYDRAULIC PRESSURE ........ OK",
                "SERVO CALIBRATION ......... OK",
                "GAIT SYNC ................. LOCKED",
                power > 0 ? "FUEL CELL ................. " + power + "%" : "FUEL CELL ................. EMPTY",
                ok ? "ALL SYSTEMS ONLINE" : "STANDBY // DEAD WEIGHT"};
        int[] at = {0, 8, 18, 28, 38, 48};
        float fade = Mth.clamp((total - since) / 15.0F, 0.0F, 1.0F);
        float x = sw / 2.0F - 95, y = sh / 2.0F + 30;
        g.fill(Math.round(x - 6), Math.round(y - 5), Math.round(x + 196), Math.round(y + 58), Holo.argb(0.4F * fade, 0x041820));
        Holo.frame(g, Math.round(x - 6), Math.round(y - 5), Math.round(x + 196), Math.round(y + 58), 3, Holo.NEON, 0.7F * fade,
                Mth.clamp(since / 8.0F, 0.0F, 1.0F));
        for (int i = 0; i < lines.length; i++) {
            float s = since - at[i];
            if (s < 0.0F) break;
            String full = lines[i];
            int n = Math.min(full.length(), (int) (s * 3.5F));
            boolean typing = n < full.length();
            String shown = full.substring(0, n) + (typing && (int) (t / 2) % 2 == 0 ? "_" : "");
            int col = i == 0 ? Holo.NEON : i == lines.length - 1 ? (ok ? Holo.GREEN : 0xFF3B3B) : Holo.WHITE;
            Holo.glowText(g, font, shown, x, y + i * 9, i == 0 || i == lines.length - 1 ? 0.65F : 0.6F, col, fade);
        }
        float bar = Mth.clamp(since / ExoClient.BOOT_TICKS, 0.0F, 1.0F);
        g.fill(Math.round(x), Math.round(y + 55), Math.round(x + 190 * bar), Math.round(y + 56), Holo.argb(0.9F * fade, Holo.NEON));
    }
}
