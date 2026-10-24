package com.geist.client;

import com.geist.GeistMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Visor zoom: hold C (rebindable) while wearing the visor. Scroll while zoomed to change the power,
 * 2x to 16x (remembered). The view eases in, the mouse slows down to match (like a spyglass), your hand
 * is hidden, and the lens shows the power and a rangefinder: how far away and what is under the crosshair.
 * The HUD cards fade out while zoomed so the view stays clear.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class Zoom {
    public static final IGuiOverlay OVERLAY = (gui, g, pt, w, h) -> render(g, pt, w, h);
    private static final float[] LEVELS = {2.0F, 3.0F, 4.0F, 6.0F, 8.0F, 12.0F, 16.0F};
    private static final int HINT_TICKS = 60;
    private static int level = 2;                                   // 4x until you scroll
    private static boolean zooming;
    private static float amount, amountO, mag = 1.0F, magO = 1.0F;
    private static long hintFrom = -1000L;
    /** The mouse sensitivity from before zooming (restored afterwards), and the lowered one we set. */
    private static double savedSensitivity = -1.0D, appliedSensitivity = -1.0D;
    // rangefinder, refreshed every tick while zoomed
    private static double range = -1.0D;
    private static String rangeName = "";
    @Nullable private static LivingEntity rangeLiving;

    private Zoom() {}

    public static boolean zooming() {
        return zooming;
    }

    /** 0 = not zoomed, 1 = fully zoomed (eased). */
    public static float amount(float pt) {
        return Mth.lerp(pt, amountO, amount);
    }

    public static float magnification(float pt) {
        return Mth.lerp(pt, magO, mag);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        amountO = amount;
        magO = mag;
        if (mc.player == null || mc.level == null) {
            stop(mc, false);
            amount = amountO = 0.0F;
            mag = magO = 1.0F;
            return;
        }
        boolean want = GeistState.ZOOM.isDown() && mc.screen == null && GeistState.wearing(mc)
                && mc.options.getCameraType().isFirstPerson() && !mc.player.isScoping();
        if (want && !zooming) start(mc);
        else if (!want && zooming) stop(mc, true);
        float target = zooming ? LEVELS[level] : 1.0F;
        mag += (target - mag) * 0.5F;
        if (Math.abs(mag - target) < 0.01F) mag = target;
        amount += ((zooming ? 1.0F : 0.0F) - amount) * 0.5F;
        if (amount < 0.01F) amount = 0.0F;
        if (amount > 0.99F) amount = 1.0F;
        if (zooming) {
            applySensitivity(mc);
            rangefind(mc);
        }
    }

    private static void start(Minecraft mc) {
        zooming = true;
        hintFrom = mc.level.getGameTime();
        mc.player.playSound(SoundEvents.SPYGLASS_USE, 0.6F, 1.2F);
    }

    private static void stop(Minecraft mc, boolean sound) {
        if (zooming && sound && mc.player != null) mc.player.playSound(SoundEvents.SPYGLASS_STOP_USING, 0.6F, 1.2F);
        zooming = false;
        restoreSensitivity(mc);
        range = -1.0D;
        rangeLiving = null;
    }

    // ---------------------------------------------------------------- slower mouse while zoomed

    private static void applySensitivity(Minecraft mc) {
        OptionInstance<Double> opt = mc.options.sensitivity();
        if (savedSensitivity < 0.0D) savedSensitivity = opt.get();
        double base = savedSensitivity * 0.6D + 0.2D;                       // Minecraft's turn speed grows with the cube of this
        double want = Mth.clamp((base / Math.cbrt(LEVELS[level]) - 0.2D) / 0.6D, 0.0D, 1.0D);
        if (Math.abs(opt.get() - want) > 1.0E-6D) opt.set(want);
        appliedSensitivity = want;
    }

    private static void restoreSensitivity(Minecraft mc) {
        if (savedSensitivity < 0.0D) return;
        OptionInstance<Double> opt = mc.options.sensitivity();
        if (Math.abs(opt.get() - appliedSensitivity) < 1.0E-6D) opt.set(savedSensitivity);   // unless you changed it yourself
        savedSensitivity = -1.0D;
        appliedSensitivity = -1.0D;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop(Minecraft.getInstance(), false);
    }

    // ---------------------------------------------------------------- the zoom itself, scroll, hand

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;                             // the hand keeps its normal view
        float m = magnification((float) event.getPartialTick());
        if (m <= 1.001F) return;
        double half = Math.toRadians(event.getFOV() / 2.0D);
        event.setFOV(Math.toDegrees(2.0D * Math.atan(Math.tan(half) / m)));
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!zooming) return;
        double d = event.getScrollDelta();
        if (d == 0.0D) return;
        event.setCanceled(true);                                            // don't change hotbar slot while zoomed
        int before = level;
        level = Mth.clamp(level + (d > 0.0D ? 1 : -1), 0, LEVELS.length - 1);
        Minecraft mc = Minecraft.getInstance();
        if (level != before && mc.player != null && mc.level != null) {
            mc.player.playSound(SoundEvents.SPYGLASS_USE, 0.3F, 1.3F + level * 0.1F);
            hintFrom = mc.level.getGameTime();
        }
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (amount(event.getPartialTick()) > 0.5F) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- rangefinder

    private static void rangefind(Minecraft mc) {
        LocalPlayer p = mc.player;
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1.0F);
        HitResult hr = p.pick(512.0D, 1.0F, false);
        double blockDist = hr.getType() == HitResult.Type.BLOCK ? hr.getLocation().distanceTo(eye) : -1.0D;
        double far = blockDist > 0.0D ? Math.min(blockDist, 160.0D) : 160.0D;
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, eye.add(look.scale(far)),
                p.getBoundingBox().expandTowards(look.scale(far)).inflate(1.0D), e -> !e.isSpectator() && e.isPickable(), far * far);
        rangeLiving = null;
        if (eh != null) {
            Entity e = eh.getEntity();
            range = eh.getLocation().distanceTo(eye);
            rangeName = e.getDisplayName().getString();
            if (e instanceof LivingEntity le) rangeLiving = le;
        } else if (hr instanceof BlockHitResult bh && blockDist > 0.0D) {
            range = blockDist;
            rangeName = mc.level.getBlockState(bh.getBlockPos()).getBlock().getName().getString();
        } else {
            range = -1.0D;
            rangeName = "";
        }
    }

    // ---------------------------------------------------------------- the lens overlay

    private static void render(GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        float a = amount(pt);
        if (a < 0.02F || mc.player == null || mc.level == null || mc.options.hideGui) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Font font = mc.font;
        float cx = sw / 2.0F, cy = sh / 2.0F;
        int r = Math.round(Math.min(sw, sh) * 0.46F);
        int icx = Math.round(cx), top = Math.round(cy) - r, bot = Math.round(cy) + r;
        int shade = Holo.argb(0.62F * a, 0x01070B);                        // darken everything outside the lens
        if (top > 0) g.fill(0, 0, sw, top, shade);
        if (bot < sh) g.fill(0, bot, sw, sh, shade);
        for (int y = Math.max(0, top); y < Math.min(sh, bot); y++) {
            float dy = y + 0.5F - cy;
            int hw = Math.round(Mth.sqrt(Math.max(0.0F, r * r - dy * dy)));
            g.fill(0, y, icx - hw, y + 1, shade);
            g.fill(icx + hw, y, sw, y + 1, shade);
        }
        Holo.arc(g, cx, cy, r, 0, 360, Holo.argb(0.8F * a, Holo.NEON));
        Holo.arc(g, cx, cy, r - 3, 0, 360, Holo.argb(0.22F * a, Holo.NEON));
        for (int i = 0; i < 72; i++) {                                      // bezel ticks every 5 degrees
            double ang = Math.toRadians(i * 5);
            float in = i % 9 == 0 ? r - 7 : r - 4;
            Holo.line(g, Math.round(cx + (float) Math.sin(ang) * in), Math.round(cy - (float) Math.cos(ang) * in),
                    Math.round(cx + (float) Math.sin(ang) * (r - 1)), Math.round(cy - (float) Math.cos(ang) * (r - 1)), Holo.argb(0.45F * a, Holo.NEON));
        }
        int c = Holo.argb(0.55F * a, Holo.NEON), icy = Math.round(cy);     // fine crosshair with a gap and range marks
        Holo.hline(g, icx - r + 8, icx - 9, icy, c);
        Holo.hline(g, icx + 9, icx + r - 8, icy, c);
        Holo.vline(g, icx, icy - r + 8, icy - 9, c);
        Holo.vline(g, icx, icy + 9, icy + r - 8, c);
        for (int k = 2; k * 6 < r - 12; k++) {
            int len = k % 5 == 0 ? 3 : 1;
            Holo.vline(g, icx - k * 6, icy - len, icy + len, c);
            Holo.vline(g, icx + k * 6, icy - len, icy + len, c);
            Holo.hline(g, icx - len, icx + len, icy + k * 6, c);
        }

        float S = Holo.SMALL, M = Holo.MEDIUM;
        int ry = Math.round(cy + r * 0.52F);
        String power = (LEVELS[level] == Math.round(LEVELS[level]) ? String.valueOf(Math.round(LEVELS[level])) : String.valueOf(LEVELS[level])) + "×";
        String dist = range < 0.0D ? "---" : range < 10.0D ? String.format("%.1f m", range) : Math.round(range) + " m";
        float lx = cx - r * 0.42F, rx = cx + r * 0.42F;
        Holo.sayCentered(g, font, "ZOOM", lx, ry, S, Holo.argb(a, Holo.NEON));
        Holo.sayCentered(g, font, power, lx, ry + Holo.lineH(S), M, Holo.argb(a, 0xFFFFFF));
        for (int i = 0; i < LEVELS.length; i++) {                           // which power, out of seven
            int px = Math.round(lx - LEVELS.length * 2 + i * 4), py = ry + Holo.lineH(S) + Holo.lineH(M) + 2;
            g.fill(px, py, px + 3, py + 2, Holo.argb(a * (i == level ? 1.0F : 0.3F), i == level ? 0xFFFFFF : Holo.NEON));
        }
        Holo.sayCentered(g, font, "RANGE", rx, ry, S, Holo.argb(a, Holo.NEON));
        Holo.sayCentered(g, font, dist, rx, ry + Holo.lineH(S), M, Holo.argb(a, 0xFFFFFF));
        if (!rangeName.isEmpty() && range >= 0.0D) {
            String what = rangeName;
            int col = 0xC8D8E8;
            if (rangeLiving != null && rangeLiving.isAlive()) {
                what += " · ❤ " + EntityScanner.num(rangeLiving.getHealth()) + " / " + EntityScanner.num(rangeLiving.getMaxHealth());
                col = EntityScanner.classify(rangeLiving);
            }
            Holo.sayCentered(g, font, Holo.fit(font, what, S, Math.round(r * 1.4F)), cx, cy + r * 0.8F, S, Holo.argb(a, col));
        }
        long since = mc.level.getGameTime() - hintFrom;
        if (since >= 0 && since < HINT_TICKS) {
            float ha = a * Mth.clamp((HINT_TICKS - since) / 15.0F, 0.0F, 1.0F);
            Holo.sayCentered(g, font, "Scroll to change zoom (2× - 16×)", cx, cy - r * 0.62F, S, Holo.argb(ha * 0.9F, 0xFFFFFF));
        }
        RenderSystem.disableBlend();
    }
}
