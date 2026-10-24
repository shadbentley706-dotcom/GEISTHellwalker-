package com.geist.client.gauntlet;

import com.geist.GeistMod;
import com.geist.item.GauntletItem;
import com.geist.network.GauntletActionPacket;
import com.geist.network.GauntletAnimPacket;
import com.geist.network.GeistNetwork;
import com.geist.registry.ModSounds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * The gauntlet on the client: the animation state every renderer reads (one record per player nearby),
 * the input (hold attack on a mob, or the barrage key), camera shake and field-of-view kicks, steam, boot-up.
 * The server decides everything that happens; this class only asks (start / stop) and then shows what the
 * server's messages say.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class GauntletClient {
    public static final int IDLE = 0, WINDUP = 1, BARRAGE = 2, COOLDOWN = 3;
    public static final int BOOT_TICKS = 60;
    /** Ticks to hold attack on a mob before it turns into a barrage (a quick click is a normal punch). */
    public static final int HOLD_TICKS = 6;

    public static final KeyMapping BARRAGE_KEY = new KeyMapping("key.geist.gauntlet_barrage", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.geist");

    @Mod.EventBusSubscriber(modid = GeistMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(BARRAGE_KEY);
        }
    }

    /** Per-player animation state (everyone nearby, including you). */
    public static final class Anim {
        public int state = IDLE;
        public long start;
        public int cooldownLen = 1;
        /** Game time of the last punch with each arm (0 = right, 1 = left). */
        public final long[] punchAt = {-1000L, -1000L};
        public long lastHeavy = -1000L, bootStart = -1000L;
        public int punches, hits;
        /** Carrying: the held mob's entity id (-1 = nothing), when it was lifted, when the throw wind-up began (-1 = not winding). */
        public int carryId = -1;
        public long carryStart, chargeStart = -1L;
        /** When the last throw and the last slam happened. */
        public long throwAt = -1000L, slamAt = -1000L;
        /** The throwing arm (0 = right, 1 = left): the wearer's main arm. */
        public int throwSide;
        /** Guard: up or not, when it went up / down, the last block (kind 0 hit, 1 projectile, 2 sonic boom) and the break. */
        public boolean guardUp, parried;
        public long guardStart = -1000L, guardDown = -1000L, lastBlock = -1000L, guardBreakAt = -1000L, heatAt;
        public int blockKind;
        /** The guard's heat as last reported (0..100), at {@link #heatAt}. */
        public float heat;
        Boolean wearing;
    }

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    // ---- the local player's request
    private static boolean requested, viaAttack, needRelease;
    // ---- carrying: hold use to wind up, let go to throw; attack slams
    private static boolean charging, useNeedsRelease, atkNeedsRelease;
    // ---- guard: hold use with both hands empty
    private static boolean guarding;
    private static int guardHeld;
    private static long guardReadyAt;
    private static int attackHeld, sentAge;
    /** Game time when the barrage / the throw are ready again (from the server's messages). */
    private static long barrageReadyAt, throwReadyAt;
    private static boolean wasCooling;
    // ---- camera: a damped spring on pitch and roll, plus decaying shake ("trauma")
    private static float dipOff, dipVel, rollOff, rollVel, trauma, fovKick;
    private static long fovKickAt = -1000L;
    private static long lastFrameNanos;

    private GauntletClient() {}

    public static Anim anim(Entity e) {
        return ANIMS.computeIfAbsent(e.getId(), k -> new Anim());
    }

    @Nullable
    public static Anim peek(int entityId) {
        return ANIMS.get(entityId);
    }

    @Nullable
    public static Anim local() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? null : ANIMS.get(p.getId());
    }

    public static long barrageReadyAt() { return barrageReadyAt; }
    public static long throwReadyAt() { return throwReadyAt; }
    public static boolean requested() { return requested; }

    /** Can a new barrage start right now (as far as the client can tell)? */
    public static boolean barrageReady(LocalPlayer p, long now) {
        Anim a = ANIMS.get(p.getId());
        return !GauntletItem.worn(p).isEmpty() && (a == null || a.state == IDLE) && now >= barrageReadyAt
                && (p.getAbilities().instabuild || p.getFoodData().getFoodLevel() >= GauntletItem.START_FOOD);
    }

    private static void kick(float dip, float roll, float shake) {
        dipVel += dip;
        rollVel += roll;
        trauma = Math.min(1.0F, trauma + shake);
    }

    private static void fov(float amount, long now) {
        fovKick = amount;
        fovKickAt = now;
    }

    // ---------------------------------------------------------------- messages from the server

    public static void remote(int entityId, int event, int a, int b) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity e = level.getEntity(entityId);
        if (e == null) return;
        Anim an = anim(e);
        long now = level.getGameTime();
        boolean local = e == mc.player;
        switch (event) {
            case GauntletAnimPacket.WINDUP -> {
                an.state = WINDUP;
                an.start = now;
                an.punches = an.hits = 0;
                if (local) kick(30.0F, 0.0F, 0.12F);
            }
            case GauntletAnimPacket.BARRAGE -> {
                an.state = BARRAGE;
                an.start = now;
                if (local) {
                    kick(-50.0F, 0.0F, 0.2F);
                    fov(0.05F, now);
                }
            }
            case GauntletAnimPacket.PUNCH -> {
                int side = a == 0 ? 0 : 1;
                an.punchAt[side] = now;
                an.punches++;
                if (b == 1) an.hits++;
                if (local) {
                    boolean hit = b == 1;
                    kick(hit ? 14.0F : 5.0F, (side == 0 ? 1 : -1) * (hit ? 9.0F : 3.0F), hit ? 0.1F : 0.03F);
                    if (hit) fov(-0.02F, now);
                }
            }
            case GauntletAnimPacket.COOLDOWN -> {
                an.state = COOLDOWN;
                an.start = now;
                an.cooldownLen = Math.max(1, a);
                if (local) {
                    barrageReadyAt = now + a;
                    kick(75.0F, (mc.player.getRandom().nextBoolean() ? 1 : -1) * 40.0F, 0.55F);
                    fov(0.07F, now);
                    wasCooling = true;
                    needRelease = true;
                }
            }
            case GauntletAnimPacket.CANCEL -> {
                an.state = IDLE;
                if (local) barrageReadyAt = now + GauntletItem.ABORT_COOLDOWN;
            }
            case GauntletAnimPacket.HEAVY -> {
                an.lastHeavy = now;
                if (local) {
                    kick(55.0F, (mc.player.getRandom().nextBoolean() ? 1 : -1) * 22.0F, 0.32F);
                    fov(-0.05F, now);
                }
            }
            case GauntletAnimPacket.CARRY -> {
                an.carryId = a;
                an.carryStart = now;
                an.chargeStart = -1L;
                an.throwSide = e instanceof Player pl && pl.getMainArm() == HumanoidArm.LEFT ? 1 : 0;
                GauntletCarryRender.lifted(a, entityId);
                if (local) {
                    charging = false;
                    useNeedsRelease = true;                                // the grab click itself must not start a wind-up
                    atkNeedsRelease = mc.options.keyAttack.isDown();
                    kick(28.0F, 0.0F, 0.18F);
                }
            }
            case GauntletAnimPacket.CHARGE -> an.chargeStart = now;
            case GauntletAnimPacket.THROW -> {
                an.carryId = -1;
                an.chargeStart = -1L;
                an.throwAt = now;
                GauntletCarryRender.thrown(b, now, an.throwSide);
                if (local) {
                    throwReadyAt = now + a;
                    charging = false;
                    kick(-45.0F, (an.throwSide == 0 ? -1 : 1) * 26.0F, 0.32F);
                    fov(0.07F, now);
                }
            }
            case GauntletAnimPacket.SLAM -> {
                an.carryId = -1;
                an.chargeStart = -1L;
                an.slamAt = now;
                GauntletCarryRender.dropped(b);
                if (local) {
                    throwReadyAt = now + a;
                    charging = false;
                    needRelease = true;                                    // a held attack must not roll into a barrage
                    kick(85.0F, 0.0F, 0.6F);
                    fov(-0.05F, now);
                }
            }
            case GauntletAnimPacket.GUARD -> {
                an.heat = GauntletAnimator.guardHeat(an, now);             // settle the heat at the old cooling rate first
                an.heatAt = now;
                if (a == 1) {
                    an.guardUp = true;
                    an.guardStart = now;
                } else if (an.guardUp) {
                    an.guardUp = false;
                    an.guardDown = now;
                }
            }
            case GauntletAnimPacket.BLOCK -> {
                an.lastBlock = now;
                an.blockKind = a % 10;
                an.parried = a >= 10;
                an.heat = b;
                an.heatAt = now;
                if (local) {
                    boolean big = an.blockKind == 2 || an.blockKind == 3;           // a sonic boom or a blast
                    float side = mc.player.getRandom().nextBoolean() ? 1.0F : -1.0F;
                    kick(big ? 75.0F : an.parried ? 18.0F : 30.0F, side * (big ? 32.0F : 10.0F), big ? 0.65F : an.parried ? 0.12F : 0.2F);
                    if (an.parried) fov(-0.04F, now);
                }
            }
            case GauntletAnimPacket.GUARD_BREAK -> {
                an.guardUp = false;
                an.guardDown = now;
                an.guardBreakAt = now;
                an.heat = GauntletItem.GUARD_HEAT_MAX;
                an.heatAt = now;
                if (local) {
                    guarding = false;
                    useNeedsRelease = true;                                // let go of use before guarding again
                    guardReadyAt = now + a;
                    kick(90.0F, (mc.player.getRandom().nextBoolean() ? 1 : -1) * 45.0F, 0.75F);
                    fov(0.08F, now);
                }
            }
            case GauntletAnimPacket.DROP -> {
                an.carryId = -1;
                an.chargeStart = -1L;
                GauntletCarryRender.dropped(b);
                if (local) {
                    throwReadyAt = now + a;
                    charging = false;
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- input (local player)

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer p) || GauntletItem.worn(p).isEmpty()) return;
        Anim a = ANIMS.get(p.getId());
        if (a != null && (a.state == WINDUP || a.state == BARRAGE)) {
            Input in = event.getInput();
            in.jumping = false;                                   // planted: no hopping while the pistons work
        }
    }

    /** While a barrage is on (or being asked for), clicks do not throw normal punches; while carrying, hands are full. */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || GauntletItem.worn(p).isEmpty()) return;
        Anim a = ANIMS.get(p.getId());
        if (a != null && a.carryId >= 0 && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if ((guarding || (a != null && a.guardUp)) && event.isUseItem()) {   // holding use to guard must not keep flicking levers
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (a != null && a.guardUp && event.isAttack()) {                   // forearms up: no punching
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!event.isAttack()) return;
        if (requested || (a != null && (a.state == WINDUP || a.state == BARRAGE))) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void send(boolean start) {
        GeistNetwork.CHANNEL.sendToServer(new GauntletActionPacket(start));
    }

    private static void sendAction(int action) {
        GeistNetwork.CHANNEL.sendToServer(new GauntletActionPacket(action));
    }

    /** Is the local player carrying a mob? */
    public static boolean carrying() {
        Anim a = local();
        return a != null && a.carryId >= 0;
    }

    public static boolean charging() {
        return charging;
    }

    public static long guardReadyAt() {
        return guardReadyAt;
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
        ItemStack mine = GauntletItem.worn(lp);

        // ---- what the local player is asking for
        boolean open = mc.screen != null || !mc.mouseHandler.isMouseGrabbed();
        boolean gDown = !open && BARRAGE_KEY.isDown();
        boolean atkDown = !open && mc.options.keyAttack.isDown();
        attackHeld = atkDown ? attackHeld + 1 : 0;
        boolean aimed = mc.crosshairPickEntity instanceof LivingEntity;
        Anim held = ANIMS.get(lp.getId());
        boolean carrying = held != null && held.carryId >= 0;
        boolean can = !mine.isEmpty() && lp.getMainHandItem().isEmpty() && !lp.isSpectator() && !lp.isUsingItem() && !carrying;
        boolean want = can && (gDown || (atkDown && (viaAttack || (attackHeld >= HOLD_TICKS && aimed))));
        if (!want) needRelease = false;
        if (want && !requested && !needRelease && barrageReady(lp, now)) {
            requested = true;
            viaAttack = !gDown;
            sentAge = 0;
            send(true);
        } else if (!want && requested) {
            requested = false;
            viaAttack = false;
            send(false);
        }
        // ---- carrying: hold use to wind the throw up, let go to throw; attack slams it down
        boolean useDown = !open && mc.options.keyUse.isDown();
        if (!useDown) useNeedsRelease = false;
        if (!atkDown) atkNeedsRelease = false;
        if (carrying) {
            if (useDown && !useNeedsRelease && !charging) {
                charging = true;
                sendAction(GauntletActionPacket.CHARGE);
            } else if (!useDown && charging) {
                charging = false;
                sendAction(GauntletActionPacket.THROW);
            }
            if (atkDown && !atkNeedsRelease && !charging) {
                atkNeedsRelease = true;
                sendAction(GauntletActionPacket.SLAM);
            }
            lp.setSprinting(false);
        } else {
            charging = false;
        }

        // ---- guard: hold use with both hands empty (sneak + use on a mob is the grab, not a guard)
        Anim ga = ANIMS.get(lp.getId());
        boolean guardWant = useDown && !mine.isEmpty() && lp.getMainHandItem().isEmpty() && lp.getOffhandItem().isEmpty()
                && !lp.isSpectator() && !carrying && !useNeedsRelease && now >= guardReadyAt
                && (ga == null || ga.state == IDLE) && !(lp.isShiftKeyDown() && aimed);
        guardHeld = guardWant ? guardHeld + 1 : 0;
        if (guardWant && !guarding && guardHeld >= GauntletItem.GUARD_HOLD_TICKS) {
            guarding = true;
            sendAction(GauntletActionPacket.GUARD_UP);
        } else if (!guardWant && guarding) {
            guarding = false;
            sendAction(GauntletActionPacket.GUARD_DOWN);
        } else if (guarding && (ga == null || !ga.guardUp) && (guardHeld - GauntletItem.GUARD_HOLD_TICKS) % 10 == 9) {
            sendAction(GauntletActionPacket.GUARD_UP);                     // the server said not yet (a lock-out): ask again
        }
        if (ga != null && ga.guardUp) lp.setSprinting(false);

        Anim la = ANIMS.get(lp.getId());
        // the server said no (hunger, cooldown...) and never answered: stop swallowing clicks until the button is released
        if (requested && (la == null || la.state == IDLE) && ++sentAge > 8) {
            requested = false;
            needRelease = true;
            send(false);
        }
        if (la != null && (la.state == WINDUP || la.state == BARRAGE)) lp.setSprinting(false);

        // ---- the lock-out ends
        if (la != null && la.state == COOLDOWN && now - la.start >= la.cooldownLen) la.state = IDLE;
        if (wasCooling && now >= barrageReadyAt && (la == null || la.state == IDLE)) {
            wasCooling = false;
            if (!mine.isEmpty()) lp.playSound(ModSounds.GAUNTLET_READY.get(), 0.6F, 1.0F);
        }

        // ---- everyone nearby wearing one: boot-up, steam, safety nets
        RandomSource r = level.random;
        for (Player pl : level.players()) {
            if (pl.distanceToSqr(lp) > 48.0D * 48.0D) continue;
            ItemStack g = GauntletItem.worn(pl);
            Anim a = anim(pl);
            boolean w = !g.isEmpty();
            if (Boolean.FALSE.equals(a.wearing) && w) {
                a.bootStart = now;
                pl.level().playLocalSound(pl.getX(), pl.getY(), pl.getZ(), ModSounds.GAUNTLET_BOOT.get(), SoundSource.PLAYERS, 0.8F, 1.0F, false);
            }
            a.wearing = w;
            Entity heldMob = a.carryId >= 0 ? level.getEntity(a.carryId) : null;
            boolean lostIt = heldMob == null ? now - a.carryStart > 40 : heldMob.distanceToSqr(pl) > 25.0D && now - a.carryStart > 10;
            if (a.carryId >= 0 && (!w || lostIt)) {
                GauntletCarryRender.dropped(a.carryId);                    // lost the message that it was let go
                a.carryId = -1;
                a.chargeStart = -1L;
            }
            if (a.carryId >= 0) pl.yBodyRot = pl.getYHeadRot();            // shoulders square to the throw
            if (!w) {
                a.state = IDLE;
                continue;
            }
            // lost messages must not leave an arm stuck in a pose
            if (a.state == WINDUP && now - a.start > GauntletItem.WINDUP_TICKS + 30) a.state = IDLE;
            if (a.state == BARRAGE && now - a.start > GauntletItem.MAX_BARRAGE_TICKS + 30) a.state = IDLE;
            if (a.state == COOLDOWN && now - a.start >= a.cooldownLen) a.state = IDLE;
            if (a.state == COOLDOWN) steam(level, pl, a, now, r);
            else if (a.state == WINDUP && now - a.start >= 6 && r.nextFloat() < 0.35F) {   // the pistons loading hiss
                for (int side = 0; side < 2; side++) {
                    Vec3 at = GauntletItem.bracerPos(pl, side);
                    level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, 0.0D, 0.02D, 0.0D);
                }
            }
        }
        if (now % 200 == 0) ANIMS.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    /** Steam pouring out of the bracers: violent in the first second, thinning out as the metal cools. */
    private static void steam(ClientLevel level, Player pl, Anim a, long now, RandomSource r) {
        float k = Mth.clamp((now - a.start) / (float) Math.max(a.cooldownLen, 1), 0.0F, 1.0F);
        float rate = k < 0.2F ? 1.0F : 0.55F * (1.0F - k) + 0.04F;
        double yaw = Math.toRadians(pl.yBodyRot);
        for (int side = 0; side < 2; side++) {
            Vec3 at = GauntletItem.bracerPos(pl, side);
            double sx = side == 0 ? 1.0D : -1.0D;
            double ox = -Math.cos(yaw) * sx, oz = -Math.sin(yaw) * sx;
            int n = k < 0.2F ? 3 : 1;
            for (int i = 0; i < n; i++) {
                if (r.nextFloat() > rate) continue;
                double vx = ox * (0.05D + 0.12D * rate) + (r.nextDouble() - 0.5D) * 0.06D;
                double vz = oz * (0.05D + 0.12D * rate) + (r.nextDouble() - 0.5D) * 0.06D;
                level.addParticle(ParticleTypes.CLOUD, at.x, at.y + 0.1D, at.z, vx, 0.05D + 0.1D * rate * r.nextDouble(), vz);
            }
            if (k < 0.5F && r.nextFloat() < 0.15F * rate) level.addParticle(ParticleTypes.SMOKE, at.x, at.y + 0.1D, at.z, 0.0D, 0.04D, 0.0D);
            if (k < 0.35F && r.nextFloat() < 0.12F) level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, ox * 0.2D, 0.1D, oz * 0.2D);
        }
        if (k > 0.1F && k < 0.7F && r.nextFloat() < 0.04F) {
            level.playLocalSound(pl.getX(), pl.getY(), pl.getZ(), ModSounds.EXO_HISS.get(), SoundSource.PLAYERS, 0.3F, 0.9F + r.nextFloat() * 0.2F, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ANIMS.clear();
        GauntletCarryRender.clear();
        requested = viaAttack = needRelease = wasCooling = false;
        charging = useNeedsRelease = atkNeedsRelease = guarding = false;
        guardHeld = 0;
        guardReadyAt = 0L;
        barrageReadyAt = throwReadyAt = 0L;
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
        dipVel += (-dipOff * 260.0F - dipVel * 22.0F) * dt;
        dipOff += dipVel * dt;
        rollVel += (-rollOff * 220.0F - rollVel * 18.0F) * dt;
        rollOff += rollVel * dt;
        trauma = Math.max(0.0F, trauma - dt * 1.9F);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float s = trauma * trauma;
        float pitch = dipOff, roll = rollOff, yaw = 0.0F;
        if (s > 0.0001F) {
            double t = System.nanoTime() / 1.0E9D;
            pitch += (float) (Math.sin(t * 71.0D) + Math.sin(t * 43.0D + 1.3D)) * 1.5F * s;
            yaw += (float) (Math.sin(t * 59.0D + 2.1D) + Math.sin(t * 31.0D)) * 1.1F * s;
            roll += (float) Math.sin(t * 53.0D + 0.7D) * 2.2F * s;
        }
        if (pitch != 0.0F) event.setPitch(event.getPitch() + pitch);
        if (yaw != 0.0F) event.setYaw(event.getYaw() + yaw);
        if (roll != 0.0F) event.setRoll(event.getRoll() + roll);
    }

    /** The view narrows as the pistons load, then pops wide when the barrage starts and when the steam vents. */
    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float t = mc.level.getGameTime() + mc.getFrameTime();
        Anim a = ANIMS.get(mc.player.getId());
        float k = 1.0F;
        if (a != null && a.state == WINDUP) k -= 0.05F * GauntletAnimator.ease((t - a.start) / GauntletItem.WINDUP_TICKS);
        if (a != null && a.carryId >= 0) k -= 0.06F * GauntletAnimator.ease(GauntletAnimator.chargeK(a, t));
        float since = t - fovKickAt;
        if (since >= 0.0F && since < 24.0F) k += fovKick * (float) Math.exp(-since / 5.0F);
        if (k != 1.0F) event.setNewFovModifier(event.getNewFovModifier() * k);
    }
}
