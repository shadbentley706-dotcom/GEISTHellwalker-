package com.geist.client;

import com.geist.GeistMod;
import com.geist.client.exo.ExoHud;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;


/**
 * The G.E.I.S.T. holographic HUD (v11), laid out to be read at a glance:
 * <ul>
 *   <li>top centre: a compass with your heading, plus red marks showing which way nearby hostiles are;</li>
 *   <li>top left: LOCATION (coordinates, which way you face, biome, whether mobs can spawn where you stand)
 *       and STATUS (exact health and food, armour wear, XP);</li>
 *   <li>top right: TIME (clock, countdown to night or sunrise, moon, area danger) and NEARBY (radar, how many
 *       hostiles / friendly creatures / players are close, and where the closest hostile is);</li>
 *   <li>around the crosshair: lock-on tags, the trajectory readout, chest x-ray and the portal helper.</li>
 * </ul>
 * Sharp text with shadows on dark glass cards stays readable over bright sky. The cards move out of the
 * way of potion icons, boss bars and the exo-frame panel, fade while you zoom, and a key guide shows for
 * a few seconds every time the HUD boots.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class GeistHud {
    /** Glass panel behind the hotbar (drawn beneath the vanilla hotbar). */
    public static final IGuiOverlay PANELS = (gui, g, pt, w, h) -> renderPanels(g, pt, w, h);
    /** Everything else (drawn above the vanilla HUD). */
    public static final IGuiOverlay HUD = (gui, g, pt, w, h) -> renderAll(g, pt, w, h);

    private static final int M = 6, GAP = 4;
    /** How long the key guide stays up after the HUD boots. */
    private static final int GUIDE_TICKS = 200;
    private static final String[] FACING = {"North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West"};
    private static final String[] AXIS = {"-Z", "+X -Z", "+X", "+X +Z", "+Z", "-X +Z", "-X", "-X -Z"};
    private static final String[] MOON = {"Full moon", "Waning gibbous", "Last quarter", "Waning crescent",
            "New moon", "Waxing crescent", "First quarter", "Waxing gibbous"};
    /** Bottom of the lowest boss bar drawn this frame (0 = none), so the compass can sit below it. */
    private static int bossBottom;

    private GeistHud() {}

    @Mod.EventBusSubscriber(modid = GeistMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "geist_panels", PANELS);
            event.registerAboveAll("geist_zoom", Zoom.OVERLAY);
            event.registerAboveAll("geist_hud", HUD);
        }
    }

    /** Boss bars are drawn before the HUD each frame: remember how far down they reach. */
    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        bossBottom = Math.max(bossBottom, event.getY() + 7);
    }

    private static void renderPanels(GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (!GeistState.hudActive(mc) || mc.options.hideGui) return;
        float b = GeistState.boot(pt);
        RenderSystem.enableBlend();
        g.fill(sw / 2 - 94, sh - 25, sw / 2 + 94, sh, Holo.argb(0.35F * b, 0x06202E));
        RenderSystem.disableBlend();
    }

    private static void renderAll(GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        int bossY = bossBottom;
        bossBottom = 0;
        if (mc.player == null || mc.level == null || mc.options.hideGui || !GeistState.hudActive(mc)) return;   // Z off = nothing
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float time = mc.level.getGameTime() + pt;
        float b = GeistState.boot(pt);
        float zoom = Zoom.amount(pt);
        float a = Mth.clamp((b - 0.6F) / 0.4F, 0.0F, 1.0F) * (1.0F - zoom);    // cards: in after boot, out while zoomed

        g.fill(0, 0, sw, sh, Holo.argb(0.05F * b, 0x2A80C8));                       // a faint lens tint
        edgeBrackets(g, sw, sh, b);
        Holo.frame(g, sw / 2 - 94, sh - 25, sw / 2 + 94, sh - 1, 3, Holo.NEON, 0.8F, b);   // hotbar frame

        boolean[] on = GeistState.MODULES;
        if (on[GeistState.SCAN]) EntityScanner.renderTargets(g, mc, sw, sh, pt, time, b >= 1.0F);
        if (b < 1.0F) {
            bootSequence(g, mc, sw, sh, b);
            RenderSystem.disableBlend();
            return;
        }
        int rightY = M + effectRows(mc);
        if (on[GeistState.NAV]) {
            // the compass fits between the top cards: measure them first
            int top = Math.max(M, bossY + 3);
            int leftW = a > 0.02F ? locationWidth(mc) : 0;
            int rightW = a > 0.02F && on[GeistState.CHRONO] && rightY < top + compassHeight() ? timeWidth(mc) : 0;
            int room = Math.min(sw / 2 - M - leftW, sw / 2 - M - rightW) - 16;
            compass(g, mc, sw, top, Mth.clamp(room, 30, 100), pt, on[GeistState.DEEP]);
        }
        if (a > 0.02F) {
            int leftLimit = Math.min(ExoHud.panelTop(mc, sw, sh), com.geist.client.gauntlet.GauntletHud.panelTop(mc, sw, sh)) - GAP, rightLimit = sh - M;
            int y = M;
            if (on[GeistState.NAV]) y = locationCard(g, mc, M, y, a, leftLimit) + GAP;
            if (on[GeistState.VITALS]) statusCard(g, mc, M, y, a, time, leftLimit);
            // the chest / portal panels open beside the crosshair: on narrow screens, dim the right column under them
            float ra = sw < 720 && (ChestPeek.active(mc) || PortalSyncer.active(mc)) ? a * 0.25F : a;
            if (on[GeistState.CHRONO]) rightY = timeCard(g, mc, sw, rightY, ra, rightLimit) + GAP;
            if (on[GeistState.DEEP]) rightY = nearbyCard(g, mc, sw, rightY, pt, time, ra, rightLimit) + GAP;
        }
        if (on[GeistState.DEEP]) EntityScanner.renderThreats(g, mc, sw, sh, pt, time);
        ChestPeek.render(g, mc, sw, sh, time);
        PortalSyncer.render(g, mc, sw, sh, pt, time);
        OrePulse.renderHud(g, mc, sw, sh, pt, rightY);
        TrajectoryPredictor.renderHud(g, mc, sw, sh, pt, time);
        SmokeVision.renderHud(g, mc, sw, time);
        guide(g, mc, sw, sh, time);
        RenderSystem.disableBlend();
    }

    // ---------------------------------------------------------------- frame

    private static void edgeBrackets(GuiGraphics g, int sw, int sh, float b) {
        int c = Holo.argb(0.4F * b, Holo.NEON), m = 3, len = (int) (20 * b);
        Holo.hline(g, m, m + len, m, c); Holo.vline(g, m, m, m + len, c);
        Holo.hline(g, sw - 1 - m - len, sw - 1 - m, m, c); Holo.vline(g, sw - 1 - m, m, m + len, c);
        Holo.hline(g, m, m + len, sh - 1 - m, c); Holo.vline(g, m, sh - 1 - m - len, sh - 1 - m, c);
        Holo.hline(g, sw - 1 - m - len, sw - 1 - m, sh - 1 - m, c); Holo.vline(g, sw - 1 - m, sh - 1 - m - len, sh - 1 - m, c);
    }

    /** Height taken by the potion-effect icons in the top right corner (one row for good effects, one for bad). */
    private static int effectRows(Minecraft mc) {
        boolean good = false, bad = false;
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            if (!e.showIcon()) continue;
            if (e.getEffect().isBeneficial()) good = true;
            else bad = true;
        }
        return bad ? 52 : good ? 26 : 0;
    }

    /** Card width for its content; the limits grow with the text size (which follows the GUI scale). */
    private static int cardW(int content) {
        float k = Holo.px(Holo.SMALL) / 0.5F;
        return Mth.clamp(content + 10, Math.round(96 * k), maxW());
    }

    private static int maxW() {
        return Math.round(150 * Holo.px(Holo.SMALL) / 0.5F);
    }

    /** Draws text and returns the x just after it (for lines mixing colours). */
    private static float seg(GuiGraphics g, Font font, String s, float x, float y, float size, int argb) {
        Holo.say(g, font, s, x, y, size, argb);
        return x + Holo.textW(font, s, size);
    }

    // ---------------------------------------------------------------- compass (top centre)

    private static int compassHeight() {
        return Holo.lineH(Holo.MEDIUM) + 4 + 4 + Holo.lineH(Holo.SMALL) + 2;
    }

    private static void compass(GuiGraphics g, Minecraft mc, int sw, int y, int half, float pt, boolean blips) {
        Font font = mc.font;
        int cx = sw / 2;
        float heading = (Mth.wrapDegrees(mc.player.getViewYRot(pt)) + 180.0F + 360.0F) % 360.0F;   // 0 = north
        int letters = Holo.lineH(Holo.MEDIUM), base = y + letters + 4;
        g.fill(cx - half - 6, y - 2, cx + half + 6, base + 3, Holo.argb(0.4F, 0x03121B));
        Holo.hline(g, cx - half, cx + half, base, Holo.argb(0.6F, Holo.NEON));
        for (int deg = 0; deg < 360; deg += 15) {
            float rel = Mth.wrapDegrees(deg - heading);
            if (Math.abs(rel) > 60.0F) continue;
            int x = cx + Math.round(rel / 60.0F * half);
            float fade = 1.0F - Math.abs(rel) / 60.0F * 0.75F;
            boolean major = deg % 45 == 0;
            Holo.vline(g, x, base - (major ? 3 : 1), base, Holo.argb(fade, Holo.NEON));
            if (major) {
                boolean main = deg % 90 == 0;
                float size = main ? Holo.MEDIUM : Holo.SMALL;
                int col = deg == 0 ? 0xFF5B5B : main ? Holo.WHITE : Holo.NEON;     // north in red, like a real compass
                Holo.sayCentered(g, font, cardinal(deg), x, base - 3 - Holo.lineH(size), size, Holo.argb(fade, col));
            }
        }
        if (blips) {                                     // hostiles nearby: a mark on the tape, or an arrow at its end
            for (float[] bl : EntityScanner.blips(mc, pt)) {
                int col = Holo.argb(0.95F, (int) bl[2]);
                if (Math.abs(bl[0]) <= 60.0F) {
                    int x = cx + Math.round(bl[0] / 60.0F * half);
                    g.fill(x - 1, base + 1, x + 2, base + 3, col);
                    g.fill(x, base + 3, x + 1, base + 4, col);
                } else {
                    int x = bl[0] > 0 ? cx + half + 3 : cx - half - 4;
                    int d = bl[0] > 0 ? 1 : -1;
                    for (int i = 0; i < 3; i++) Holo.vline(g, x + d * i, base - 2 + i, base + 2 - i, col);
                }
            }
        }
        String hd = String.format("%s %03d°", cardinal(heading), Math.round(heading) % 360);
        float S = Holo.SMALL;
        int bw = Holo.textW(font, hd, S) + 8, by = base + 4;
        g.fill(cx - bw / 2, by, cx + bw / 2, by + Holo.lineH(S) + 2, Holo.argb(0.55F, 0x03121B));
        Holo.hline(g, cx - bw / 2, cx + bw / 2 - 1, by, Holo.argb(0.8F, Holo.NEON));
        Holo.sayCentered(g, font, hd, cx, by + 2, S, 0xFFFFFFFF);
    }

    // ---------------------------------------------------------------- LOCATION card (top left)

    /** The LOCATION card's lines: coordinates, facing, biome, light; the last entry is "1" if mobs can spawn here. */
    private static String[] locationLines(Minecraft mc) {
        Player p = mc.player;
        BlockPos pos = p.blockPosition();
        float heading = (Mth.wrapDegrees(p.getYRot()) + 180.0F + 360.0F) % 360.0F;
        int dir = Math.floorMod(Math.round(heading / 45.0F), 8);
        int raw = mc.level.getMaxLocalRawBrightness(pos), blockLight = mc.level.getBrightness(LightLayer.BLOCK, pos);
        boolean spawns = blockLight == 0 && raw <= 7;
        return new String[]{"X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ(),
                "Facing " + FACING[dir] + " (" + AXIS[dir] + ")", biomeName(mc, pos),
                "Light " + raw + (spawns ? " · mobs can spawn" : " · too bright for mobs"), spawns ? "1" : ""};
    }

    private static int locationWidth(Minecraft mc) {
        Font font = mc.font;
        String[] l = locationLines(mc);
        return cardW(Math.max(Math.max(Holo.textW(font, l[0], Holo.MEDIUM), Holo.textW(font, l[1], Holo.SMALL)),
                Math.max(Holo.textW(font, l[2], Holo.SMALL), Holo.textW(font, l[3], Holo.SMALL))));
    }

    private static int locationCard(GuiGraphics g, Minecraft mc, int x, int y, float a, int limit) {
        Font font = mc.font;
        float S = Holo.SMALL, Md = Holo.MEDIUM;
        BlockPos pos = mc.player.blockPosition();
        String[] coord = {String.valueOf(pos.getX()), String.valueOf(pos.getY()), String.valueOf(pos.getZ())};
        String[] l = locationLines(mc);
        String facing = l[1], biome = l[2], light = l[3];
        boolean spawns = !l[4].isEmpty();
        int w = locationWidth(mc);
        int h = titleH() + Holo.lineH(Md) + 1 + 3 * Holo.lineH(S) + 3;
        if (y + h > limit) return y - GAP;
        Holo.card(g, x, y, x + w, y + h, Holo.NEON, a);
        int ty = Holo.cardTitle(g, font, "LOCATION", x, y, x + w, Holo.NEON, a);
        int label = Holo.argb(a, Holo.NEON), value = Holo.argb(a, 0xFFFFFF);
        float cx = x + 5;
        String[] names = {"X ", "Y ", "Z "};
        for (int i = 0; i < 3; i++) {
            cx = seg(g, font, names[i], cx, ty, Md, label);
            cx = seg(g, font, coord[i] + (i < 2 ? "  " : ""), cx, ty, Md, value);
        }
        ty += Holo.lineH(Md) + 1;
        Holo.say(g, font, Holo.fit(font, facing, S, w - 9), x + 5, ty, S, Holo.argb(a, 0xE8F6FF));
        ty += Holo.lineH(S);
        Holo.say(g, font, Holo.fit(font, biome, S, w - 9), x + 5, ty, S, Holo.argb(a * 0.85F, 0xC8D8E8));
        ty += Holo.lineH(S);
        Holo.say(g, font, Holo.fit(font, light, S, w - 9), x + 5, ty, S, Holo.argb(a, spawns ? Holo.AMBER : Holo.GREEN));
        return y + h;
    }

    private static String biomeName(Minecraft mc, BlockPos pos) {
        return mc.level.getBiome(pos).unwrapKey().map(k -> {
            ResourceLocation id = k.location();
            String key = Util.makeDescriptionId("biome", id);
            String s = Component.translatable(key).getString();
            if (!s.equals(key)) return s;
            StringBuilder sb = new StringBuilder();                     // no translation: "dark_forest" -> "Dark Forest"
            for (String part : id.getPath().split("_")) {
                if (part.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return sb.toString();
        }).orElse("Unknown biome");
    }

    private static int titleH() {
        return 3 + Holo.lineH(Holo.SMALL) + 3;
    }

    // ---------------------------------------------------------------- STATUS card (left, under LOCATION)

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static void statusCard(GuiGraphics g, Minecraft mc, int x, int y, float a, float time, int limit) {
        Font font = mc.font;
        Player p = mc.player;
        float S = Holo.SMALL, Md = Holo.MEDIUM;
        int lm = Holo.lineH(Md), ls = Holo.lineH(S);
        float hp = p.getHealth(), max = Math.max(1.0F, p.getMaxHealth()), abs = p.getAbsorptionAmount();
        boolean low = hp / max < 0.3F;
        int food = p.getFoodData().getFoodLevel();
        float sat = p.getFoodData().getSaturationLevel();
        boolean underwater = p.getAirSupply() < p.getMaxAirSupply();
        int pieces = 0;
        for (EquipmentSlot slot : ARMOR) if (p.getItemBySlot(slot).isDamageableItem()) pieces++;
        int armorRows = Math.max(1, (pieces + 1) / 2);
        int w = cardW(Holo.textW(font, "Health 20 / 20 +20", Md));
        int h = titleH() + (lm + 5) * 2 + ls + 2 + ls + armorRows * (ls + 1) + 2 + ls + 5 + (underwater ? ls + 5 : 0) + 1;
        if (y + h > limit) {                                            // not enough room: only the essentials
            h = titleH() + (lm + 5) * 2 + 1;
            if (y + h > limit) return;
            underwater = false;
            armorRows = -1;
        }
        int accent = low ? Holo.RED : Holo.NEON;
        float warn = low ? 0.7F + 0.3F * Mth.sin(time * 0.5F) : 1.0F;
        Holo.card(g, x, y, x + w, y + h, accent, a * warn);
        int ty = Holo.cardTitle(g, font, low ? "STATUS · LOW HEALTH" : "STATUS", x, y, x + w, accent, a);
        int left = x + 5, right = x + w - 5, bw = w - 10;
        // health (+ absorption in gold)
        Holo.say(g, font, "Health", left, ty, Md, Holo.argb(a, 0xFF7B8B));
        String hs = EntityScanner.num(hp) + " / " + EntityScanner.num(max) + (abs > 0 ? " +" + EntityScanner.num(abs) : "");
        Holo.sayRight(g, font, hs, right, ty, Md, Holo.argb(a, 0xFFFFFF));
        ty += lm + 1;
        Holo.bar(g, left, ty, bw, 3, hp / max, Holo.argb(a, 0xFF3B4B), Holo.argb(0.35F * a, 0xFF3B4B));
        if (abs > 0) g.fill(left, ty + 2, left + Math.round(bw * Math.min(1.0F, abs / max)), ty + 3, Holo.argb(a, 0xFFD54A));
        ty += 4;
        // food
        Holo.say(g, font, "Food", left, ty, Md, Holo.argb(a, 0xFFB56B));
        Holo.sayRight(g, font, food + " / 20", right, ty, Md, Holo.argb(a, 0xFFFFFF));
        ty += lm + 1;
        Holo.bar(g, left, ty, bw, 3, food / 20.0F, Holo.argb(a, 0xFF9F3B), Holo.argb(0.35F * a, 0xFF9F3B));
        ty += 4;
        if (armorRows < 0) return;
        Holo.say(g, font, "Saturation " + EntityScanner.num(sat) + (sat <= 0.0F && food < 20 ? " · eat soon" : ""), left, ty, S,
                Holo.argb(a * 0.9F, sat <= 0.0F ? Holo.AMBER : 0xFFE07A));
        ty += ls + 2;
        // armour: points, then the wear of each piece (two per row)
        double tough = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        Holo.say(g, font, "Armor " + p.getArmorValue() + (tough > 0 ? " · toughness " + Math.round(tough) : ""), left, ty, S, Holo.argb(a, 0xC8D8E8));
        ty += ls;
        if (pieces == 0) {
            Holo.say(g, font, "No armor worn", left, ty, S, Holo.argb(a * 0.7F, 0x8090A0));
            ty += ls + 1;
        } else {
            int col = 0, half = (bw - 4) / 2;
            for (EquipmentSlot slot : ARMOR) {
                ItemStack st = p.getItemBySlot(slot);
                if (!st.isDamageableItem()) continue;
                int px = left + col * (half + 4);
                armorPiece(g, font, st, px, ty, half, a);
                if (++col == 2) { col = 0; ty += ls + 1; }
            }
            if (col == 1) ty += ls + 1;
        }
        ty += 2;
        // experience
        String xp = "Level " + p.experienceLevel;
        Holo.say(g, font, xp, left, ty, S, Holo.argb(a, 0x9CFF5A));
        int xx = left + Holo.textW(font, xp, S) + 4;
        Holo.bar(g, xx, ty + ls / 2 - 1, right - xx, 2, p.experienceProgress, Holo.argb(a, 0x7CFF3A), Holo.argb(0.3F * a, 0x7CFF3A));
        ty += ls + 2;
        if (underwater) {
            float air = Math.max(0, p.getAirSupply()) / (float) p.getMaxAirSupply();
            Holo.say(g, font, "Air", left, ty, S, Holo.argb(a, 0x8FD0FF));
            int ax = left + Holo.textW(font, "Air", S) + 4;
            Holo.bar(g, ax, ty + ls / 2 - 1, right - ax, 2, air, Holo.argb(a, air < 0.3F ? Holo.RED : 0x8FD0FF), Holo.argb(0.3F * a, 0x8FD0FF));
        }
    }

    /** A small armour icon, a wear bar and the percentage left. Red with "!" when it is about to break. */
    private static void armorPiece(GuiGraphics g, Font font, ItemStack st, int x, int y, int w, float a) {
        float S = Holo.SMALL;
        float left = 1.0F - st.getDamageValue() / (float) Math.max(1, st.getMaxDamage());
        int c = left > 0.5F ? 0x4CFF7A : (left > 0.2F ? 0xFFC14D : 0xFF3B3B);
        if (a > 0.5F) {
            g.pose().pushPose();
            g.pose().translate(x, y - 1, 0.0F);
            g.pose().scale(0.5F, 0.5F, 1.0F);
            g.renderItem(st, 0, 0);
            g.pose().popPose();
        }
        String pc = Math.round(left * 100) + "%" + (left <= 0.2F ? "!" : "");
        int tw = Holo.textW(font, pc, S);
        Holo.sayRight(g, font, pc, x + w, y, S, Holo.argb(a, c));
        int bx = x + 10, bw = w - 10 - tw - 3;
        if (bw > 3) Holo.bar(g, bx, y + Holo.lineH(S) / 2 - 1, bw, 2, left, Holo.argb(a, c), Holo.argb(0.25F * a, c));
    }

    // ---------------------------------------------------------------- TIME card (top right)

    private static int timeWidth(Minecraft mc) {
        return timeCard(null, mc, 0, 0, 0.0F, 0);
    }

    /** Draws the TIME card and returns its bottom; with no GuiGraphics it only measures, and returns its width. */
    private static int timeCard(@Nullable GuiGraphics g, Minecraft mc, int sw, int y, float a, int limit) {
        Font font = mc.font;
        float S = Holo.SMALL, L = Holo.LARGE;
        long dt = mc.level.getDayTime();
        int t = (int) (dt % 24000L);
        int hours = (t / 1000 + 6) % 24, mins = (t % 1000) * 60 / 1000;
        String clock = String.format("%02d:%02d", hours, mins);
        String day = "Day " + (dt / 24000L + 1);
        String moon = MOON[mc.level.getMoonPhase()];
        boolean night = t >= 13000 && t < 23000;
        int left = night ? 23000 - t : (t < 13000 ? 13000 - t : 37000 - t);       // ticks until sunrise / nightfall
        int secs = left / 20;
        String countdown = (night ? "Sunrise in " : "Night falls in ") + secs / 60 + ":" + String.format("%02d", secs % 60);
        String mobs = night ? "Monsters roam outside" : "Daytime · safer outside";
        float cd = GeistState.clampedDifficulty;
        String danger = cd < 0.0F ? "" : "Area danger: " + (cd < 0.25F ? "Low" : cd < 0.6F ? "Medium" : "High");
        int topRow = Math.max(Holo.lineH(L), 2 * Holo.lineH(S));
        int w = cardW(Math.max(Math.max(Holo.textW(font, countdown, S), Holo.textW(font, mobs, S)),
                Holo.textW(font, clock, L) + 6 + Math.max(Holo.textW(font, day, S), Holo.textW(font, moon, S))));
        if (g == null) return w;
        int h = titleH() + topRow + 2 + Holo.lineH(S) + 5 + Holo.lineH(S) + (danger.isEmpty() ? 0 : Holo.lineH(S)) + 2;
        if (y + h > limit) return y - GAP;
        int x = sw - M - w;
        Holo.card(g, x, y, x + w, y + h, Holo.NEON, a);
        int ty = Holo.cardTitle(g, font, "TIME", x, y, x + w, Holo.NEON, a);
        Holo.say(g, font, clock, x + 5, ty, L, Holo.argb(a, 0xFFFFFF));
        Holo.sayRight(g, font, day, x + w - 5, ty, S, Holo.argb(a, Holo.NEON));
        Holo.sayRight(g, font, moon, x + w - 5, ty + Holo.lineH(S), S, Holo.argb(a * 0.85F, 0xDDE6FF));
        ty += topRow + 2;
        int cc = night ? 0x8FD0FF : Holo.AMBER;
        Holo.say(g, font, countdown, x + 5, ty, S, Holo.argb(a, cc));
        ty += Holo.lineH(S) + 1;
        float span = night ? 10000.0F : 14000.0F;                       // night 13000-23000, day 23000-13000
        Holo.bar(g, x + 5, ty, w - 10, 2, 1.0F - left / span, Holo.argb(a, cc), Holo.argb(0.25F * a, cc));
        ty += 4;
        Holo.say(g, font, mobs, x + 5, ty, S, Holo.argb(a, night ? 0xFF6B6B : Holo.GREEN));
        ty += Holo.lineH(S);
        if (!danger.isEmpty()) {
            int dc = cd < 0.25F ? Holo.GREEN : cd < 0.6F ? Holo.AMBER : Holo.RED;
            Holo.say(g, font, danger, x + 5, ty, S, Holo.argb(a, dc));
        }
        return y + h;
    }

    // ---------------------------------------------------------------- NEARBY card (right, under TIME)

    private static int nearbyCard(GuiGraphics g, Minecraft mc, int sw, int y, float pt, float time, float a, int limit) {
        Font font = mc.font;
        float S = Holo.SMALL;
        int ls = Holo.lineH(S);
        int[] n = EntityScanner.counts(mc);
        String[] rows = {n[0] + " hostile", n[1] + " friendly", n[2] + (n[2] == 1 ? " player" : " players")};
        int[] cols = {Holo.RED, Holo.GREEN, Holo.BLUE};
        LivingEntity near = EntityScanner.nearestThreat(mc);
        String closest = near == null ? "No hostiles nearby"
                : near.getDisplayName().getString() + " · " + Math.round(mc.player.distanceTo(near)) + " m " + EntityScanner.direction(mc, near, pt);
        int r = Mth.clamp((limit - y) / 5, 18, 26);
        int textW = 0;
        for (String s : rows) textW = Math.max(textW, Holo.textW(font, s, S) + 7);
        int w = cardW(Math.max(2 * r + 8 + textW, Math.min(Holo.textW(font, closest, S), maxW() - 10)));
        int h = titleH() + 2 * r + 4 + ls + 2;
        if (y + h > limit) return y - GAP;
        int x = sw - M - w;
        Holo.card(g, x, y, x + w, y + h, n[0] > 0 ? Holo.RED : Holo.NEON, a);
        String title = "NEARBY · " + Math.round(EntityScanner.scanRadius()) + " m";
        int ty = Holo.cardTitle(g, font, title, x, y, x + w, n[0] > 0 ? 0xFF6B6B : Holo.NEON, a);
        float rcx = x + 5 + r, rcy = ty + r + 1;
        EntityScanner.renderRadar(g, mc, rcx, rcy, r, pt, time, a);
        int tx = x + 5 + 2 * r + 8, ry = Math.round(rcy - 1.5F * ls);
        for (int i = 0; i < 3; i++) {
            g.fill(tx, ry + i * ls + 2, tx + 3, ry + i * ls + 5, Holo.argb(a, cols[i]));
            Holo.say(g, font, rows[i], tx + 6, ry + i * ls, S, Holo.argb(a * (n[i] > 0 ? 1.0F : 0.55F), 0xE8F6FF));
        }
        ty += 2 * r + 4;
        Holo.say(g, font, Holo.fit(font, closest, S, w - 9), x + 5, ty, S, Holo.argb(a, near == null ? Holo.GREEN : 0xFF6B6B));
        return y + h;
    }

    // ---------------------------------------------------------------- boot + key guide

    private static void bootSequence(GuiGraphics g, Minecraft mc, int sw, int sh, float b) {
        Font font = mc.font;
        String full = Component.translatable("hud.geist.boot").getString();
        int n = Math.min(full.length(), (int) (full.length() * Mth.clamp(b * 1.4F, 0.0F, 1.0F)));
        String s = full.substring(0, n) + ((int) (b * 20) % 2 == 0 ? "_" : "");
        float M2 = Holo.MEDIUM;
        float x = sw / 2.0F - Holo.textW(font, full, M2) / 2.0F;
        Holo.say(g, font, s, x, sh / 2.0F + 24, M2, Holo.argb(1.0F, Holo.NEON));
        int bw = (int) (120 * b), by = Math.round(sh / 2.0F + 26 + Holo.lineH(M2));
        g.fill(sw / 2 - 60, by, sw / 2 + 60, by + 1, Holo.argb(0.3F, Holo.NEON));
        g.fill(sw / 2 - 60, by, sw / 2 - 60 + bw, by + 1, Holo.argb(0.9F, Holo.NEON));
    }

    /** For a few seconds after the HUD boots: the keys, and what the colours mean. */
    private static void guide(GuiGraphics g, Minecraft mc, int sw, int sh, float time) {
        float since = time - GeistState.bootStart - GeistState.BOOT_TICKS;
        if (since < 0.0F || since > GUIDE_TICKS) return;
        float a = Mth.clamp((GUIDE_TICKS - since) / 30.0F, 0.0F, 1.0F) * Mth.clamp(since / 8.0F, 0.0F, 1.0F);
        if (a <= 0.02F) return;
        Font font = mc.font;
        float S = Holo.SMALL;
        String keys = "[" + key(GeistState.TOGGLE) + "] HUD on/off    [Hold " + key(GeistState.WHEEL) + "] Command wheel    [Hold "
                + key(GeistState.ZOOM) + "] Zoom (scroll to change)";
        String[] legend = {"Friendly", "Hostile", "Player", "Locked on"};
        int[] lc = {Holo.GREEN, Holo.RED, Holo.BLUE, Holo.BLUE};
        int legendW = 0;
        for (String s : legend) legendW += Holo.textW(font, s, S) + 12;
        int ls = Holo.lineH(S);
        int w = Math.max(Holo.textW(font, keys, S), legendW) + 14, h = titleH() + ls * 2 + 4;
        int x = sw / 2 - w / 2, y = sh - 64 - h;
        Holo.card(g, x, y, x + w, y + h, Holo.NEON, a);
        int ty = Holo.cardTitle(g, font, "G.E.I.S.T. CONTROLS", x, y, x + w, Holo.NEON, a);
        Holo.sayCentered(g, font, keys, sw / 2.0F, ty, S, Holo.argb(a, 0xFFFFFF));
        ty += ls + 2;
        float lx = sw / 2.0F - legendW / 2.0F;
        for (int i = 0; i < legend.length; i++) {
            int bx = Math.round(lx);
            if (i < 3) g.fill(bx, ty + 1, bx + 5, ty + 6, Holo.argb(a, lc[i]));
            else {
                int c = Holo.argb(a, lc[i]);
                g.fill(bx, ty + 1, bx + 2, ty + 2, c); g.fill(bx, ty + 1, bx + 1, ty + 3, c);
                g.fill(bx + 3, ty + 5, bx + 5, ty + 6, c); g.fill(bx + 4, ty + 4, bx + 5, ty + 6, c);
            }
            lx = seg(g, font, legend[i], lx + 8, ty, S, Holo.argb(a, 0xE8F6FF)) + 4;
        }
    }

    private static String key(net.minecraft.client.KeyMapping k) {
        return k.getTranslatedKeyMessage().getString().toUpperCase();
    }

    public static String cardinal(float heading) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[Math.floorMod(Math.round(heading / 45.0F), 8)];
    }
}
