package com.geist.event;

import com.geist.GeistMod;
import com.geist.item.GauntletItem;
import com.geist.network.GauntletAnimPacket;
import com.geist.network.GeistNetwork;
import com.geist.registry.ModItems;
import com.geist.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The gauntlet's rules on the server.
 *
 * Normal punch: with the gauntlet on and an empty main hand, attack damage is 10 and the swing speed 1.4 per
 * second (transient attribute modifiers, so the vanilla attack cooldown, crits and the HUD all just work).
 * A full-power punch also kicks the target back hard.
 *
 * Barrage: the client asks (hold attack on a mob, or the barrage key). 10 ticks of wind-up, then a punch every
 * 4 or 5 ticks, alternating arms, each one 3 damage that skips the target's hit cooldown and barely moves it,
 * costing hunger. It ends when the key is released, after 4 seconds, or when hunger runs low; then steam vents
 * and the item is locked out (3 to 7 seconds, shown as the usual item cooldown).
 *
 * Grab, carry and throw: see GauntletCarry.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID)
public final class GauntletEvents {
    private static final UUID PUNCH_DAMAGE_ID = UUID.fromString("5b0d6e1a-7c34-4d0e-9a52-1c8f3e6b7b01");
    private static final UUID PUNCH_SPEED_ID = UUID.fromString("5b0d6e1a-7c34-4d0e-9a52-1c8f3e6b7b02");
    private static final UUID STANCE_ID = UUID.fromString("5b0d6e1a-7c34-4d0e-9a52-1c8f3e6b7b03");
    private static final int WINDUP = 1, BARRAGE = 2;

    /** One player's barrage in progress. */
    private static final class Run {
        int phase = WINDUP;
        long start, barrageStart, nextPunch;
        int side, punches;
        boolean held = true;
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    /** True while a barrage punch is being applied, so the knockback handler can tone it down. */
    private static boolean barrageHit;

    private GauntletEvents() {}

    static void send(ServerPlayer p, int event, int a, int b) {
        GeistNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new GauntletAnimPacket(p.getId(), event, a, b));
    }

    static void sound(ServerLevel level, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        level.playSound(null, x, y, z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static void tell(Player p, String key, ChatFormatting style, Object... args) {
        p.displayClientMessage(Component.translatable(key, args).withStyle(style), true);
    }

    /** Is this player mid-barrage (wind-up included)? */
    static boolean barraging(ServerPlayer p) {
        return RUNS.containsKey(p.getUUID());
    }

    private static boolean canFight(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && p.getMainHandItem().isEmpty();
    }

    // ---------------------------------------------------------------- the barrage request (from the client)

    public static void request(ServerPlayer p, boolean start) {
        UUID id = p.getUUID();
        if (!start) {
            Run r = RUNS.get(id);
            if (r != null) r.held = false;
            return;
        }
        if (RUNS.containsKey(id) || GauntletCarry.carrying(p) || GauntletGuard.guarding(p)) return;   // hands busy: no barrage
        ItemStack g = GauntletItem.worn(p);
        if (g.isEmpty() || !canFight(p) || p.getCooldowns().isOnCooldown(g.getItem())) return;
        boolean creative = p.getAbilities().instabuild;
        if (!creative && p.getFoodData().getFoodLevel() < GauntletItem.START_FOOD) {
            tell(p, "message.geist.gauntlet.hungry", ChatFormatting.RED);
            return;
        }
        ServerLevel level = p.serverLevel();
        Run r = new Run();
        r.start = level.getGameTime();
        RUNS.put(id, r);
        p.causeFoodExhaustion(GauntletItem.WINDUP_EXHAUSTION);
        stance(p, true);
        sound(level, p.getX(), p.getY(), p.getZ(), ModSounds.GAUNTLET_WINDUP.get(), 1.0F, 1.0F);
        send(p, GauntletAnimPacket.WINDUP, 0, 0);
    }

    // ---------------------------------------------------------------- every tick

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        ItemStack g = GauntletItem.worn(p);
        weapon(p, !g.isEmpty() && p.getMainHandItem().isEmpty() && !p.isSpectator());
        Run r = RUNS.get(p.getUUID());
        if (r == null) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (g.isEmpty() || !canFight(p)) {
            finish(p, r, now);
            return;
        }
        p.setSprinting(false);
        if (r.phase == WINDUP) {
            if (!r.held) {
                finish(p, r, now);
                return;
            }
            if (now - r.start >= GauntletItem.WINDUP_TICKS) {
                r.phase = BARRAGE;
                r.barrageStart = now;
                r.nextPunch = now;
                send(p, GauntletAnimPacket.BARRAGE, 0, 0);
            }
        }
        if (r.phase == BARRAGE) {
            boolean spent = now - r.barrageStart >= GauntletItem.MAX_BARRAGE_TICKS;
            boolean starving = !p.getAbilities().instabuild && p.getFoodData().getFoodLevel() <= GauntletItem.STOP_FOOD;
            if (!r.held || spent || starving) {
                if (starving && r.held) tell(p, "message.geist.gauntlet.starved", ChatFormatting.RED);
                finish(p, r, now);
                return;
            }
            if (now >= r.nextPunch) {
                punch(p, r);
                r.nextPunch = now + GauntletItem.PUNCH_GAPS[r.punches++ % GauntletItem.PUNCH_GAPS.length];
            }
        }
    }

    /** Ends a barrage (or a wind-up that never got going): steam, the lock-out and the client messages. */
    private static void finish(ServerPlayer p, Run r, long now) {
        RUNS.remove(p.getUUID());
        stance(p, false);
        ServerLevel level = p.serverLevel();
        if (r.phase == BARRAGE) {
            int cd = GauntletItem.cooldownFor((int) (now - r.barrageStart));
            p.getCooldowns().addCooldown(ModItems.GAUNTLET.get(), cd);
            vent(p, level);
            send(p, GauntletAnimPacket.COOLDOWN, cd, 0);
        } else {
            p.getCooldowns().addCooldown(ModItems.GAUNTLET.get(), GauntletItem.ABORT_COOLDOWN);
            sound(level, p.getX(), p.getY(), p.getZ(), ModSounds.GAUNTLET_VENT.get(), 0.35F, 1.7F);
            level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.0D, p.getZ(), 6, 0.3D, 0.2D, 0.3D, 0.03D);
            send(p, GauntletAnimPacket.CANCEL, 0, 0);
        }
    }

    /** Steam blasting violently out of both bracers. */
    static void vent(ServerPlayer p, ServerLevel level) {
        double yaw = Math.toRadians(p.yBodyRot);
        for (int side = 0; side < 2; side++) {
            Vec3 at = GauntletItem.bracerPos(p, side);
            double sx = side == 0 ? 1.0D : -1.0D;
            double ox = -Math.cos(yaw) * sx, oz = -Math.sin(yaw) * sx;           // outward
            for (int i = 0; i < 16; i++) {
                double dx = ox * 0.9D + (level.random.nextDouble() - 0.5D) * 0.9D;
                double dy = 0.15D + level.random.nextDouble() * 0.7D;
                double dz = oz * 0.9D + (level.random.nextDouble() - 0.5D) * 0.9D;
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, dx, dy, dz, 0.42D);
            }
            level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 6, 0.12D, 0.12D, 0.12D, 0.14D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 3, 0.1D, 0.1D, 0.1D, 0.06D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.1D, 0.1D, 0.1D, 0.35D);
        }
        sound(level, p.getX(), p.getY(), p.getZ(), ModSounds.GAUNTLET_VENT.get(), 1.2F, 1.0F);
    }

    // ---------------------------------------------------------------- one barrage punch

    private static void punch(ServerPlayer p, Run r) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        int side = r.side;
        r.side ^= 1;
        LivingEntity target = pick(p, eye, look);
        boolean landed = target != null && strike(p, target, look);
        p.causeFoodExhaustion(GauntletItem.BARRAGE_EXHAUSTION);
        if (landed) {
            sound(level, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), ModSounds.GAUNTLET_HIT.get(),
                    0.9F, 0.9F + level.random.nextFloat() * 0.25F);
        } else {
            sound(level, p.getX(), p.getEyeY(), p.getZ(), ModSounds.GAUNTLET_WHIFF.get(), 0.7F, 0.9F + level.random.nextFloat() * 0.3F);
        }
        send(p, GauntletAnimPacket.PUNCH, side, landed ? 1 : 0);
    }

    /** The nearest living thing along the line of the punch, not through walls. */
    private static LivingEntity pick(ServerPlayer p, Vec3 eye, Vec3 look) {
        Level level = p.level();
        double reach = GauntletItem.REACH;
        Vec3 end = eye.add(look.scale(reach));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double max = wall.getType() == HitResult.Type.MISS ? reach : wall.getLocation().distanceTo(eye);
        Vec3 stop = eye.add(look.scale(max));
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, stop).inflate(1.0D),
                e -> e != p && e.isAlive() && !e.isSpectator() && e.isPickable() && e.isAttackable());
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity t : near) {
            if (!canHarm(p, t)) continue;
            AABB box = t.getBoundingBox().inflate(0.25D);                      // a little forgiving
            Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, stop);
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
        }
        return best;
    }

    static boolean canHarm(ServerPlayer p, LivingEntity t) {
        if (t instanceof Player tp && !p.canHarmPlayer(tp)) return false;
        return !(t instanceof TamableAnimal pet && pet.isOwnedBy(p));
    }

    /** Applies a barrage punch to the target: damage that ignores the hit cooldown, a sonic-boom shockwave. */
    private static boolean strike(ServerPlayer p, LivingEntity t, Vec3 look) {
        float dmg = GauntletItem.BARRAGE_DAMAGE;
        MobEffectInstance strength = p.getEffect(MobEffects.DAMAGE_BOOST), weakness = p.getEffect(MobEffects.WEAKNESS);
        if (strength != null) dmg += 0.75F * (strength.getAmplifier() + 1);
        if (weakness != null) dmg -= 0.5F * (weakness.getAmplifier() + 1);
        dmg = Math.max(1.0F, dmg);
        t.invulnerableTime = 0;                                                // lightning fast: no hit cooldown
        boolean ok;
        barrageHit = true;
        try {
            ok = t.hurt(p.damageSources().playerAttack(p), dmg);
        } finally {
            barrageHit = false;
        }
        if (ok) shockwave(p.serverLevel(), t, look, false);
        return ok;
    }

    /** A sonic-boom ring: the vanilla boom sprite, a ring of light thrown out sideways, sparks. */
    static void shockwave(ServerLevel level, LivingEntity t, Vec3 look, boolean big) {
        Vec3 at = new Vec3(t.getX(), t.getY() + t.getBbHeight() * 0.6D, t.getZ()).subtract(look.scale(t.getBbWidth() * 0.5D));
        level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        Vec3 ref = Math.abs(look.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = look.cross(ref).normalize();
        Vec3 up = right.cross(look).normalize();
        int n = big ? 18 : 10;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0D / n;
            Vec3 d = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, d.x, d.y, d.z, big ? 0.24D : 0.16D);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, big ? 12 : 5, 0.15D, 0.15D, 0.15D, 0.4D);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, big ? 10 : 4, 0.2D, 0.2D, 0.2D, 0.3D);
    }

    /** Barrage hits barely move the target (the full vanilla shove would send it flying out of reach). */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (barrageHit) event.setStrength(Math.min(event.getStrength(), GauntletItem.BARRAGE_KNOCKBACK));
    }

    /** No normal swings while the barrage is on. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Player p = event.getEntity();
        if (RUNS.containsKey(p.getUUID()) || GauntletCarry.carrying(p) || GauntletGuard.guarding(p)) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- the normal punch

    /** Heavy punch (10 damage, 1.4 swings a second) while the gauntlet is on and the main hand is empty. */
    private static void weapon(ServerPlayer p, boolean on) {
        AttributeInstance dmg = p.getAttribute(Attributes.ATTACK_DAMAGE), spd = p.getAttribute(Attributes.ATTACK_SPEED);
        if (dmg == null || spd == null) return;
        boolean has = dmg.getModifier(PUNCH_DAMAGE_ID) != null;
        if (on && !has) {
            dmg.addTransientModifier(new AttributeModifier(PUNCH_DAMAGE_ID, "Gauntlet punch", GauntletItem.PUNCH_DAMAGE - 1.0D, AttributeModifier.Operation.ADDITION));
            spd.addTransientModifier(new AttributeModifier(PUNCH_SPEED_ID, "Gauntlet weight", GauntletItem.PUNCH_SPEED - 4.0D, AttributeModifier.Operation.ADDITION));
        } else if (!on && has) {
            dmg.removeModifier(PUNCH_DAMAGE_ID);
            spd.removeModifier(PUNCH_SPEED_ID);
        }
    }

    /** Planted hydraulic stance: slower movement during the wind-up and the barrage. */
    private static void stance(ServerPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.getModifier(STANCE_ID) != null;
        if (on && !has) {
            speed.addTransientModifier(new AttributeModifier(STANCE_ID, "Gauntlet stance", -0.35D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!on && has) {
            speed.removeModifier(STANCE_ID);
        }
    }

    /** A full-power punch (not a barrage hit) shows a shockwave and shoves the target back. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (barrageHit) return;
        DamageSource src = event.getSource();
        if (!(src.getDirectEntity() instanceof ServerPlayer p) || src.getEntity() != p || !"player".equals(src.getMsgId())) return;
        if (GauntletItem.worn(p).isEmpty() || !p.getMainHandItem().isEmpty()) return;
        LivingEntity t = event.getEntity();
        if (event.getAmount() < 8.5F) return;                                  // a half-charged jab: nothing special
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        double yaw = Math.toRadians(p.getYRot());
        t.knockback(GauntletItem.HEAVY_KNOCKBACK, Math.sin(yaw), -Math.cos(yaw));
        shockwave(level, t, look, true);
        sound(level, t.getX(), t.getY() + t.getBbHeight() * 0.5D, t.getZ(), ModSounds.GAUNTLET_PUNCH.get(), 1.2F, 0.9F + level.random.nextFloat() * 0.2F);
        send(p, GauntletAnimPacket.HEAVY, 0, 0);
    }

    // ---------------------------------------------------------------- cleanup

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RUNS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNS.clear();
        barrageHit = false;
    }
}
