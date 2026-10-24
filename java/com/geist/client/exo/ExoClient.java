package com.geist.client.exo;

import com.geist.GeistMod;
import com.geist.item.ExoFrameItem;
import com.geist.network.ExoActionPacket;
import com.geist.network.ExoAnimPacket;
import com.geist.network.GeistNetwork;
import com.geist.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The exo-frame on the client.
 * Leap: jump while sprinting -> the pistons compress (you crouch for a quarter second) -> launch forward
 * and up about 5-6 blocks with a steam blast. Land hard and you drop into a half-second three-point landing.
 * Also: heavy stomping footsteps with camera shake, 10% slower walking, the boot-up sequence when the suit
 * goes on, idle steam, short-circuit sparks, and the animation state every renderer reads.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class ExoClient {
    public static final int NONE = 0, COMPRESS = 1, AIRBORNE = 2, LANDING = 3;
    public static final int BOOT_TICKS = 60;
    /** Launch speed: a 5.9-block apex and 9-12 blocks forward (checked against Minecraft air physics). */
    private static final double LEAP_UP = 1.0D, LEAP_FORWARD = 0.85D, HEAVY_DROP = 5.0D;
    /** One footfall every half walk cycle (the leg swing is cos(position * 0.6662)). */
    private static final float STEP = (float) Math.PI / 0.6662F;

    /** Per-player animation state (everyone nearby, including you). */
    public static final class Anim {
        public int type = NONE;
        public long start;
        public float strength;
        public long lastStep = -1000L, bootStart = -1000L;
        public boolean rightStep;
        int stepIndex = Integer.MIN_VALUE;
        Boolean wearing;
    }

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    // the local player's leap
    private static int phase = NONE;
    private static long phaseStart, lastLaunch = -1000L, landedAt = -1000L, bootLocal = -1000L;
    private static boolean lastJumpKey, airborne;
    private static double peakY;
    private static float lastDrop;
    // camera: a damped spring on pitch and roll, plus decaying shake ("trauma")
    private static float dipOff, dipVel, rollOff, rollVel, trauma;
    private static long lastFrameNanos;

    private ExoClient() {}

    public static Anim anim(Entity e) {
        return ANIMS.computeIfAbsent(e.getId(), k -> new Anim());
    }

    @Nullable
    public static Anim peek(int entityId) {
        return ANIMS.get(entityId);
    }

    public static int phase() { return phase; }
    public static long phaseStart() { return phaseStart; }
    public static long lastLaunch() { return lastLaunch; }
    public static long landedAt() { return landedAt; }
    public static float lastDrop() { return lastDrop; }
    public static long bootLocal() { return bootLocal; }

    /** Leap is ready (shown on the HUD): powered, dry, cooled down, on the ground. */
    public static boolean leapReady(LocalPlayer p, ItemStack exo, long now) {
        return ExoFrameItem.active(exo, now) && now - lastLaunch >= ExoFrameItem.LEAP_COOLDOWN && phase == NONE
                && (p.getAbilities().instabuild || ExoFrameItem.power(exo) >= ExoFrameItem.LEAP_COST);
    }

    private static boolean canLeap(LocalPlayer p, ItemStack exo, long now) {
        return leapReady(p, exo, now) && p.isSprinting() && p.onGround() && !p.isInWater() && !p.isInLava()
                && !p.getAbilities().flying && !p.isPassenger() && !p.isFallFlying() && !p.onClimbable();
    }

    // ---------------------------------------------------------------- input (local player)

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer p)) return;
        Input in = event.getInput();
        boolean jumpKey = in.jumping;
        ItemStack exo = ExoFrameItem.worn(p);
        if (exo.isEmpty()) {
            phase = NONE;
            lastJumpKey = jumpKey;
            return;
        }
        long now = p.level().getGameTime();
        if (phase == LANDING) {                                   // recovery: braced on the ground for half a second
            in.forwardImpulse = 0.0F;
            in.leftImpulse = 0.0F;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = true;
        } else if (phase == COMPRESS) {                           // the pistons compress: crouch, no vanilla jump
            in.jumping = false;
            in.shiftKeyDown = true;
        } else if (jumpKey && !lastJumpKey && canLeap(p, exo, now)) {
            startCompress(p, now);
            in.jumping = false;
            in.shiftKeyDown = true;
        }
        if (!p.isSprinting() && phase != LANDING) {               // heavy hydraulic limbs: 10% slower walking
            in.forwardImpulse *= 0.9F;
            in.leftImpulse *= 0.9F;
        }
        lastJumpKey = jumpKey;
    }

    private static void startCompress(LocalPlayer p, long now) {
        phase = COMPRESS;
        phaseStart = now;
        Anim a = anim(p);
        a.type = COMPRESS;
        a.start = now;
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), ModSounds.EXO_COMPRESS.get(), SoundSource.PLAYERS, 0.9F, 1.0F, false);
        dipVel += 40.0F;                                          // the view sinks as the pistons load
    }

    private static void launch(LocalPlayer p, long now) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        p.setDeltaMovement(-Mth.sin(yaw) * LEAP_FORWARD, LEAP_UP, Mth.cos(yaw) * LEAP_FORWARD);
        p.setOnGround(false);
        phase = AIRBORNE;
        phaseStart = now;
        lastLaunch = now;
        Anim a = anim(p);
        a.type = AIRBORNE;
        a.start = now;
        airborne = true;
        peakY = p.getY();
        GeistNetwork.CHANNEL.sendToServer(new ExoActionPacket(ExoActionPacket.LEAP, 0.0F));
        dipVel -= 90.0F;                                          // kick: the view snaps up
        trauma = Math.min(1.0F, trauma + 0.3F);
    }

    private static void land(LocalPlayer p, float drop, long now) {
        phase = LANDING;
        phaseStart = now;
        landedAt = now;
        lastDrop = drop;
        Anim a = anim(p);
        a.type = LANDING;
        a.start = now;
        a.strength = drop;
        GeistNetwork.CHANNEL.sendToServer(new ExoActionPacket(ExoActionPacket.LANDED, drop));
        float k = Math.min(drop, 30.0F);
        dipVel += 70.0F + k * 7.0F;                               // the impact drives the view down
        rollVel += (p.getRandom().nextBoolean() ? 1 : -1) * (30.0F + k * 2.0F);
        trauma = Math.min(1.0F, trauma + 0.35F + k * 0.025F);
    }

    /** Another player's leap or landing (sent by the server). */
    public static void remoteAnim(int entityId, int type, float strength) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (e == null || e == mc.player) return;
        Anim a = anim(e);
        a.type = type == ExoAnimPacket.LEAP ? AIRBORNE : LANDING;
        a.start = mc.level.getGameTime();
        a.strength = strength;
    }

    // ---------------------------------------------------------------- every tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer lp = mc.player;
        ClientLevel level = mc.level;
        if (lp == null || level == null || mc.isPaused()) return;
        long now = level.getGameTime();
        ItemStack exo = ExoFrameItem.worn(lp);

        // the local leap: compress -> launch -> airborne -> landing
        if (phase == COMPRESS && now - phaseStart >= ExoFrameItem.COMPRESS_TICKS) launch(lp, now);
        if (lp.isInWater() || lp.getAbilities().flying || lp.onClimbable() || lp.isFallFlying()) {
            peakY = lp.getY();                                    // water, flight and ladders break a fall
            if (phase == AIRBORNE) phase = NONE;
        }
        if (!lp.onGround()) {
            if (!airborne) {
                airborne = true;
                peakY = lp.getY();
            }
            peakY = Math.max(peakY, lp.getY());
        } else if (airborne) {
            airborne = false;
            double drop = peakY - lp.getY();
            boolean leapt = phase == AIRBORNE;
            if (!exo.isEmpty() && ExoFrameItem.active(exo, now) && !lp.isInWater() && (leapt || drop >= HEAVY_DROP)) {
                land(lp, (float) drop, now);
            } else if (leapt) {
                phase = NONE;
            }
        }
        if (phase == LANDING && now - phaseStart >= ExoFrameItem.LANDING_TICKS) phase = NONE;
        if (exo.isEmpty() && phase != NONE) phase = NONE;

        // everyone nearby wearing a suit: boot-up, footsteps, steam, sparks
        RandomSource r = level.random;
        for (Player pl : level.players()) {
            if (pl.distanceToSqr(lp) > 48.0D * 48.0D) continue;
            ItemStack ex = ExoFrameItem.worn(pl);
            Anim a = anim(pl);
            boolean w = !ex.isEmpty();
            if (Boolean.FALSE.equals(a.wearing) && w) boot(pl, a, now, pl == lp);
            a.wearing = w;
            if (!w) continue;
            if (pl != lp) {                                       // others' leap states end on the ground
                if (a.type == AIRBORNE && pl.onGround() && now - a.start > 4) a.type = NONE;
                if (a.type == COMPRESS && now - a.start > ExoFrameItem.COMPRESS_TICKS + 15) a.type = NONE;
            }
            if (a.type == LANDING && now - a.start > ExoFrameItem.LANDING_TICKS) a.type = NONE;
            if (a.type == AIRBORNE && pl == lp && phase != AIRBORNE) a.type = NONE;
            if (a.type == COMPRESS && pl == lp && phase != COMPRESS) a.type = phase;

            float pos = pl.walkAnimation.position();
            int idx = Mth.floor(pos / STEP);
            if (idx != a.stepIndex) {
                boolean stepping = a.stepIndex != Integer.MIN_VALUE && pl.walkAnimation.speed() > 0.12F && pl.onGround()
                        && !pl.isInWater() && !pl.isPassenger();
                a.stepIndex = idx;
                if (stepping) stomp(pl, a, ex, now, pl == lp);
            }

            boolean active = ExoFrameItem.active(ex, now);
            double yaw = pl.yBodyRot * Mth.DEG_TO_RAD;
            double bx = Math.sin(yaw) * 0.34D, bz = -Math.cos(yaw) * 0.34D;    // behind the player (the battery)
            if (active && r.nextFloat() < 0.012F) {               // idle: a puff of steam from the battery vents
                level.addParticle(ParticleTypes.CLOUD, pl.getX() + bx, pl.getY() + 1.15D, pl.getZ() + bz, 0.0D, 0.04D, 0.0D);
                if (r.nextFloat() < 0.35F) {
                    level.playLocalSound(pl.getX(), pl.getY(), pl.getZ(), ModSounds.EXO_HISS.get(), SoundSource.PLAYERS, 0.25F, 0.9F + r.nextFloat() * 0.2F, false);
                }
            }
            if (ExoFrameItem.shorted(ex)) {                       // shorted: sparks crawling over the legs
                if (r.nextFloat() < 0.3F) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, pl.getX() + (r.nextDouble() - 0.5D) * 0.6D, pl.getY() + r.nextDouble() * 1.1D,
                            pl.getZ() + (r.nextDouble() - 0.5D) * 0.6D, (r.nextDouble() - 0.5D) * 0.1D, 0.05D, (r.nextDouble() - 0.5D) * 0.1D);
                }
                if (r.nextFloat() < 0.04F) level.addParticle(ParticleTypes.SMOKE, pl.getX() + bx, pl.getY() + 1.0D, pl.getZ() + bz, 0.0D, 0.03D, 0.0D);
            } else if (ExoFrameItem.power(ex) <= 0 && r.nextFloat() < 0.03F) {   // dead: the battery smokes
                level.addParticle(ParticleTypes.SMOKE, pl.getX() + bx, pl.getY() + 1.0D, pl.getZ() + bz, 0.0D, 0.02D, 0.0D);
            }
        }
        if (now % 200 == 0) ANIMS.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    private static void boot(Player pl, Anim a, long now, boolean local) {
        a.bootStart = now;
        if (local) bootLocal = now;
        pl.level().playLocalSound(pl.getX(), pl.getY(), pl.getZ(), ModSounds.EXO_BOOT.get(), SoundSource.PLAYERS, 0.9F, 1.0F, false);
    }

    private static void stomp(Player pl, Anim a, ItemStack ex, long now, boolean local) {
        a.lastStep = now;
        a.rightStep = !a.rightStep;
        boolean dead = ExoFrameItem.power(ex) <= 0;
        float speed = Math.min(1.0F, pl.walkAnimation.speed());
        RandomSource r = pl.getRandom();
        pl.level().playLocalSound(pl.getX(), pl.getY(), pl.getZ(), ModSounds.EXO_STOMP.get(), SoundSource.PLAYERS,
                0.3F + 0.45F * speed, (dead ? 0.75F : 0.9F) + r.nextFloat() * 0.15F, false);
        if (r.nextFloat() < 0.25F && !dead) {                     // a hiss of steam from the ankle actuator
            double side = a.rightStep ? -0.2D : 0.2D, yaw = pl.yBodyRot * Mth.DEG_TO_RAD;
            pl.level().addParticle(ParticleTypes.POOF, pl.getX() + Math.cos(yaw) * side, pl.getY() + 0.15D, pl.getZ() + Math.sin(yaw) * side,
                    0.0D, 0.02D, 0.0D);
        }
        if (local) {
            dipVel += (dead ? 32.0F : 17.0F) * speed;             // the view drops with each heavy step
            rollVel += (a.rightStep ? 1 : -1) * 6.0F * speed;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ANIMS.clear();
        phase = NONE;
        airborne = false;
        dipOff = dipVel = rollOff = rollVel = trauma = 0.0F;
    }

    // ---------------------------------------------------------------- camera

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long t = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0F : Mth.clamp((t - lastFrameNanos) / 1.0E9F, 0.0F, 0.05F);
        lastFrameNanos = t;
        if (Minecraft.getInstance().isPaused()) return;
        dipVel += (-dipOff * 220.0F - dipVel * 20.0F) * dt;      // springs back to level
        dipOff += dipVel * dt;
        rollVel += (-rollOff * 180.0F - rollVel * 16.0F) * dt;
        rollOff += rollVel * dt;
        trauma = Math.max(0.0F, trauma - dt * 1.6F);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float s = trauma * trauma;
        float pitch = dipOff, roll = rollOff, yaw = 0.0F;
        if (s > 0.0001F) {
            double t = System.nanoTime() / 1.0E9D;
            pitch += (float) (Math.sin(t * 61.0D) + Math.sin(t * 37.0D + 1.3D)) * 2.2F * s;
            yaw += (float) (Math.sin(t * 53.0D + 2.1D) + Math.sin(t * 29.0D)) * 1.6F * s;
            roll += (float) (Math.sin(t * 47.0D + 0.7D)) * 3.0F * s;
        }
        if (pitch != 0.0F) event.setPitch(event.getPitch() + pitch);
        if (yaw != 0.0F) event.setYaw(event.getYaw() + yaw);
        if (roll != 0.0F) event.setRoll(event.getRoll() + roll);
    }

    /** The view narrows as the pistons load, punches wide on launch, and tightens on impact. */
    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = mc.level.getGameTime() + mc.getFrameTime();
        float k = 1.0F;
        if (phase == COMPRESS) k -= 0.06F * Mth.clamp((t - phaseStart) / ExoFrameItem.COMPRESS_TICKS, 0.0F, 1.0F);
        float sinceLaunch = t - lastLaunch;
        if (sinceLaunch >= 0.0F && sinceLaunch < 24.0F) k += 0.16F * (float) Math.exp(-sinceLaunch / 7.0F);
        float sinceLand = t - landedAt;
        if (sinceLand >= 0.0F && sinceLand < 14.0F) k -= 0.07F * (float) Math.exp(-sinceLand / 4.0F);
        if (k != 1.0F) event.setNewFovModifier(event.getNewFovModifier() * k);
    }
}
