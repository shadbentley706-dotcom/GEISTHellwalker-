package com.geist.client.gauntlet;

import com.geist.GeistMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * How a carried or thrown mob looks (for everyone nearby).
 *
 * Carried: drawn lying across the wearer's raised hands, belly down and head forward (squat mobs stay upright),
 * wriggling; the wind-up draws it back over the throwing shoulder with the nose tipped up. It is drawn where the
 * hands are this very frame, so it never lags behind the wearer.
 * Thrown: a tall mob flies nose first along its path in a tight football spiral; a squat one tumbles end over end;
 * either leaves a corkscrew of air behind it.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class GauntletCarryRender {
    /** Held mob id -> the wearer's id. */
    private static final Map<Integer, Integer> CARRIED = new HashMap<>();
    private static final Map<Integer, Flight> FLIGHTS = new HashMap<>();
    private static final Set<Integer> PUSHED = new HashSet<>();

    private static final class Flight {
        final long start;
        final float sign;
        Vec3 dir;

        Flight(long start, float sign) {
            this.start = start;
            this.sign = sign;
        }
    }

    private GauntletCarryRender() {}

    public static void lifted(int mob, int carrier) {
        CARRIED.put(mob, carrier);
        FLIGHTS.remove(mob);
    }

    public static void thrown(int mob, long now, int side) {
        CARRIED.remove(mob);
        FLIGHTS.put(mob, new Flight(now, side == 0 ? 1.0F : -1.0F));
    }

    public static void dropped(int mob) {
        CARRIED.remove(mob);
    }

    public static void clear() {
        CARRIED.clear();
        FLIGHTS.clear();
        PUSHED.clear();
    }

    private static boolean tall(LivingEntity e) {
        return e.getBbHeight() > e.getBbWidth() * 1.25F;
    }

    /** Lowest priority: if another mod cancels the render, this never pushes (and Post would never pop). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        int id = e.getId();
        Integer carrier = CARRIED.get(id);
        Flight fl = carrier == null ? FLIGHTS.get(id) : null;
        if (carrier == null && fl == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick();
        float t = mc.level.getGameTime() + pt;
        float h = e.getBbHeight();
        PoseStack ps = event.getPoseStack();
        if (carrier != null) {
            Entity c = mc.level.getEntity(carrier);
            if (!(c instanceof LivingEntity cl)) return;
            held(ps, e, cl, GauntletClient.peek(carrier), pt, t);
        } else {
            Vec3 move = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
            if (move.lengthSqr() > 0.004D) fl.dir = move.normalize();
            if (fl.dir == null) return;
            float age = t - fl.start;
            Vector dir = new Vector(fl.dir);
            Quaternionf q;
            if (tall(e)) {                                            // the spiral: nose along the path, spinning
                q = new Quaternionf().rotationAxis(age * 0.75F * fl.sign, dir.x, dir.y, dir.z)
                        .mul(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, dir.x, dir.y, dir.z));
            } else {                                                  // end over end
                Vector side = dir.sideways();
                q = new Quaternionf().rotationAxis(age * 0.45F, side.x, side.y, side.z);
            }
            ps.pushPose();
            PUSHED.add(id);
            ps.translate(0.0D, h * 0.5D, 0.0D);
            ps.mulPose(q);
            ps.translate(0.0D, -h * 0.5D, 0.0D);
        }
    }

    /** Puts the pose stack where the wearer's hands hold the mob this frame. */
    private static void held(PoseStack ps, LivingEntity e, LivingEntity c, GauntletClient.Anim a, float pt, float t) {
        float w = e.getBbWidth(), h = e.getBbHeight();
        boolean tall = tall(e);
        float k = a == null ? 0.0F : GauntletAnimator.ease(GauntletAnimator.chargeK(a, t));
        float lift = a == null ? 1.0F : GauntletAnimator.ease((t - a.carryStart) / 6.0F);
        float sideSign = a != null && a.throwSide == 1 ? -1.0F : 1.0F;
        float yaw = Mth.rotLerp(pt, c.yBodyRotO, c.yBodyRot) * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);                  // the wearer's forward
        float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);                 // and right
        double thick = tall ? w : h;
        double up = c.getBbHeight() + 0.08D + thick * 0.5D - (1.0D - lift) * 0.7D + 0.12D * k;
        double fwd = 0.22D - 0.55D * k, right = 0.32D * k * sideSign;
        double cx = Mth.lerp(pt, c.xo, c.getX()) + fx * fwd + rx * right;
        double cy = Mth.lerp(pt, c.yo, c.getY()) + up;
        double cz = Mth.lerp(pt, c.zo, c.getZ()) + fz * fwd + rz * right;
        double ex = Mth.lerp(pt, e.xo, e.getX()), ey = Mth.lerp(pt, e.yo, e.getY()), ez = Mth.lerp(pt, e.zo, e.getZ());

        float ax = fz, az = -fx;                                      // up x forward: the tipping axis
        float wriggle = 0.12F * Mth.sin(t * 0.9F) * (1.0F - k);
        Quaternionf q;
        if (tall) {                                                   // lying along the arms: belly down, head forward
            float mobYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
            float tilt = Mth.HALF_PI * lift - 0.66F * k;             // the wind-up tips the nose up
            q = new Quaternionf().rotationAxis(tilt, ax, 0.0F, az).rotateY(mobYaw - yaw);
        } else {                                                      // squat: upright, tipped back for the throw
            q = new Quaternionf().rotationAxis(-0.5F * k, ax, 0.0F, az);
        }
        q.premul(new Quaternionf().rotationAxis(wriggle, fx, 0.0F, fz));
        ps.pushPose();
        PUSHED.add(e.getId());
        ps.translate(cx - ex, cy - ey, cz - ez);                      // to the centre of the held mob
        ps.mulPose(q);
        ps.translate(0.0D, -h * 0.5D, 0.0D);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    /** Ends flights that have landed, and leaves the corkscrew wake behind the ones still in the air. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        long now = level.getGameTime();
        CARRIED.entrySet().removeIf(en -> level.getEntity(en.getKey()) == null || level.getEntity(en.getValue()) == null);
        Iterator<Map.Entry<Integer, Flight>> it = FLIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Flight> en = it.next();
            Flight fl = en.getValue();
            long age = now - fl.start;
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof LivingEntity le) || !le.isAlive() || age > 100L || (age > 3L && (e.onGround() || e.isInWater()))) {
                it.remove();
                continue;
            }
            if (fl.dir == null || age > 60L) continue;
            Vector dir = new Vector(fl.dir), side = dir.sideways(), up = dir.cross(side);
            double r = le.getBbWidth() * 0.6D + 0.15D;
            double cx = e.getX(), cy = e.getY() + le.getBbHeight() * 0.5D, cz = e.getZ();
            for (int i = 0; i < 2; i++) {
                double ang = age * 0.75D * fl.sign + i * Math.PI;
                double ox = (side.x * Math.cos(ang) + up.x * Math.sin(ang)) * r;
                double oy = (side.y * Math.cos(ang) + up.y * Math.sin(ang)) * r;
                double oz = (side.z * Math.cos(ang) + up.z * Math.sin(ang)) * r;
                level.addParticle(ParticleTypes.CLOUD, cx + ox, cy + oy, cz + oz, -dir.x * 0.05D, -dir.y * 0.05D, -dir.z * 0.05D);
            }
            if (level.random.nextFloat() < 0.3F) level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 0.0D, 0.0D, 0.0D);
        }
    }

    /** A small float vector helper (directions only). */
    private static final class Vector {
        final float x, y, z;

        Vector(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vector(Vec3 v) {
            this((float) v.x, (float) v.y, (float) v.z);
        }

        /** A unit vector at right angles to this one, lying flat. */
        Vector sideways() {
            float sx = -z, sz = x;
            float len = Mth.sqrt(sx * sx + sz * sz);
            return len < 1.0E-3F ? new Vector(1.0F, 0.0F, 0.0F) : new Vector(sx / len, 0.0F, sz / len);
        }

        Vector cross(Vector o) {
            return new Vector(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
        }
    }
}
