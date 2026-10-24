package com.geist.client;

import com.geist.GeistMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * G.E.I.S.T. Entity Scanner.
 * Basic mode (HUD off): brackets on every creature in line of sight within 32 blocks.
 * Upgraded mode (HUD on): everything within 15 blocks is tracked even through walls (dashed box when
 * hidden); blue brackets lock onto what you look at with a plain info tag; a radar, compass blips and
 * edge arrows warn of hostiles behind you or out of sight.
 * Colours: green = friendly, red = hostile, blue = players.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class EntityScanner {
    private static final double SIGHT_RANGE = 32.0D, SCAN_RADIUS = 15.0D;

    private record Target(LivingEntity e, float x0, float y0, float x1, float y1, int rgb, boolean seen) {
        float cx() { return (x0 + x1) / 2.0F; }
        float cy() { return (y0 + y1) / 2.0F; }
    }

    private static final List<Target> TARGETS = new ArrayList<>();
    private static final Map<Integer, Boolean> SCAN = new HashMap<>();      // entity id -> in line of sight
    private static final Set<Integer> THREATS = new HashSet<>();            // hostiles within the scan radius
    private static final Set<Integer> ON_SCREEN = new HashSet<>();
    private static int lockId = -1;
    private static int alertCooldown;

    private EntityScanner() {}

    static int classify(LivingEntity e) {
        if (e instanceof Player) return Holo.BLUE;
        if (e instanceof Enemy) return Holo.RED;
        if (e instanceof NeutralMob n && n.isAngry()) return Holo.RED;
        return Holo.GREEN;
    }

    static String stance(LivingEntity e) {
        if (e instanceof Player) return "PLAYER";
        if (e instanceof Enemy || (e instanceof NeutralMob n && n.isAngry())) return "HOSTILE";
        if (e instanceof NeutralMob) return "NEUTRAL";
        return "PASSIVE";
    }

    private static boolean scannable(Minecraft mc, Entity en) {
        return en instanceof LivingEntity le && en != mc.player && le.isAlive() && !(en instanceof ArmorStand)
                && (!en.isInvisible() || en.isCurrentlyGlowing());
    }

    public static int targetCount() { return TARGETS.size(); }

    public static int hostileCount() {
        int n = 0;
        for (Target t : TARGETS) if (t.rgb() == Holo.RED) n++;
        return n;
    }

    // ---------------------------------------------------------------- scan (20 times a second)

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        SCAN.clear();
        Set<Integer> before = new HashSet<>(THREATS);
        THREATS.clear();
        if (alertCooldown > 0) alertCooldown--;
        if (!GeistState.hudActive(mc) || mc.level == null) return;               // Z off = no scanning
        boolean hud = true;
        boolean deep = GeistState.module(mc, GeistState.DEEP);
        if (hud && !deep && !GeistState.MODULES[GeistState.SCAN]) return;            // both scan modules off
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!scannable(mc, en)) continue;
            double d2 = en.distanceToSqr(mc.player);
            if (d2 > SIGHT_RANGE * SIGHT_RANGE) continue;
            boolean los = mc.player.hasLineOfSight(en);
            boolean inRadius = deep && d2 <= SCAN_RADIUS * SCAN_RADIUS;
            if (los || inRadius) SCAN.put(en.getId(), los);
            if (inRadius && classify((LivingEntity) en) == Holo.RED) THREATS.add(en.getId());
        }
        // a soft alert when a new hostile enters the scan radius out of sight
        if (deep && alertCooldown == 0) {
            for (int id : THREATS) {
                if (!before.contains(id) && (!SCAN.getOrDefault(id, false) || !ON_SCREEN.contains(id))) {
                    mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 2.0F);
                    alertCooldown = 30;
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- project hitboxes onto the screen (every frame)

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        TARGETS.clear();
        ON_SCREEN.clear();
        Minecraft mc = Minecraft.getInstance();
        if (!GeistState.wearing(mc) || mc.level == null || SCAN.isEmpty()) {
            lockId = -1;                                                // nothing tracked: drop the lock
            return;
        }
        Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
        Matrix4f proj = new Matrix4f(event.getProjectionMatrix());
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        for (Entity en : mc.level.entitiesForRendering()) {
            Boolean seen = SCAN.get(en.getId());
            if (seen == null || !(en instanceof LivingEntity le) || !le.isAlive()) continue;
            double ox = Mth.lerp(pt, en.xOld, en.getX()) - en.getX();
            double oy = Mth.lerp(pt, en.yOld, en.getY()) - en.getY();
            double oz = Mth.lerp(pt, en.zOld, en.getZ()) - en.getZ();
            AABB b = en.getBoundingBox().move(ox, oy, oz);
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            boolean behind = false;
            for (int i = 0; i < 8; i++) {
                double x = (i & 1) == 0 ? b.minX : b.maxX, y = (i & 2) == 0 ? b.minY : b.maxY, z = (i & 4) == 0 ? b.minZ : b.maxZ;
                Vector4f v = new Vector4f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1.0F);
                view.transform(v);
                proj.transform(v);
                if (v.w <= 0.05F) { behind = true; break; }
                float sx = (v.x / v.w * 0.5F + 0.5F) * sw, sy = (1.0F - (v.y / v.w * 0.5F + 0.5F)) * sh;
                minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
                minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
            }
            if (behind || maxX < 0 || maxY < 0 || minX > sw || minY > sh) continue;
            TARGETS.add(new Target(le, minX, minY, maxX, maxY, classify(le), seen));
            ON_SCREEN.add(en.getId());
        }
        // lock-on: what's under the crosshair, else the box nearest the screen centre (even through walls in HUD mode)
        boolean hud = GeistState.hudActive(mc);
        Entity pick = mc.crosshairPickEntity;
        Target best = null;
        float bestD = Float.MAX_VALUE;
        for (Target t : TARGETS) {
            if (t.e() == pick) { best = t; break; }
            if (!hud) continue;
            boolean contains = sw / 2.0F >= t.x0() && sw / 2.0F <= t.x1() && sh / 2.0F >= t.y0() && sh / 2.0F <= t.y1();
            float dx = t.cx() - sw / 2.0F, dy = t.cy() - sh / 2.0F;
            float d = contains ? 0.0F : (float) Math.sqrt(dx * dx + dy * dy);
            float limit = t.e().getId() == lockId ? 90.0F : 45.0F;          // a little "stickiness" once locked
            if (d < limit && d < bestD) { bestD = d; best = t; }
        }
        lockId = best == null ? -1 : best.e().getId();
    }

    // ---------------------------------------------------------------- brackets, lock-on and info tag

    /** The creature the scanner is locked onto (what you look at), or null. */
    @Nullable
    public static LivingEntity locked(Minecraft mc) {
        if (lockId < 0 || mc.level == null) return null;
        Entity e = mc.level.getEntity(lockId);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    static void renderTargets(GuiGraphics g, Minecraft mc, int sw, int sh, float pt, float time, boolean upgraded) {
        Font font = mc.font;
        for (Target t : TARGETS) {
            if (upgraded && t.e().getId() == lockId) continue;         // the lock-on draws its own brackets
            int x0 = Math.round(t.x0()), y0 = Math.round(t.y0()), x1 = Math.round(t.x1()), y1 = Math.round(t.y1());
            int w = x1 - x0, h = y1 - y0;
            int len = Mth.clamp(Math.min(w, h) / 4, 3, 12);
            if (t.seen()) {
                brackets(g, x0 + 1, y0 + 1, x1 + 1, y1 + 1, len, 1, 0x70000000);
                brackets(g, x0, y0, x1, y1, len, 1, 0xFF000000 | t.rgb());
            } else {                                                    // tracked through walls: a dashed box
                dashedBox(g, x0, y0, x1, y1, Holo.argb(0.6F, t.rgb()), (int) (time * 0.5F));
                brackets(g, x0, y0, x1, y1, len, 1, Holo.argb(0.9F, t.rgb()));
            }
        }
        Target lock = null;
        for (Target t : TARGETS) if (t.e().getId() == lockId) lock = t;
        if (lock == null) return;
        if (upgraded) lockOn(g, font, mc, lock, sw, sh, time);
        else simpleLabel(g, font, mc, lock);
    }

    /** The locked target: bright blue corner brackets and a small info tag beside it. */
    private static void lockOn(GuiGraphics g, Font font, Minecraft mc, Target t, int sw, int sh, float time) {
        int x0 = Math.round(t.x0()) - 3, y0 = Math.round(t.y0()) - 3, x1 = Math.round(t.x1()) + 3, y1 = Math.round(t.y1()) + 3;
        int len = Mth.clamp(Math.min(x1 - x0, y1 - y0) / 3, 4, 14);
        float pulse = 0.75F + 0.25F * Mth.sin(time * 0.35F);
        brackets(g, x0 + 1, y0 + 1, x1 + 1, y1 + 1, len, 2, 0x80000000);
        brackets(g, x0, y0, x1, y1, len, 2, Holo.argb(pulse, Holo.BLUE));
        infoTag(g, font, mc, t, x0, y0, x1, sw, sh);
    }

    /** Name, health, what it is, how far away - in plain words. The coloured strip on the left is friend or foe. */
    private static void infoTag(GuiGraphics g, Font font, Minecraft mc, Target t, int bx0, int by0, int bx1, int sw, int sh) {
        LivingEntity e = t.e();
        float S = Holo.SMALL, M = Holo.MEDIUM;
        float hp = e.getHealth(), max = Math.max(1.0F, e.getMaxHealth());
        String name = e.getDisplayName().getString();
        String health = "❤ " + num(hp) + " / " + num(max);
        String what = stanceWord(e) + " · " + Math.round(mc.player.distanceTo(e)) + " m away";
        String extra = !t.seen() ? "Behind a wall" : (e.getArmorValue() > 0 ? "Armor " + e.getArmorValue() : "");
        int lm = Holo.lineH(M), ls = Holo.lineH(S);
        int w = Math.max(Holo.textW(font, health, S), Holo.textW(font, what, S));
        w = Math.max(w, Math.min(Holo.textW(font, name, M), 150)) + 10;
        if (!extra.isEmpty()) w = Math.max(w, Holo.textW(font, extra, S) + 10);
        int h = 3 + lm + 1 + 5 + ls * 2 + (extra.isEmpty() ? 0 : ls) + 2;
        int x = bx1 + 6, y = by0;
        if (x + w > sw - 4) x = bx0 - 6 - w;
        x = Mth.clamp(x, 4, Math.max(4, sw - w - 4));
        y = Mth.clamp(y, 4, Math.max(4, sh - h - 4));
        g.fill(x, y, x + w, y + h, Holo.argb(0.66F, 0x03121B));
        g.fill(x, y, x + 2, y + h, 0xFF000000 | t.rgb());
        int tx = x + 6, ty = y + 3;
        Holo.say(g, font, Holo.fit(font, name, M, w - 9), tx, ty, M, 0xFF000000 | t.rgb());
        ty += lm + 1;
        float f = hp / max;
        Holo.bar(g, tx, ty, w - 10, 3, f, f > 0.5F ? 0xFF4CFF7A : (f > 0.25F ? 0xFFFFC14D : 0xFFFF3B3B), 0x80202A30);
        ty += 5;
        Holo.say(g, font, health, tx, ty, S, 0xFFFFB0B8);
        ty += ls;
        Holo.say(g, font, what, tx, ty, S, 0xFFE8F6FF);
        ty += ls;
        if (!extra.isEmpty()) Holo.say(g, font, extra, tx, ty, S, !t.seen() ? 0xFFFFC14D : 0xFFC8D8E8);
    }

    private static void simpleLabel(GuiGraphics g, Font font, Minecraft mc, Target t) {
        LivingEntity e = t.e();
        String name = e.getDisplayName().getString();
        String info = "❤ " + num(e.getHealth()) + " / " + num(e.getMaxHealth()) + "  " + Math.round(mc.player.distanceTo(e)) + " m";
        float S = Holo.SMALL;
        int cx = Math.round(t.cx()), ls = Holo.lineH(S), y = Math.round(t.y0()) - 2 * ls - 6;
        int bw = Math.max(Holo.textW(font, name, S), Holo.textW(font, info, S)) + 8;
        g.fill(cx - bw / 2, y - 2, cx + bw / 2, y + 2 * ls + 2, 0x90000000);
        g.fill(cx - bw / 2, y - 2, cx + bw / 2, y - 1, 0xFF000000 | t.rgb());
        Holo.sayCentered(g, font, name, cx, y + 1, S, 0xFF000000 | t.rgb());
        Holo.sayCentered(g, font, info, cx, y + 1 + ls, S, 0xFFE0E0E0);
    }

    static String num(float v) {
        return Math.abs(v - Math.round(v)) < 0.05F ? String.valueOf(Math.round(v)) : String.format("%.1f", v);
    }

    static String stanceWord(LivingEntity e) {
        return switch (stance(e)) {
            case "HOSTILE" -> "Hostile";
            case "NEUTRAL" -> "Neutral";
            case "PLAYER" -> "Player";
            default -> "Friendly";
        };
    }

    private static void brackets(GuiGraphics g, int x0, int y0, int x1, int y1, int len, int t, int c) {
        g.fill(x0, y0, x0 + len, y0 + t, c); g.fill(x0, y0, x0 + t, y0 + len, c);
        g.fill(x1 - len, y0, x1, y0 + t, c); g.fill(x1 - t, y0, x1, y0 + len, c);
        g.fill(x0, y1 - t, x0 + len, y1, c); g.fill(x0, y1 - len, x0 + t, y1, c);
        g.fill(x1 - len, y1 - t, x1, y1, c); g.fill(x1 - t, y1 - len, x1, y1, c);
    }

    /** A box of marching dashes. */
    private static void dashedBox(GuiGraphics g, int x0, int y0, int x1, int y1, int c, int phase) {
        for (int x = x0; x <= x1; x++) if (((x + phase) & 3) < 2) { g.fill(x, y0, x + 1, y0 + 1, c); g.fill(x, y1, x + 1, y1 + 1, c); }
        for (int y = y0; y <= y1; y++) if (((y + phase) & 3) < 2) { g.fill(x0, y, x0 + 1, y + 1, c); g.fill(x1, y, x1 + 1, y + 1, c); }
    }

    // ---------------------------------------------------------------- radar, nearby summary, threat alerts (HUD mode)

    /** Bearing of an entity relative to where the player looks: 0 = ahead, +90 = right, 180 = behind. */
    static float relative(Minecraft mc, Entity e, float pt) {
        Vec3 p = mc.player.getPosition(pt), q = e.getPosition(pt);
        float bearing = (float) (Mth.atan2(q.z - p.z, q.x - p.x) * (180.0D / Math.PI)) - 90.0F;
        return Mth.wrapDegrees(bearing - mc.player.getViewYRot(pt));
    }

    /** Where something is, in words: ahead, behind, to your left / right. */
    static String direction(Minecraft mc, Entity e, float pt) {
        float rel = relative(mc, e, pt), a = Math.abs(rel);
        if (a <= 35.0F) return "ahead";
        if (a >= 135.0F) return "behind you";
        return rel > 0.0F ? "to your right" : "to your left";
    }

    /** Creatures the scanner tracks within the scan radius. */
    private static List<LivingEntity> nearby(Minecraft mc) {
        List<LivingEntity> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) return out;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!SCAN.containsKey(en.getId()) || !(en instanceof LivingEntity le) || !le.isAlive()) continue;
            if (en.distanceToSqr(mc.player) <= SCAN_RADIUS * SCAN_RADIUS) out.add(le);
        }
        return out;
    }

    /** How many hostiles, friendly creatures and players are within the scan radius. */
    static int[] counts(Minecraft mc) {
        int[] c = new int[3];
        for (LivingEntity le : nearby(mc)) {
            int k = classify(le);
            c[k == Holo.RED ? 0 : k == Holo.BLUE ? 2 : 1]++;
        }
        return c;
    }

    /** The closest hostile within the scan radius, or null. */
    @Nullable
    static LivingEntity nearestThreat(Minecraft mc) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity le : nearby(mc)) {
            if (classify(le) != Holo.RED) continue;
            double d = le.distanceToSqr(mc.player);
            if (d < bd) { bd = d; best = le; }
        }
        return best;
    }

    /** Hostiles (and the locked target) for the compass: {bearing relative to the view, distance, colour}. */
    static List<float[]> blips(Minecraft mc, float pt) {
        List<float[]> out = new ArrayList<>();
        for (LivingEntity le : nearby(mc)) {
            boolean lock = le.getId() == lockId;
            if (!lock && classify(le) != Holo.RED) continue;
            out.add(new float[]{relative(mc, le, pt), mc.player.distanceTo(le), lock ? Holo.BLUE : Holo.RED});
        }
        return out;
    }

    public static float scanRadius() {
        return (float) SCAN_RADIUS;
    }

    /** The radar: you in the middle (pointing where you look), rings at 5, 10 and 15 m, a dot per creature. */
    static void renderRadar(GuiGraphics g, Minecraft mc, float cx, float cy, float r, float pt, float time, float alpha) {
        int ring = Holo.argb(0.75F * alpha, Holo.NEON), dim = Holo.argb(0.22F * alpha, Holo.NEON);
        Holo.arc(g, cx, cy, r, 0, 360, ring);
        Holo.arc(g, cx, cy, r * 2 / 3, 0, 360, dim);
        Holo.arc(g, cx, cy, r / 3, 0, 360, dim);
        Holo.hline(g, Math.round(cx - r), Math.round(cx + r), Math.round(cy), dim);
        Holo.vline(g, Math.round(cx), Math.round(cy - r), Math.round(cy + r), dim);
        double sweep = Math.toRadians(time * 9.0F);                      // one soft sweep line
        Holo.line(g, Math.round(cx), Math.round(cy), Math.round((float) (cx + Math.sin(sweep) * r)),
                Math.round((float) (cy - Math.cos(sweep) * r)), Holo.argb(0.3F * alpha, Holo.NEON));
        for (LivingEntity le : nearby(mc)) {
            float d = mc.player.distanceTo(le);
            float rel = (float) Math.toRadians(relative(mc, le, pt));
            int x = Math.round(cx + (float) Math.sin(rel) * d / (float) SCAN_RADIUS * r);
            int y = Math.round(cy - (float) Math.cos(rel) * d / (float) SCAN_RADIUS * r);
            int c = Holo.argb(alpha, classify(le));
            g.fill(x - 1, y - 1, x + 2, y + 2, c);
            if (le.getId() == lockId) Holo.arc(g, x + 0.5F, y + 0.5F, 3.5F, 0, 360, Holo.argb(0.9F * alpha, Holo.BLUE));
        }
        int you = Holo.argb(alpha, 0xFFFFFF);                            // you: an arrow pointing up (= where you look)
        Holo.line(g, Math.round(cx), Math.round(cy - 3), Math.round(cx - 2), Math.round(cy + 2), you);
        Holo.line(g, Math.round(cx), Math.round(cy - 3), Math.round(cx + 2), Math.round(cy + 2), you);
    }

    /** Red arrows at the screen edge pointing to hostiles behind you or hidden, and a warning when one is close behind. */
    static void renderThreats(GuiGraphics g, Minecraft mc, int sw, int sh, float pt, float time) {
        Font font = mc.font;
        LivingEntity closeBehind = null;
        float cx = sw / 2.0F, cy = sh / 2.0F, rx = sw * 0.42F, ry = sh * 0.38F;
        float flash = 0.6F + 0.4F * Mth.sin(time * 0.6F);
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!THREATS.contains(en.getId()) || !(en instanceof LivingEntity le)) continue;
            boolean visible = ON_SCREEN.contains(en.getId()) && SCAN.getOrDefault(en.getId(), false);
            if (visible) continue;
            float rel = relative(mc, en, pt), d = mc.player.distanceTo(en);
            if (Math.abs(rel) > 100.0F && d < 8.0F && (closeBehind == null || d < mc.player.distanceTo(closeBehind))) closeBehind = le;
            double a = Math.toRadians(rel);
            float x = cx + (float) Math.sin(a) * rx, y = cy - (float) Math.cos(a) * ry;
            int c = Holo.argb(flash, Holo.RED);
            double ox = Math.sin(a), oy = -Math.cos(a), px = -oy, py = ox;  // a chevron pointing outward
            int tx = Math.round((float) (x + ox * 6)), ty = Math.round((float) (y + oy * 6));
            Holo.line(g, Math.round((float) (x + px * 6)), Math.round((float) (y + py * 6)), tx, ty, c);
            Holo.line(g, Math.round((float) (x - px * 6)), Math.round((float) (y - py * 6)), tx, ty, c);
            Holo.line(g, Math.round((float) (x + px * 4 - ox * 3)), Math.round((float) (y + py * 4 - oy * 3)), Math.round((float) (x + ox * 2)), Math.round((float) (y + oy * 2)), c);
            Holo.line(g, Math.round((float) (x - px * 4 - ox * 3)), Math.round((float) (y - py * 4 - oy * 3)), Math.round((float) (x + ox * 2)), Math.round((float) (y + oy * 2)), c);
            Holo.sayCentered(g, font, Math.round(d) + " m", x, y - (float) oy * 11 - 3, Holo.SMALL, c);
        }
        if (closeBehind != null) {
            String s = "⚠ " + closeBehind.getDisplayName().getString() + " behind you · " + Math.round(mc.player.distanceTo(closeBehind)) + " m";
            float M = Holo.MEDIUM;
            int w = Holo.textW(font, s, M), y = Math.round(cy) - 30 - Holo.lineH(M);
            g.fill(Math.round(cx - w / 2.0F - 5), y - 3, Math.round(cx + w / 2.0F + 5), y + Holo.lineH(M) + 2, Holo.argb(0.55F * flash, 0x400000));
            Holo.sayCentered(g, font, s, cx, y, M, Holo.argb(flash, 0xFF6B6B));
        }
    }
}
