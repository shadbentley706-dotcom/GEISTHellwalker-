package com.grapefruit.hellblazer.client;

import com.grapefruit.hellblazer.HellblazerMod;
import com.grapefruit.hellblazer.client.screen.CasebookScreen;
import com.grapefruit.hellblazer.client.ui.Grim;
import com.grapefruit.hellblazer.client.ui.Hud;
import com.grapefruit.hellblazer.network.EventPacket;
import com.grapefruit.hellblazer.occult.CaseFile;
import com.grapefruit.hellblazer.occult.CaseType;
import com.grapefruit.hellblazer.occult.HbRank;
import com.grapefruit.hellblazer.registry.HbSounds;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The casebook on the HUD when it's closed: K opens it; the case being worked is pinned on the right (on a red card
 * with a crack across one corner) with its progress, its clock and, for a marked place, which way and how far; a
 * banner for what comes back (a case taken, closed or lost); and on promotion the sigil burns up in the middle of the
 * screen with the new rank under it.
 */
@Mod.EventBusSubscriber(modid = HellblazerMod.MODID, value = Dist.CLIENT)
public final class CaseHud {
    public static final KeyMapping CASEBOOK = new KeyMapping("key.geist_hellblazer.casebook", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.geist_hellblazer");
    private static final int BANNER_LIFE = 80, PROMO_LIFE = 140;

    private record Banner(String title, String body, int color, boolean crack) {}

    private static final Deque<Banner> QUEUE = new ArrayDeque<>();
    private static Banner banner;
    private static int bannerAge, promoAge = -1, promoRank;

    private CaseHud() {}

    @Mod.EventBusSubscriber(modid = HellblazerMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(CASEBOOK);
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("casebook_hud", OVERLAY);
        }
    }

    private static void ui(SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    private static void banner(String title, String body, int color, boolean crack) {
        QUEUE.add(new Banner(title, body, color, crack));
        while (QUEUE.size() > 4) QUEUE.poll();
    }

    /** From the server. */
    public static void onEvent(EventPacket m) {
        Minecraft mc = Minecraft.getInstance();
        boolean book = mc.screen instanceof CasebookScreen;
        if (mc.screen instanceof CasebookScreen s) s.onEvent(m);
        switch (m.kind) {
            case EventPacket.RANK_UP -> {
                promoRank = m.a;
                promoAge = 0;
                ui(HbSounds.RANK_UP.get(), 1.0F, 0.9F);
            }
            case EventPacket.CLOSED -> {
                banner(CaseText.tr("case.geist_hellblazer.banner.closed"), CaseText.tr("case.geist_hellblazer.banner.paid", m.text, m.a, m.b), Grim.GREEN, false);
                ui(HbSounds.CASE_CLOSED.get(), 1.0F, 0.8F);
            }
            case EventPacket.FAILED -> {
                banner(CaseText.tr("case.geist_hellblazer.banner.failed"), m.text, Grim.RED, true);
                ui(HbSounds.CASE_FAILED.get(), 1.0F, 0.7F);
            }
            case EventPacket.ACCEPTED -> {
                if (!book) banner(CaseText.tr("case.geist_hellblazer.banner.accepted"), m.text, Grim.BONE, false);
            }
            case EventPacket.DENIED -> {
                if (!book) {
                    banner(CaseText.tr("case.geist_hellblazer.banner.denied"), CaseText.denial(m.text, m.a, m.b), Grim.RED, false);
                    ui(HbSounds.CASE_DENY.get(), 1.0F, 0.5F);
                }
            }
            default -> {
            }
        }
    }

    // ================================================================ ticking

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (CASEBOOK.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new CasebookScreen());
        }
        if (mc.isPaused()) return;
        if (banner == null && !QUEUE.isEmpty() && promoAge < 0) {
            banner = QUEUE.poll();
            bannerAge = 0;
        }
        if (banner != null && ++bannerAge > BANNER_LIFE) banner = null;
        if (promoAge >= 0 && ++promoAge > PROMO_LIFE) promoAge = -1;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        QUEUE.clear();
        banner = null;
        promoAge = -1;
    }

    // ================================================================ drawing

    public static final IGuiOverlay OVERLAY = (gui, g, pt, sw, sh) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        RenderSystem.enableBlend();
        Font f = mc.font;
        if (mc.screen == null) tracker(g, f, sw, sh, pt);
        if (banner != null) drawBanner(g, f, sw, sh, pt);
        if (promoAge >= 0) promotion(g, f, sw, sh, pt);
        RenderSystem.disableBlend();
    };

    /** The case being worked, pinned on the right. */
    private static void tracker(GuiGraphics g, Font f, int sw, int sh, float pt) {
        CaseFile c = CaseData.tracked();
        if (c == null) return;
        Minecraft mc = Minecraft.getInstance();
        float t = mc.level != null ? mc.level.getGameTime() + pt : 0.0F;
        int lh = Hud.lineH(Hud.SMALL);
        int w = Mth.clamp(sw / 5, 116, 176);
        int x1 = sw - 6, x0 = x1 - w;
        int y = Math.round(sh * 0.32F);
        String status = status(c, mc);
        int h = lh * (status.isEmpty() ? 3 : 4) + 10;
        int col = Grim.typeColor(c.type);
        g.fill(x0, y, x1, y + h, Grim.a(0.66F, 0x120405));
        g.fill(x0, y, x0 + 2, y + h, Grim.a(0.95F, col));
        g.fill(x0, y, x1, y + 1, Grim.a(0.5F, Grim.RED));
        // a crack across the corner of the card
        Grim.cracks(g, x0, y, x1, y + h, 9071L, 0.3F, 0.55F);
        int s = Math.max(1, lh / 5);
        Grim.glyph(g, Grim.typeIcon(c.type), x0 + 5, y + 3, s, Grim.a(0.95F, col));
        Hud.say(g, f, Hud.fit(f, c.name, Hud.SMALL, w - 14 - 7 * s), x0 + 8 + 7 * s, y + 3, Hud.SMALL, Grim.a(1.0F, Grim.BONE));
        Hud.say(g, f, Hud.fit(f, CaseText.objective(c), Hud.SMALL, w - 12), x0 + 5, y + 4 + lh, Hud.SMALL, Grim.a(0.92F, Grim.WHITE));
        int ly = y + 5 + lh * 2;
        if (!status.isEmpty()) {
            boolean urgent = status.startsWith("!");
            String shown = urgent ? status.substring(1) : status;
            float k = urgent ? (((int) (t / 5.0F)) % 2 == 0 ? 1.0F : 0.55F) : 0.85F;
            Hud.say(g, f, Hud.fit(f, shown, Hud.SMALL, w - 12), x0 + 5, ly, Hud.SMALL, Grim.a(k, urgent ? Grim.RED : Grim.GREY));
            ly += lh;
        }
        long left = CaseText.timeLeft(c);
        String clock = CaseText.clock(left);
        String count = c.progress + "/" + c.count;
        int clockCol = left > 0 && left < 600 ? (((int) (t / 5.0F)) % 2 == 0 ? Grim.RED : Grim.EMBER) : Grim.WHITE;
        int cw = Hud.textW(f, clock, Hud.SMALL) + Hud.textW(f, count, Hud.SMALL) + 12;
        Grim.progress(g, x0 + 5, ly + lh / 2 - 1, Math.max(10, w - 14 - cw), 3, c.progress / (float) Math.max(1, c.count), col, 1.0F);
        Hud.sayRight(g, f, count + "  " + clock, x1 - 5, ly, Hud.SMALL, Grim.a(1.0F, clockCol));
        // a marked place: which way, how far
        if ((c.type == CaseType.TARGET || c.type == CaseType.HAUNTING) && mc.player != null) {
            double dx = c.x + 0.5D - mc.player.getX(), dz = c.z + 0.5D - mc.player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            float rel = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getViewYRot(pt));
            String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
            Hud.sayRight(g, f, arrows[Math.floorMod(Math.round(rel / 45.0F), 8)] + " " + dist + " m", x1 - 5, y + h + 2, Hud.SMALL, Grim.a(1.0F, Grim.BONE));
        }
    }

    /** A line about where things stand with a marked place ("!" in front: urgent). */
    private static String status(CaseFile c, Minecraft mc) {
        if (mc.player == null || mc.level == null) return "";
        if (c.type == CaseType.HAUNTING) {
            double dx = c.x + 0.5D - mc.player.getX(), dz = c.z + 0.5D - mc.player.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 > 64.0D * 64.0D) return "";
            boolean dark = mc.level.isNight() || !mc.level.dimensionType().hasSkyLight();
            if (!dark) return CaseText.tr("case.geist_hellblazer.hud.wait_dark");
            return d2 <= 12.0D * 12.0D ? "!" + CaseText.tr("case.geist_hellblazer.hud.rising") : CaseText.tr("case.geist_hellblazer.hud.go_in");
        }
        if (c.type == CaseType.TARGET && c.spawned) return "!" + CaseText.tr("case.geist_hellblazer.hud.target_here", c.subject);
        return "";
    }

    private static void drawBanner(GuiGraphics g, Font f, int sw, int sh, float pt) {
        float t = bannerAge + pt;
        float a = Mth.clamp(t / 4.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((t - BANNER_LIFE + 12.0F) / 12.0F, 0.0F, 1.0F));
        if (a <= 0.01F) return;
        int lh = Hud.lineH(Hud.MEDIUM), lh2 = Hud.lineH(Hud.SMALL);
        int w = Math.max(Hud.textW(f, banner.title, Hud.MEDIUM), Hud.textW(f, banner.body, Hud.SMALL)) + 28;
        w = Math.min(w, sw - 20);
        int open = Math.round(w * Grim.ease(t / 6.0F));
        int cx = sw / 2, y = Math.round(sh * 0.66F), h = lh + lh2 + 8;
        g.fill(cx - open / 2, y, cx + open / 2, y + h, Grim.a(0.74F * a, 0x120405));
        g.fill(cx - open / 2, y, cx + open / 2, y + 1, Grim.a(a, banner.color));
        g.fill(cx - open / 2, y + h - 1, cx + open / 2, y + h, Grim.a(0.5F * a, banner.color));
        if (open < w - 2) return;
        if (banner.crack) Grim.cracks(g, cx - w / 2, y, cx + w / 2, y + h, 4417L, Mth.clamp(t / 10.0F, 0.0F, 1.0F) * 0.4F, a * 0.8F);
        Grim.fringe(g, f, banner.title, cx - Hud.textW(f, banner.title, Hud.MEDIUM) / 2.0F, y + 3, Hud.MEDIUM, banner.color, a);
        Hud.sayCentered(g, f, Hud.fit(f, banner.body, Hud.SMALL, w - 10), cx, y + 4 + lh, Hud.SMALL, Grim.a(a, Grim.WHITE));
    }

    /** Promotion: the sigil burns up out of the dark, the glass cracks, the new rank under it. */
    private static void promotion(GuiGraphics g, Font f, int sw, int sh, float pt) {
        float t = promoAge + pt;
        float a = Mth.clamp(t / 6.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((t - PROMO_LIFE + 20.0F) / 20.0F, 0.0F, 1.0F));
        if (a <= 0.01F) return;
        HbRank r = HbRank.byIndex(promoRank);
        g.fillGradient(0, 0, sw, sh / 3, Grim.a(0.65F * a, 0x000000), Grim.a(0.0F, 0x000000));
        g.fillGradient(0, sh * 2 / 3, sw, sh, Grim.a(0.0F, 0x000000), Grim.a(0.65F * a, 0x000000));
        int cx = sw / 2, cy = Math.round(sh * 0.34F);
        float grow = Grim.ease((t - 3.0F) / 12.0F);
        float sh2 = Math.min(sw, sh) * 0.28F * grow, sw2 = sh2 * 0.5F;
        float flicker = 0.75F + 0.25F * Mth.sin(t * 0.9F) * Mth.sin(t * 0.37F);
        if (sh2 > 4.0F) {
            Grim.sigil(g, cx - sw2 / 2.0F - 1.0F, cy - sh2 / 2.0F, sw2, sh2, 0xFF2020, 0.35F * a);
            Grim.sigil(g, cx - sw2 / 2.0F, cy - sh2 / 2.0F, sw2, sh2, Grim.RED, a * flicker);
        }
        if (t > 8.0F) Grim.cracks(g, cx - Math.round(sh2), cy - Math.round(sh2 * 0.6F), cx + Math.round(sh2), cy + Math.round(sh2 * 0.6F), 2207L,
                Mth.clamp((t - 8.0F) / 8.0F, 0.0F, 1.0F), a * 0.9F);
        int lhL = Hud.lineH(Hud.LARGE), lh = Hud.lineH(Hud.SMALL);
        int ty = cy + Math.round(sh2 / 2.0F) + 6;
        float tk = Mth.clamp((t - 12.0F) / 8.0F, 0.0F, 1.0F);
        if (tk > 0.0F) {
            String head = CaseText.tr("case.geist_hellblazer.promo.title");
            Grim.fringe(g, f, Grim.bleed(head, tk * head.length(), 5), cx - Hud.textW(f, head, Hud.LARGE) / 2.0F, ty, Hud.LARGE, Grim.BONE, a);
            Hud.sayCentered(g, f, r.title, cx, ty + lhL + 2, Hud.MEDIUM, Grim.a(a * tk, Grim.RED));
        }
        float uk = Mth.clamp((t - 26.0F) / 10.0F, 0.0F, 1.0F);
        if (uk > 0.0F) {
            String opens = CaseText.tr("case.geist_hellblazer.rank." + r.name().toLowerCase(java.util.Locale.ROOT) + ".opens");
            Hud.sayCentered(g, f, opens, cx, ty + lhL + Hud.lineH(Hud.MEDIUM) + 6, Hud.SMALL, Grim.a(a * uk, Grim.GREY));
        }
    }
}
