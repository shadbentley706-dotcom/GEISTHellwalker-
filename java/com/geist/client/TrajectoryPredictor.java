package com.geist.client;

import com.geist.GeistMod;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
import java.util.List;
import java.util.Optional;

/**
 * Trajectory (v11). Hold a bow, crossbow, trident, ender pearl, snowball, egg, splash or lingering potion,
 * or a bottle o' enchanting: a clean arc shows where it will go - Minecraft's own launch speed, gravity,
 * drag and your momentum - ending on the block it lands on, or boxing the creature it would hit (the arc
 * turns green for friendly, red for hostile, blue for a player). One short line under the crosshair says
 * what will happen: "Hits Zombie - 18 m" or "Lands 24 m away - 1.2 s".
 * <p>Aim help: lock onto a creature (look at it with the HUD on) and a gold diamond shows where to point
 * so the shot lands on it, allowing for drop and for the creature walking; it turns green on target.
 * <p>Light on performance: creatures are gathered once per tick instead of on every step of the flight,
 * the flight is only worked out again when your aim, position or draw changes, and it stops at unloaded
 * chunks.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class TrajectoryPredictor {
    private static final int MAX_TICKS = 200;
    private static final double ENTITY_RANGE = 96.0D;

    /** How a projectile is launched (speed, pitch offset - potions are lobbed 20 degrees up) and flies. */
    private record Shot(String name, float speed, float pitchOffset, double gravity, double drag, double waterDrag,
                        boolean momentum, int color, boolean ready, float power, String hint, int bounces) {
        Shot(String name, float speed, float pitchOffset, double gravity, double drag, double waterDrag,
             boolean momentum, int color, boolean ready, float power, String hint) {
            this(name, speed, pitchOffset, gravity, drag, waterDrag, momentum, color, ready, power, hint, 0);
        }
    }

    private record Path(List<Vec3> points, @Nullable Vec3 end, @Nullable Direction face, @Nullable BlockPos block,
                        @Nullable LivingEntity hit, int ticks) {}

    @Nullable private static Shot shot;
    @Nullable private static Path path;
    private static final List<LivingEntity> CANDIDATES = new ArrayList<>();
    private static long candidatesTick = Long.MIN_VALUE;
    private static final double[] KEY = new double[11];
    // aim help
    @Nullable private static LivingEntity aimTarget;
    @Nullable private static Vec3 aimDir;
    private static int aimTicks, aimTargetId = -1;
    private static boolean aimOnScreen;
    private static float aimX, aimY;

    private TrajectoryPredictor() {}

    @Nullable
    private static Shot shot(LocalPlayer p) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(hand);
            if (s.isEmpty()) continue;
            Item item = s.getItem();
            boolean using = p.isUsingItem() && p.getUsedItemHand() == hand;
            if (item instanceof com.geist.item.ZeroLightItem) {                  // light arrows: faster, flatter; ricochet shows its bounces
                float power = using ? com.geist.item.ZeroLightItem.power(p.getTicksUsingItem()) : 1.0F;
                boolean ricochet = com.geist.item.ZeroLightItem.mode(s) == com.geist.item.ZeroLightItem.RICOCHET;
                return new Shot("Zero Light", power * com.geist.item.ZeroLightItem.SPEED, 0.0F, com.geist.item.ZeroLightItem.GRAVITY,
                        0.99D, 0.6D, true, ricochet ? 0xC77DFF : Holo.NEON, using && power >= 0.1F, power, "Draw to fire",
                        ricochet ? com.geist.item.ZeroLightItem.MAX_BOUNCES : 0);
            }
            if (item instanceof BowItem) {
                float power = using ? BowItem.getPowerForTime(p.getTicksUsingItem()) : 1.0F;
                return new Shot("Bow", power * 3.0F, 0.0F, 0.05D, 0.99D, 0.6D, true, Holo.NEON, using && power >= 0.1F, power,
                        "Draw the bow to fire");
            }
            if (item instanceof CrossbowItem) {
                if (CrossbowItem.containsChargedProjectile(s, Items.FIREWORK_ROCKET)) continue;    // rockets fly straight
                return new Shot("Crossbow", 3.15F, 0.0F, 0.05D, 0.99D, 0.6D, false, Holo.NEON, CrossbowItem.isCharged(s), 1.0F,
                        "Load the crossbow to fire");
            }
            if (item instanceof TridentItem && EnchantmentHelper.getRiptide(s) == 0) {
                return new Shot("Trident", 2.5F, 0.0F, 0.05D, 0.99D, 0.99D, true, 0x4FE8D8, using && p.getTicksUsingItem() >= 10, 1.0F,
                        using ? "Keep holding..." : "Hold right-click to throw");
            }
            if (item instanceof EnderpearlItem) {
                return new Shot("Ender pearl", 1.5F, 0.0F, 0.03D, 0.99D, 0.8D, true, 0xC77DFF, !p.getCooldowns().isOnCooldown(item), 1.0F,
                        "Recharging...");
            }
            if (item instanceof SnowballItem) return new Shot("Snowball", 1.5F, 0.0F, 0.03D, 0.99D, 0.8D, true, 0xE8F6FF, true, 1.0F, "");
            if (item instanceof EggItem) return new Shot("Egg", 1.5F, 0.0F, 0.03D, 0.99D, 0.8D, true, 0xF2E6C8, true, 1.0F, "");
            if (item instanceof ThrowablePotionItem) return new Shot("Potion", 0.5F, -20.0F, 0.05D, 0.99D, 0.8D, true, 0xFF7BE0, true, 1.0F, "");
            if (item instanceof ExperienceBottleItem) return new Shot("XP bottle", 0.7F, -20.0F, 0.07D, 0.99D, 0.8D, true, 0x9CFF5A, true, 1.0F, "");
        }
        return null;
    }

    /** The launch velocity for a given aim, exactly as Minecraft works it out (plus your momentum). */
    private static Vec3 launch(LocalPlayer p, Shot s, float xRot, float yRot) {
        float xr = xRot * Mth.DEG_TO_RAD, yr = yRot * Mth.DEG_TO_RAD, xo = (xRot + s.pitchOffset()) * Mth.DEG_TO_RAD;
        Vec3 v = new Vec3(-Mth.sin(yr) * Mth.cos(xr), -Mth.sin(xo), Mth.cos(yr) * Mth.cos(xr)).normalize().scale(s.speed());
        if (s.momentum()) {
            Vec3 m = p.getDeltaMovement();
            v = v.add(m.x, p.onGround() ? 0.0D : m.y, m.z);
        }
        return v;
    }

    /** Creatures a shot could hit: gathered once per tick (the old version searched again on every step). */
    private static void gatherCandidates(Minecraft mc, LocalPlayer p) {
        CANDIDATES.clear();
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(ENTITY_RANGE),
                en -> en instanceof LivingEntity && en.isAlive() && en.isPickable() && !en.isSpectator())) {
            CANDIDATES.add((LivingEntity) e);
        }
    }

    /** Step the projectile tick by tick, as Minecraft moves it, until it hits a block or a creature. */
    private static Path simulate(Minecraft mc, LocalPlayer p, Shot s, Vec3 start, Vec3 vel) {
        List<Vec3> pts = new ArrayList<>();
        pts.add(start);
        Vec3 pos = start;
        int bouncesLeft = s.bounces();
        for (int i = 0; i < MAX_TICKS; i++) {
            Vec3 next = pos.add(vel);
            BlockHitResult bh = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            boolean blocked = bh.getType() != HitResult.Type.MISS;
            Vec3 end = blocked ? bh.getLocation() : next;
            LivingEntity hit = null;
            Vec3 hitAt = null;
            double best = Double.MAX_VALUE;
            AABB seg = new AABB(pos, end).inflate(0.3D);
            for (LivingEntity e : CANDIDATES) {
                AABB box = e.getBoundingBox().inflate(0.3D);
                if (!box.intersects(seg)) continue;
                Optional<Vec3> o = box.clip(pos, end);
                if (o.isPresent() && pos.distanceToSqr(o.get()) < best) {
                    best = pos.distanceToSqr(o.get());
                    hit = e;
                    hitAt = o.get();
                }
            }
            if (hit != null) {
                pts.add(hitAt);
                return new Path(pts, hitAt, null, null, hit, i + 1);
            }
            if (blocked && bouncesLeft > 0 && vel.lengthSqr() > 0.25D) {          // a ricochet: reflect off the face and carry on
                bouncesLeft--;
                pts.add(end);
                net.minecraft.core.Vec3i n = bh.getDirection().getNormal();
                Vec3 normal = new Vec3(n.getX(), n.getY(), n.getZ());
                vel = vel.subtract(normal.scale(2.0D * vel.dot(normal))).scale(com.geist.item.ZeroLightItem.RICOCHET_SPEED);
                pos = end.add(normal.scale(0.05D));
                vel = vel.scale(s.drag()).subtract(0.0D, s.gravity(), 0.0D);    // the arrow waits out the rest of that tick at the wall
                continue;
            }
            if (blocked) {
                pts.add(end);
                return new Path(pts, end, bh.getDirection(), bh.getBlockPos(), null, i + 1);
            }
            pts.add(next);
            pos = next;
            BlockPos bp = BlockPos.containing(pos);
            if (!mc.level.hasChunkAt(bp) || pos.y < mc.level.getMinBuildHeight() - 32) break;     // left the loaded world
            boolean water = mc.level.getFluidState(bp).is(FluidTags.WATER);
            vel = vel.scale(water ? s.waterDrag() : s.drag()).subtract(0.0D, s.gravity(), 0.0D);
        }
        return new Path(pts, null, null, null, null, MAX_TICKS);
    }

    // ---------------------------------------------------------------- aim help (worked out once per tick)

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        aimDir = null;
        aimTarget = null;
        if (p == null || mc.level == null || !GeistState.module(mc, GeistState.TRAJECTORY)) return;
        Shot s = shot(p);
        LivingEntity target = s == null ? null : EntityScanner.locked(mc);
        if (target == null) {
            aimTargetId = -1;
            return;
        }
        if (target.getId() != aimTargetId) {
            aimTargetId = target.getId();
            aimTicks = 0;
        }
        aimTarget = target;
        Vec3 start = p.getEyePosition().subtract(0.0D, 0.1D, 0.0D);
        Vec3 walk = new Vec3(target.getX() - target.xOld, 0.0D, target.getZ() - target.zOld);   // its speed, blocks per tick
        Vec3 aimAt = target.getBoundingBox().getCenter().add(walk.scale(aimTicks));             // lead a walking target
        double dx = aimAt.x - start.x, dz = aimAt.z - start.z, dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.5D) {                                                  // point blank: no aim help needed
            aimTarget = null;
            return;
        }
        float yaw = (float) Math.toDegrees(Mth.atan2(dz, dx)) - 90.0F;
        int[] ticks = {0};
        Float pitch = solvePitch(p, s, start, yaw, dx / dist, dz / dist, dist, aimAt.y, ticks);
        if (pitch == null) {
            aimTicks = 0;
            return;
        }
        aimTicks = Math.min(ticks[0], 60);
        float xr = pitch * Mth.DEG_TO_RAD, yr = yaw * Mth.DEG_TO_RAD;
        aimDir = new Vec3(-Mth.sin(yr) * Mth.cos(xr), -Mth.sin(xr), Mth.cos(yr) * Mth.cos(xr));
    }

    /**
     * The flattest aim (pitch) that brings the shot to the target's height at its distance, or null if it
     * cannot reach. Scans from looking down to looking up, then narrows in on the first angle that reaches.
     */
    @Nullable
    private static Float solvePitch(LocalPlayer p, Shot s, Vec3 start, float yaw, double ux, double uz, double dist, double ty, int[] ticks) {
        float prev = 85.0F;
        for (float pitch = 82.5F; pitch >= -85.0F; pitch -= 2.5F) {
            double h = heightAt(p, s, start, pitch, yaw, ux, uz, dist, ty - 64.0D, ticks);
            if (!Double.isNaN(h) && h >= ty) {
                float lo = prev, hi = pitch;                             // lo is too low (or falls short), hi reaches
                for (int k = 0; k < 14; k++) {
                    float mid = (lo + hi) * 0.5F;
                    double hm = heightAt(p, s, start, mid, yaw, ux, uz, dist, ty - 64.0D, ticks);
                    if (!Double.isNaN(hm) && hm >= ty) hi = mid;
                    else lo = mid;
                }
                heightAt(p, s, start, hi, yaw, ux, uz, dist, ty - 64.0D, ticks);   // flight time for the chosen aim
                return hi;
            }
            prev = pitch;
        }
        return null;
    }

    /** Fly (ignoring obstacles) until the shot has covered {@code dist} toward the target; its height there, or NaN. */
    private static double heightAt(LocalPlayer p, Shot s, Vec3 start, float pitch, float yaw, double ux, double uz,
                                   double dist, double floorY, int[] ticks) {
        Vec3 v0 = launch(p, s, pitch, yaw);
        double x = start.x, y = start.y, z = start.z, vx = v0.x, vy = v0.y, vz = v0.z, along = 0.0D;
        for (int i = 0; i < MAX_TICKS; i++) {
            double nx = x + vx, ny = y + vy, nz = z + vz;
            double nAlong = (nx - start.x) * ux + (nz - start.z) * uz;
            if (nAlong >= dist) {
                double f = (dist - along) / Math.max(1.0E-6D, nAlong - along);
                ticks[0] = i + 1;
                return y + (ny - y) * f;
            }
            if (ny < floorY && vy < 0.0D) return Double.NaN;
            x = nx; y = ny; z = nz; along = nAlong;
            vx *= s.drag(); vy = vy * s.drag() - s.gravity(); vz *= s.drag();
        }
        return Double.NaN;
    }

    // ---------------------------------------------------------------- the arc (every frame, only re-worked when needed)

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        aimOnScreen = false;
        Shot s = p == null || mc.level == null || !GeistState.module(mc, GeistState.TRAJECTORY) ? null : shot(p);
        shot = s;
        if (s == null) {
            path = null;
            return;
        }
        float pt = event.getPartialTick();
        long now = mc.level.getGameTime();
        if (candidatesTick != now) {
            gatherCandidates(mc, p);
            candidatesTick = now;
        }
        Vec3 start = p.getEyePosition(pt).subtract(0.0D, 0.1D, 0.0D);
        float xRot = p.getViewXRot(pt), yRot = p.getViewYRot(pt);
        Vec3 m = p.getDeltaMovement();
        double[] key = {start.x, start.y, start.z, xRot, yRot, s.speed(), m.x, m.y, m.z, now, s.name().hashCode()};
        boolean same = path != null;
        for (int i = 0; i < key.length && same; i++) same = Math.abs(key[i] - KEY[i]) < 1.0E-5D;
        if (!same) {
            path = simulate(mc, p, s, start, launch(p, s, xRot, yRot));
            System.arraycopy(key, 0, KEY, 0, key.length);
        }
        draw(event, mc, s, path, pt);
        project(event, mc, pt);
    }

    private static void draw(RenderLevelStageEvent event, Minecraft mc, Shot s, Path path, float pt) {
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float time = mc.level.getGameTime() + pt;
        float base = s.ready() ? 1.0F : 0.4F;
        int col = path.hit() != null ? EntityScanner.classify(path.hit()) : s.color();
        List<Vec3> pts = path.points();
        Vec3 eye = mc.player.getEyePosition(pt);
        // a faint copy that shows through walls, then the arc itself
        for (int pass = 0; pass < 2; pass++) {
            BufferBuilder b = WorldLines.begin(cam, pass == 0, pass == 0 ? 1.5F : 2.5F);
            for (int i = 0; i + 1 < pts.size(); i++) {
                Vec3 a = pts.get(i), c = pts.get(i + 1);
                double d = a.distanceTo(eye);
                if (d < 2.0D) continue;                                         // keep it out of your face
                float fadeIn = (float) Mth.clamp((d - 2.0D) / 3.0D, 0.0D, 1.0D);
                WorldLines.line(ps, b, a.x, a.y, a.z, c.x, c.y, c.z, col, (pass == 0 ? 0.16F : 0.85F) * base * fadeIn);
            }
            WorldLines.end();
        }
        // where it ends: a box on the creature, or the face of the block it lands on with a ring on the spot
        BufferBuilder b = WorldLines.begin(cam, true, 2.5F);
        if (path.hit() != null) {
            WorldLines.box(ps, b, lerpedBox(path.hit(), pt).inflate(0.1D), col, 0.95F * base);
        } else if (path.end() != null && path.face() != null && path.block() != null) {
            Direction f = path.face();
            Vec3 e = path.end();
            AABB blk = new AABB(path.block());
            double o = 0.01D;
            Vec3[] q = switch (f.getAxis()) {
                case Y -> new Vec3[]{new Vec3(blk.minX, e.y + (f == Direction.UP ? o : -o), blk.minZ), new Vec3(blk.maxX, e.y + (f == Direction.UP ? o : -o), blk.minZ),
                        new Vec3(blk.maxX, e.y + (f == Direction.UP ? o : -o), blk.maxZ), new Vec3(blk.minX, e.y + (f == Direction.UP ? o : -o), blk.maxZ)};
                case X -> new Vec3[]{new Vec3(e.x + (f == Direction.EAST ? o : -o), blk.minY, blk.minZ), new Vec3(e.x + (f == Direction.EAST ? o : -o), blk.maxY, blk.minZ),
                        new Vec3(e.x + (f == Direction.EAST ? o : -o), blk.maxY, blk.maxZ), new Vec3(e.x + (f == Direction.EAST ? o : -o), blk.minY, blk.maxZ)};
                case Z -> new Vec3[]{new Vec3(blk.minX, blk.minY, e.z + (f == Direction.SOUTH ? o : -o)), new Vec3(blk.maxX, blk.minY, e.z + (f == Direction.SOUTH ? o : -o)),
                        new Vec3(blk.maxX, blk.maxY, e.z + (f == Direction.SOUTH ? o : -o)), new Vec3(blk.minX, blk.maxY, e.z + (f == Direction.SOUTH ? o : -o))};
            };
            for (int i = 0; i < 4; i++) {
                Vec3 a = q[i], c = q[(i + 1) % 4];
                WorldLines.line(ps, b, a.x, a.y, a.z, c.x, c.y, c.z, col, 0.5F * base);
            }
            Vec3 u = f.getAxis() == Direction.Axis.X ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 v = f.getAxis() == Direction.Axis.Y ? new Vec3(0, 0, 1) : (f.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0));
            Vec3 c = e.add(Vec3.atLowerCornerOf(f.getNormal()).scale(o));
            double r = 0.3D + 0.05D * Math.sin(time * 0.4D);
            for (int k = 0; k < 20; k++) {
                double a0 = k * Math.PI * 2 / 20, a1 = (k + 1) * Math.PI * 2 / 20;
                Vec3 p0 = c.add(u.scale(Math.cos(a0) * r)).add(v.scale(Math.sin(a0) * r));
                Vec3 p1 = c.add(u.scale(Math.cos(a1) * r)).add(v.scale(Math.sin(a1) * r));
                WorldLines.line(ps, b, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, Holo.WHITE, 0.95F * base);
            }
        }
        WorldLines.end();
    }

    private static AABB lerpedBox(Entity e, float pt) {
        return e.getBoundingBox().move(Mth.lerp(pt, e.xOld, e.getX()) - e.getX(), Mth.lerp(pt, e.yOld, e.getY()) - e.getY(),
                Mth.lerp(pt, e.zOld, e.getZ()) - e.getZ());
    }

    /** Where the aim-help direction lands on the screen. */
    private static void project(RenderLevelStageEvent event, Minecraft mc, float pt) {
        Vec3 dir = aimDir;
        if (dir == null) return;
        Vec3 rel = mc.player.getEyePosition(pt).add(dir.scale(32.0D)).subtract(event.getCamera().getPosition());
        Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
        Matrix4f proj = new Matrix4f(event.getProjectionMatrix());
        Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
        view.transform(v);
        proj.transform(v);
        if (v.w <= 0.05F) return;
        aimX = (v.x / v.w * 0.5F + 0.5F) * mc.getWindow().getGuiScaledWidth();
        aimY = (1.0F - (v.y / v.w * 0.5F + 0.5F)) * mc.getWindow().getGuiScaledHeight();
        aimOnScreen = true;
    }

    // ---------------------------------------------------------------- the readout under the crosshair

    static void renderHud(GuiGraphics g, Minecraft mc, int sw, int sh, float pt, float time) {
        Shot s = shot;
        Path pa = path;
        if (s == null || pa == null || mc.player == null) return;
        Font font = mc.font;
        float S = Holo.SMALL, Md = Holo.MEDIUM;
        LivingEntity target = aimTarget;
        boolean onTarget = target != null && pa.hit() == target;
        if (target != null && aimDir != null && aimOnScreen) aimMarker(g, font, sw, sh, onTarget);

        // line 1: what you hold (and, for a bow, how far it is drawn)
        boolean bow = s.name().equals("Bow");
        String head = s.name() + (bow ? "  " + Math.round(s.power() * 100) + "%" : "");
        // line 2: what will happen
        String out;
        int oc;
        if (pa.hit() != null) {
            out = "Hits " + pa.hit().getDisplayName().getString() + " · " + Math.round(mc.player.distanceTo(pa.hit())) + " m";
            oc = EntityScanner.classify(pa.hit());
        } else if (pa.end() != null) {
            double range = Math.sqrt(Math.pow(pa.end().x - mc.player.getX(), 2) + Math.pow(pa.end().z - mc.player.getZ(), 2));
            out = (s.name().equals("Ender pearl") ? "Teleports you " : "Lands ") + Math.round(range) + " m away · "
                    + String.format("%.1f", pa.ticks() / 20.0F) + " s";
            oc = 0xFFFFFF;
        } else {
            out = "Flies out of range";
            oc = Holo.AMBER;
        }
        // line 3: a hint, or how the aim help is doing
        String hint = "";
        int hc = Holo.NEON;
        if (!s.ready() && !s.hint().isEmpty()) {
            hint = s.hint();
        } else if (target != null) {
            String n = target.getDisplayName().getString();
            if (onTarget) { hint = "On target!"; hc = Holo.GREEN; }
            else if (aimDir == null) { hint = n + " is out of range"; hc = Holo.AMBER; }
            else { hint = "Aim at the ◆ to hit " + n; hc = 0xFFD54A; }
        }
        int lm = Holo.lineH(Md), ls = Holo.lineH(S);
        int barW = bow ? 34 : 0;
        int w = Math.max(Holo.textW(font, head, Md) + (bow ? barW + 6 : 0), Math.max(Holo.textW(font, out, S), Holo.textW(font, hint, S))) + 10;
        int h = 3 + lm + ls + (hint.isEmpty() ? 0 : ls) + 2;
        int x = Math.round(sw / 2.0F - w / 2.0F), y = Math.round(sh / 2.0F) + 14;
        float alpha = s.ready() ? 1.0F : 0.75F;
        g.fill(x, y, x + w, y + h, Holo.argb(0.55F, 0x03121B));
        g.fill(x, y, x + w, y + 1, Holo.argb(0.9F * alpha, pa.hit() != null ? oc : s.color()));
        int ty = y + 3;
        float hx = sw / 2.0F - (Holo.textW(font, head, Md) + (bow ? barW + 6 : 0)) / 2.0F;
        Holo.say(g, font, head, hx, ty, Md, Holo.argb(alpha, s.color()));
        if (bow) {                                                          // draw power: green when fully drawn
            int bx = Math.round(hx + Holo.textW(font, head, Md) + 6), by = ty + lm / 2 - 1;
            boolean full = s.power() >= 1.0F;
            Holo.bar(g, bx, by, barW, 3, s.power(), full ? 0xFF4CFF7A : 0xFFFFC14D, 0x60202A30);
        }
        ty += lm;
        Holo.sayCentered(g, font, out, sw / 2.0F, ty, S, Holo.argb(s.ready() ? 1.0F : 0.7F, oc));
        ty += ls;
        if (!hint.isEmpty()) Holo.sayCentered(g, font, hint, sw / 2.0F, ty, S, Holo.argb(0.95F, hc));
    }

    /** The aim-help diamond, with a dotted guide from the crosshair while you are off target. */
    private static void aimMarker(GuiGraphics g, Font font, int sw, int sh, boolean onTarget) {
        int col = onTarget ? 0xFF4CFF7A : 0xFFFFD54A;
        float cx = sw / 2.0F, cy = sh / 2.0F, dx = aimX - cx, dy = aimY - cy;
        float d = Mth.sqrt(dx * dx + dy * dy);
        if (!onTarget && d > 10.0F) {
            for (float t = 7.0F; t < d - 6.0F; t += 4.0F) {
                int px = Math.round(cx + dx / d * t), py = Math.round(cy + dy / d * t);
                g.fill(px, py, px + 1, py + 1, Holo.argb(0.6F, 0xFFD54A));
            }
        }
        int x = Math.round(aimX), y = Math.round(aimY), r = onTarget ? 6 : 4;
        Holo.line(g, x, y - r, x + r, y, col);
        Holo.line(g, x + r, y, x, y + r, col);
        Holo.line(g, x, y + r, x - r, y, col);
        Holo.line(g, x - r, y, x, y - r, col);
        g.fill(x, y, x + 1, y + 1, col);
        if (!onTarget) Holo.sayCentered(g, font, "AIM", x + 0.5F, y - r - 1 - Holo.lineH(Holo.SMALL), Holo.SMALL, col);
    }
}
