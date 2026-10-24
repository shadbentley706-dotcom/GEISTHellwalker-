package com.geist.event;

import com.geist.GeistMod;
import com.geist.item.GauntletItem;
import com.geist.network.GauntletAnimPacket;
import com.geist.network.GeistNetwork;
import com.geist.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grab, carry and throw (server side).
 *
 * Sneak + use on a mob with an empty hand lifts it overhead (no bosses, nothing bigger than an iron golem, no
 * players). While you carry it you are slower, cannot sprint and it slowly costs hunger; it stays frozen in your
 * hands for up to 10 seconds, then wriggles free. Hold use to wind the throw up (full power after 1 second), let go
 * to throw it like a football: up to about 30 blocks, spiralling, with impact damage when it lands or hits a wall,
 * and it bowls over the first creature it flies into. Attack while carrying slams it into the ground in front of
 * you, knocking back anything near. A creeper cannot go off while it is held (the fuse winds down); thrown, its fuse
 * is lit and it explodes the moment it hits something (or after the usual 1.5 seconds).
 *
 * While held, the mob's AI and gravity are switched off; how to put them back is written into the mob itself, so
 * even a crash or a server stop mid-carry cannot leave a frozen mob behind (it is fixed when it next loads).
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID)
public final class GauntletCarry {
    private static final UUID CARRY_SLOW_ID = UUID.fromString("5b0d6e1a-7c34-4d0e-9a52-1c8f3e6b7b04");
    private static final String TAG = "geist_carried";

    /** One player's held mob. */
    private static final class Hold {
        final UUID mob;
        final ResourceKey<Level> dim;
        final long start;
        long chargeStart = -1L;

        Hold(UUID mob, ResourceKey<Level> dim, long start) {
            this.mob = mob;
            this.dim = dim;
            this.start = start;
        }
    }

    /** A mob in flight after a throw. */
    private static final class Flight {
        final ResourceKey<Level> dim;
        final UUID thrower;
        final long start;
        final float power;

        Flight(ResourceKey<Level> dim, UUID thrower, long start, float power) {
            this.dim = dim;
            this.thrower = thrower;
            this.start = start;
            this.power = power;
        }
    }

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Set<UUID> HELD = new HashSet<>();
    private static final Map<UUID, Long> READY = new HashMap<>();
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();

    private GauntletCarry() {}

    /** Is this player (server side) holding a mob? */
    public static boolean carrying(Entity p) {
        return !p.level().isClientSide && HOLDS.containsKey(p.getUUID());
    }

    private static double size(LivingEntity t) {
        return (double) t.getBbWidth() * t.getBbWidth() * t.getBbHeight();
    }

    private static boolean liftable(LivingEntity t) {
        if (!(t instanceof Mob) || t instanceof EnderDragon || t instanceof WitherBoss || t.getType().is(Tags.EntityTypes.BOSSES)) return false;
        return size(t) <= GauntletItem.MAX_CARRY_SIZE;
    }

    private static Vec3 forward(Player p) {
        double yaw = Math.toRadians(p.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    // ---------------------------------------------------------------- grab

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        // runs on both sides with the same filters, so the client does not go on to use an off-hand item on the same click
        Player p = event.getEntity();
        if (p == null || !(event.getTarget() instanceof LivingEntity t)) return;
        if (!p.isShiftKeyDown() || !p.getMainHandItem().isEmpty() || GauntletItem.worn(p).isEmpty() || p.isSpectator()) return;
        // things you sneak-click for other reasons stay untouched: players, armor stands, your own pets, villagers, rideable animals
        if (t instanceof Player || t instanceof ArmorStand || t instanceof AbstractVillager || t instanceof AbstractHorse
                || (t instanceof TamableAnimal ta && ta.isOwnedBy(p))) return;
        event.setCanceled(true);                                               // no breeding, no leashing: it is a grab
        if (event.getHand() != InteractionHand.MAIN_HAND) {                    // the off-hand click must not act on the mob just lifted
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (p instanceof ServerPlayer sp) grab(sp, t);
    }

    private static void grab(ServerPlayer p, LivingEntity t) {
        if (HOLDS.containsKey(p.getUUID()) || GauntletEvents.barraging(p)) return;
        GauntletGuard.lower(p);                                                // the grab takes both hands
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        boolean creative = p.getAbilities().instabuild;
        Long ready = READY.get(p.getUUID());
        if (ready != null && now < ready) {
            GauntletEvents.tell(p, "message.geist.gauntlet.throw_wait", ChatFormatting.GRAY, String.format("%.1f", (ready - now) / 20.0D));
            return;
        }
        if (!liftable(t)) {
            GauntletEvents.tell(p, "message.geist.gauntlet.too_big", ChatFormatting.GRAY);
            return;
        }
        if (HELD.contains(t.getUUID()) || !t.isAlive()) return;
        Mob m = (Mob) t;
        if (m.isNoAi()) {                                                      // a frozen display mob would not fly anyway
            GauntletEvents.tell(p, "message.geist.gauntlet.no_grip", ChatFormatting.GRAY);
            return;
        }
        if (m instanceof Creeper lit && lit.isIgnited()) {                     // lit with flint and steel: nothing stops that fuse
            GauntletEvents.tell(p, "message.geist.gauntlet.lit", ChatFormatting.RED);
            return;
        }
        if (!creative && p.getFoodData().getFoodLevel() < GauntletItem.THROW_MIN_FOOD) {
            GauntletEvents.tell(p, "message.geist.gauntlet.carry_hungry", ChatFormatting.RED);
            return;
        }
        FLIGHTS.remove(m.getUUID());                                           // caught in mid-air
        m.stopRiding();
        m.ejectPassengers();
        if (!m.getPersistentData().contains(TAG)) {                             // remember how to put it back
            CompoundTag c = new CompoundTag();
            c.putBoolean("noai", m.isNoAi());
            c.putBoolean("nograv", m.isNoGravity());
            m.getPersistentData().put(TAG, c);
        }
        m.setNoAi(true);
        m.setNoGravity(true);
        m.getNavigation().stop();
        m.setTarget(null);
        if (m instanceof Creeper held) held.setSwellDir(-1);                   // the clamps choke the fuse: no boom in your hands
        HOLDS.put(p.getUUID(), new Hold(m.getUUID(), level.dimension(), now));
        HELD.add(m.getUUID());
        slow(p, Math.min(0.4D, 0.15D + 0.04D * size(m)));
        place(p, m);
        p.causeFoodExhaustion(creative ? 0.0F : 0.5F);

        for (int side = 0; side < 2; side++) {
            Vec3 at = GauntletItem.bracerPos(p, side);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 4, 0.08D, 0.08D, 0.08D, 0.06D);
        }
        level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY(), m.getZ(), 6, 0.3D, 0.1D, 0.3D, 0.04D);
        GauntletEvents.sound(level, p.getX(), p.getY(), p.getZ(), ModSounds.GAUNTLET_WINDUP.get(), 0.6F, 1.35F);
        GauntletEvents.sound(level, p.getX(), p.getY(), p.getZ(), ModSounds.GAUNTLET_IMPACT.get(), 0.35F, 1.6F);
        GauntletEvents.send(p, GauntletAnimPacket.CARRY, m.getId(), 0);
    }

    /** Holds the mob just above the wearer's head (the client draws it lying across the raised hands). */
    private static void place(ServerPlayer p, Mob m) {
        Vec3 f = forward(p);
        m.setPos(p.getX() + f.x * 0.25D, p.getY() + p.getBbHeight() + 0.15D, p.getZ() + f.z * 0.25D);
        m.setDeltaMovement(Vec3.ZERO);
        m.fallDistance = 0.0F;
        m.setYRot(p.getYRot());
        m.yBodyRot = p.getYRot();
        m.yHeadRot = p.getYRot();
        if (m instanceof Creeper held) held.setSwellDir(-1);                   // keeps the fuse winding down while held
    }

    private static void slow(ServerPlayer p, double amount) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(CARRY_SLOW_ID);
        if (amount > 0.0D) {
            speed.addTransientModifier(new AttributeModifier(CARRY_SLOW_ID, "Gauntlet carry", -amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ---------------------------------------------------------------- carrying

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Hold h = HOLDS.get(p.getUUID());
        if (h == null) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        Entity e = h.dim == level.dimension() ? level.getEntity(h.mob) : null;
        if (!(e instanceof Mob m) || !m.isAlive()) {
            letGo(p, h, false);
            return;
        }
        boolean creative = p.getAbilities().instabuild;
        boolean able = p.isAlive() && !p.isSpectator() && !p.isSleeping() && !GauntletItem.worn(p).isEmpty() && p.getMainHandItem().isEmpty();
        boolean starving = !creative && p.getFoodData().getFoodLevel() <= GauntletItem.STOP_FOOD;
        long held = now - h.start;
        boolean loose = held > GauntletItem.MAX_CARRY_TICKS + (h.chargeStart >= 0 ? 40 : 0);
        if (!able || starving || loose || m.distanceToSqr(p) > 36.0D) {
            if (loose && able) GauntletEvents.tell(p, "message.geist.gauntlet.wriggled", ChatFormatting.GRAY);
            if (starving && able) GauntletEvents.tell(p, "message.geist.gauntlet.carry_starved", ChatFormatting.RED);
            letGo(p, h, true);
            return;
        }
        p.setSprinting(false);
        p.yBodyRot = p.getYHeadRot();                                          // shoulders square to the throw
        if (!creative) p.causeFoodExhaustion(GauntletItem.CARRY_EXHAUSTION);
        place(p, m);
    }

    // ---------------------------------------------------------------- client requests

    /** Use held while carrying: wind the throw up. */
    public static void charge(ServerPlayer p) {
        Hold h = HOLDS.get(p.getUUID());
        if (h == null || h.chargeStart >= 0) return;
        ServerLevel level = p.serverLevel();
        h.chargeStart = level.getGameTime();
        GauntletEvents.sound(level, p.getX(), p.getY(), p.getZ(), ModSounds.GAUNTLET_WINDUP.get(), 0.9F, 1.15F);
        GauntletEvents.send(p, GauntletAnimPacket.CHARGE, 0, 0);
    }

    /** Use let go while carrying: throw. */
    public static void release(ServerPlayer p) {
        Hold h = HOLDS.get(p.getUUID());
        if (h == null) return;
        ServerLevel level = p.serverLevel();
        Mob m = held(level, h);
        if (m == null) {
            letGo(p, h, false);
            return;
        }
        long charged = h.chargeStart < 0 ? 0L : level.getGameTime() - h.chargeStart;
        hurl(p, h, m, Mth.clamp(charged / (float) GauntletItem.FULL_CHARGE_TICKS, 0.0F, 1.0F));
    }

    /** Attack while carrying: slam it into the ground in front. */
    public static void slam(ServerPlayer p) {
        Hold h = HOLDS.get(p.getUUID());
        if (h == null) return;
        ServerLevel level = p.serverLevel();
        Mob m = held(level, h);
        if (m == null) {
            letGo(p, h, false);
            return;
        }
        forget(p, h, m);
        Vec3 spot = setDown(p, m, 1.4D);
        long now = level.getGameTime();
        boolean creative = p.getAbilities().instabuild;
        int cd = creative ? 20 : GauntletItem.THROW_COOLDOWN * 3 / 5;
        READY.put(p.getUUID(), now + cd);
        if (!creative) p.causeFoodExhaustion(2.0F);
        m.invulnerableTime = 0;
        m.hurt(p.damageSources().playerAttack(p), GauntletItem.SLAM_DAMAGE);
        m.setDeltaMovement(0.0D, 0.3D, 0.0D);
        m.hurtMarked = true;
        // the shockwave knocks back what stands around
        AABB area = new AABB(spot, spot).inflate(2.6D, 1.2D, 2.6D);
        for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, area, x -> x != p && x != m && x.isAlive() && !x.isSpectator())) {
            if (!GauntletEvents.canHarm(p, v) || v instanceof ArmorStand) continue;
            v.invulnerableTime = 0;
            v.hurt(p.damageSources().playerAttack(p), 3.0F);
            v.knockback(0.7D, spot.x - v.getX(), spot.z - v.getZ());
        }
        GauntletEvents.shockwave(level, m, new Vec3(0.0D, -1.0D, 0.0D), true);
        level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 0.2D, spot.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.1D, spot.z, 20, 0.6D, 0.05D, 0.6D, 0.12D);
        GauntletEvents.sound(level, spot.x, spot.y, spot.z, ModSounds.GAUNTLET_IMPACT.get(), 1.4F, 0.8F);
        GauntletEvents.sound(level, spot.x, spot.y, spot.z, ModSounds.GAUNTLET_HIT.get(), 1.0F, 0.7F);
        GauntletEvents.send(p, GauntletAnimPacket.SLAM, cd, m.getId());
    }

    // ---------------------------------------------------------------- the throw

    private static void hurl(ServerPlayer p, Hold h, Mob m, float power) {
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        boolean creative = p.getAbilities().instabuild;
        forget(p, h, m);

        float pitch = Mth.clamp(p.getXRot() - GauntletItem.THROW_LOFT, -GauntletItem.MAX_LAUNCH_UP, 60.0F);
        Vec3 dir = Vec3.directionFromRotation(pitch, p.getYRot());
        // let go just in front of the head; if that is inside a wall, from where it was held
        double reach = 0.6D + m.getBbWidth() * 0.5D;
        Vec3 eye = p.getEyePosition();
        Vec3 centre = new Vec3(eye.x + dir.x * reach, eye.y + 0.3D, eye.z + dir.z * reach);
        Vec3 feet = centre.subtract(0.0D, m.getBbHeight() * 0.5D, 0.0D);
        if (level.noCollision(m, m.getDimensions(m.getPose()).makeBoundingBox(feet))) m.setPos(feet.x, feet.y, feet.z);

        AttributeInstance kr = m.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        double res = kr == null ? 0.0D : Mth.clamp(kr.getValue(), 0.0D, 1.0D);
        double k = Mth.clamp(1.15D - 0.1D * size(m), 0.5D, 1.0D) * (1.0D - 0.3D * res);  // heavy mobs fly shorter
        double speed = Mth.lerp(power, GauntletItem.THROW_SPEED_MIN, GauntletItem.THROW_SPEED_MAX) * k;
        m.setDeltaMovement(dir.scale(speed));
        m.hurtMarked = true;
        m.setOnGround(false);
        m.fallDistance = 0.0F;
        if (m instanceof Creeper bomb) bomb.ignite();                          // let go of the clamps and the fuse is lit: it blows on impact
        FLIGHTS.put(m.getUUID(), new Flight(level.dimension(), p.getUUID(), now, power));

        int cd = creative ? 20 : GauntletItem.THROW_COOLDOWN;
        READY.put(p.getUUID(), now + cd);
        if (!creative) p.causeFoodExhaustion(GauntletItem.THROW_EXHAUSTION * (0.5F + 0.5F * power));

        Vec3 at = m.position().add(0.0D, m.getBbHeight() * 0.5D, 0.0D);
        Vec3 ref = Math.abs(dir.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = dir.cross(ref).normalize(), up = right.cross(dir).normalize();
        for (int i = 0; i < 14; i++) {                                         // a ring of air blown off the release
            double a = i * Math.PI * 2.0D / 14.0D;
            Vec3 d = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, d.x, d.y, d.z, 0.15D + 0.2D * power);
        }
        for (int side = 0; side < 2; side++) {
            Vec3 b = GauntletItem.bracerPos(p, side);
            level.sendParticles(ParticleTypes.CLOUD, b.x, b.y, b.z, 6, 0.1D, 0.1D, 0.1D, 0.12D);
        }
        GauntletEvents.sound(level, p.getX(), p.getY(), p.getZ(), ModSounds.GAUNTLET_THROW.get(), 1.2F, 1.15F - 0.25F * power);
        if (power > 0.6F) GauntletEvents.sound(level, p.getX(), p.getY(), p.getZ(), ModSounds.GAUNTLET_WHIFF.get(), 1.0F, 0.6F);
        GauntletEvents.send(p, GauntletAnimPacket.THROW, cd, m.getId());
    }

    /** Watches thrown mobs: impact damage when they land or hit a wall, and they bowl over the first creature in the way. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FLIGHTS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Flight>> it = FLIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Flight> en = it.next();
            Flight fl = en.getValue();
            ServerLevel level = server.getLevel(fl.dim);
            Entity e = level == null ? null : level.getEntity(en.getKey());
            long age = level == null ? 999L : level.getGameTime() - fl.start;
            if (!(e instanceof LivingEntity t) || !t.isAlive() || age > 100L) {
                it.remove();
                continue;
            }
            t.fallDistance = Math.min(t.fallDistance, 2.5F);                  // the throw's own impact replaces fall damage
            ServerPlayer thrower = server.getPlayerList().getPlayer(fl.thrower);
            DamageSource src = thrower != null ? thrower.damageSources().playerAttack(thrower) : level.damageSources().fall();
            Vec3 vel = t.getDeltaMovement();
            if (age > 1L && vel.lengthSqr() > 0.09D) {                         // bowling: the first thing it flies into
                List<LivingEntity> in = level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(0.25D),
                        x -> x != t && x.isAlive() && !x.isSpectator() && !x.getUUID().equals(fl.thrower) && !(x instanceof ArmorStand)
                                && (thrower == null || GauntletEvents.canHarm(thrower, x)));
                if (!in.isEmpty() && t instanceof Creeper bomb) {               // a thrown creeper goes off on whatever it hits
                    it.remove();
                    detonate(level, bomb);
                    continue;
                }
                if (!in.isEmpty()) {
                    LivingEntity v = in.get(0);
                    v.invulnerableTime = 0;
                    v.hurt(src, GauntletItem.TACKLE_DAMAGE * (0.6F + 0.6F * fl.power));
                    v.knockback(0.8D + 0.7D * fl.power, -vel.x, -vel.z);
                    it.remove();
                    impact(level, t, src, fl.power, false, vel);
                    continue;
                }
            }
            boolean wall = age > 2L && t.horizontalCollision;
            boolean ground = age > 3L && (t.onGround() || t.isInWater() || t.isInLava());
            if (!wall && !ground) {
                if (fl.power > 0.5F && age % 2L == 0L) {                       // a wake of air behind a hard throw
                    level.sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY() + t.getBbHeight() * 0.5D, t.getZ(), 1, 0.05D, 0.05D, 0.05D, 0.0D);
                }
                continue;
            }
            it.remove();
            if (t instanceof Creeper bomb) detonate(level, bomb);
            else impact(level, t, src, fl.power, wall, vel);
        }
    }

    /** A thrown creeper hitting something: its own explosion (twice as big if charged), right there. */
    private static void detonate(ServerLevel level, Creeper c) {
        CompoundTag tag = new CompoundTag();
        c.addAdditionalSaveData(tag);
        float radius = tag.contains("ExplosionRadius") ? tag.getByte("ExplosionRadius") : 3.0F;
        if (c.isPowered()) radius *= 2.0F;
        level.explode(c, c.getX(), c.getY(), c.getZ(), radius, Level.ExplosionInteraction.MOB);
        c.discard();
    }

    private static void impact(ServerLevel level, LivingEntity t, DamageSource src, float power, boolean wall, Vec3 vel) {
        t.invulnerableTime = 0;
        t.hurt(src, GauntletItem.THROW_IMPACT_DAMAGE * (0.5F + 0.5F * power) + (wall ? 2.0F : 0.0F));
        Vec3 look = vel.lengthSqr() > 1.0E-4D ? vel.normalize() : new Vec3(0.0D, -1.0D, 0.0D);
        GauntletEvents.shockwave(level, t, look, true);
        level.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 0.3D, t.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY() + 0.1D, t.getZ(), 14, 0.4D, 0.1D, 0.4D, 0.1D);
        GauntletEvents.sound(level, t.getX(), t.getY(), t.getZ(), ModSounds.GAUNTLET_IMPACT.get(), 1.3F, 0.9F + level.random.nextFloat() * 0.2F);
    }

    // ---------------------------------------------------------------- letting go

    private static Mob held(ServerLevel level, Hold h) {
        if (h.dim != level.dimension()) return null;
        Entity e = level.getEntity(h.mob);
        return e instanceof Mob m && m.isAlive() ? m : null;
    }

    /** Ends the hold: the mob gets its AI and gravity back, the wearer their speed. */
    private static void forget(ServerPlayer p, Hold h, Mob m) {
        HOLDS.remove(p.getUUID());
        HELD.remove(h.mob);
        slow(p, 0.0D);
        if (m != null) restore(m);
    }

    /** Lets go without throwing (it got loose, or the wearer can no longer hold it): set it down in front. */
    private static void letGo(ServerPlayer p, Hold h, boolean setDown) {
        Mob m = null;
        MinecraftServer server = p.getServer();
        ServerLevel level = server == null ? null : server.getLevel(h.dim);
        if (level != null && level.getEntity(h.mob) instanceof Mob found) m = found;
        forget(p, h, m);
        if (m != null && setDown && m.level() == p.level() && m.isAlive()) setDown(p, m, 1.0D);
        long now = p.serverLevel().getGameTime();
        int cd = p.getAbilities().instabuild ? 20 : GauntletItem.DROP_COOLDOWN;
        READY.put(p.getUUID(), now + cd);
        GauntletEvents.send(p, GauntletAnimPacket.DROP, cd, m != null ? m.getId() : -1);
    }

    /** Puts the mob on the ground {@code dist} blocks in front (or at the wearer's feet if that is blocked). Returns the spot. */
    private static Vec3 setDown(ServerPlayer p, Mob m, double dist) {
        ServerLevel level = p.serverLevel();
        Vec3 f = forward(p);
        Vec3 top = new Vec3(p.getX() + f.x * dist, p.getY() + 1.0D, p.getZ() + f.z * dist);
        BlockHitResult hit = level.clip(new ClipContext(top, top.subtract(0.0D, 4.0D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, m));
        Vec3 spot = hit.getType() == HitResult.Type.MISS ? top.subtract(0.0D, 1.0D, 0.0D) : hit.getLocation();
        if (!level.noCollision(m, m.getDimensions(m.getPose()).makeBoundingBox(spot))) spot = p.position();
        m.setPos(spot.x, spot.y, spot.z);
        m.setDeltaMovement(Vec3.ZERO);
        m.fallDistance = 0.0F;
        return spot;
    }

    /** Gives the mob back what it had before it was picked up. */
    private static void restore(Mob m) {
        CompoundTag pd = m.getPersistentData();
        if (!pd.contains(TAG)) return;
        CompoundTag c = pd.getCompound(TAG);
        m.setNoAi(c.getBoolean("noai"));
        m.setNoGravity(c.getBoolean("nograv"));
        pd.remove(TAG);
    }

    // ---------------------------------------------------------------- protection and cleanup

    /** A held mob does not choke in the ceiling above your head or get crushed. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        LivingEntity t = event.getEntity();
        if (t.level().isClientSide || !HELD.contains(t.getUUID())) return;
        DamageSource s = event.getSource();
        if (s.is(DamageTypes.IN_WALL) || s.is(DamageTypes.CRAMMING) || s.is(DamageTypeTags.IS_FALL) || s.is(DamageTypes.FLY_INTO_WALL)) {
            event.setCanceled(true);
        }
    }

    /** Safety net: a mob that was saved while held (a crash, a server stop) gets its AI back when it loads. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob m)) return;
        if (!m.getPersistentData().contains(TAG)) return;
        if (!HELD.contains(m.getUUID())) {
            restore(m);
            return;
        }
        // still held, but something (a command, another mod) moved it to another dimension: free it; the hold ends next tick
        for (Hold h : HOLDS.values()) {
            if (h.mob.equals(m.getUUID()) && h.dim != event.getLevel().dimension()) restore(m);
        }
    }

    /** Someone who comes into view of a player already carrying something sees the carry too. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof ServerPlayer carrier)) return;
        if (GauntletGuard.guarding(carrier)) {                                 // a raised guard shows too
            GeistNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new GauntletAnimPacket(carrier.getId(), GauntletAnimPacket.GUARD, 1, 0));
        }
        Hold h = HOLDS.get(carrier.getUUID());
        if (h == null) return;
        Mob m = held(carrier.serverLevel(), h);
        if (m == null) return;
        GeistNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new GauntletAnimPacket(carrier.getId(), GauntletAnimPacket.CARRY, m.getId(), 0));
        if (h.chargeStart >= 0L) {
            GeistNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new GauntletAnimPacket(carrier.getId(), GauntletAnimPacket.CHARGE, 0, 0));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Hold h = HOLDS.get(p.getUUID());
            if (h != null) letGo(p, h, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Hold h = HOLDS.get(p.getUUID());
            if (h != null) letGo(p, h, true);
        }
        READY.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Hold h = HOLDS.get(p.getUUID());
            if (h != null) letGo(p, h, false);
        }
    }

    /** Before the world is saved for the last time, every held mob gets its AI back. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, Hold> en : new ArrayList<>(HOLDS.entrySet())) {
            ServerLevel level = server.getLevel(en.getValue().dim);
            if (level != null && level.getEntity(en.getValue().mob) instanceof Mob m) restore(m);
        }
        HOLDS.clear();
        HELD.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HOLDS.clear();
        HELD.clear();
        READY.clear();
        FLIGHTS.clear();
    }
}
