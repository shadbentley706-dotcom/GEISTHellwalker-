package com.geist.event;

import com.geist.GeistMod;
import com.geist.item.GauntletItem;
import com.geist.network.GauntletAnimPacket;
import com.geist.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The gauntlet guard (server side): hold use with both hands empty and the forearms come up in front of the face.
 *
 * From the front (the same half circle a shield covers) it blocks melee hits, arrows and other projectiles,
 * explosions (creepers, TNT, fireballs) and the Warden's sonic boom; a blast or a boom still shoves you back, it just
 * does not hurt. Fire, magic and falls are not blocked. Every block heats the hydraulics: a hit costs 6 + 5 per point
 * of damage, a projectile 10, a blast 10 + 2.5 per point of damage, a sonic boom 55. Heat bleeds off (1 a tick with the guard up, 3 with it down). A block that would push the heat past 100
 * breaks the guard: only part of that hit is stopped, the guard drops, steam vents and it cannot go up for 3 seconds.
 * Raise the guard within a quarter second of a hit and it is a parry: no heat (a sonic boom costs 20), and a melee
 * attacker is shoved back and slowed. While guarding you move at about half speed and cannot punch.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID)
public final class GauntletGuard {
    private static final UUID GUARD_SLOW_ID = UUID.fromString("5b0d6e1a-7c34-4d0e-9a52-1c8f3e6b7b05");
    public static final int HIT = 0, PROJECTILE = 1, SONIC = 2, BLAST = 3;

    private static final class Guard {
        boolean up;
        long start, brokenUntil;
        float heat;
    }

    private static final Map<UUID, Guard> GUARDS = new HashMap<>();
    /** Damage multiplier for the hit that broke a guard (applied when the hurt goes through). */
    private static final Map<UUID, Float> LEAK = new HashMap<>();

    private GauntletGuard() {}

    /** Is this player (server side) guarding? */
    public static boolean guarding(Entity p) {
        if (p.level().isClientSide) return false;
        Guard g = GUARDS.get(p.getUUID());
        return g != null && g.up;
    }

    private static boolean able(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && !GauntletItem.worn(p).isEmpty()
                && p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty();
    }

    // ---------------------------------------------------------------- up and down (from the client)

    public static void raise(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        Guard g = GUARDS.computeIfAbsent(p.getUUID(), k -> new Guard());
        if (g.up || now < g.brokenUntil || !able(p)) return;
        if (GauntletEvents.barraging(p) || GauntletCarry.carrying(p) || p.getCooldowns().isOnCooldown(GauntletItem.worn(p).getItem())) return;
        g.up = true;
        g.start = now;
        slow(p, true);
        p.setSprinting(false);
        GauntletEvents.sound(level, p.getX(), p.getY(), p.getZ(), ModSounds.GAUNTLET_WINDUP.get(), 0.35F, 1.9F);
        GauntletEvents.send(p, GauntletAnimPacket.GUARD, 1, 0);
    }

    public static void lower(ServerPlayer p) {
        Guard g = GUARDS.get(p.getUUID());
        if (g == null || !g.up) return;
        g.up = false;
        slow(p, false);
        GauntletEvents.send(p, GauntletAnimPacket.GUARD, 0, 0);
    }

    private static void slow(ServerPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(GUARD_SLOW_ID);
        if (on) speed.addTransientModifier(new AttributeModifier(GUARD_SLOW_ID, "Gauntlet guard", -GauntletItem.GUARD_SLOW, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        LEAK.remove(p.getUUID());                                              // a leak not used this tick is stale
        Guard g = GUARDS.get(p.getUUID());
        if (g == null) return;
        g.heat = Math.max(0.0F, g.heat - (g.up ? GauntletItem.GUARD_COOL_UP : GauntletItem.GUARD_COOL_DOWN));
        if (g.up) {
            if (!able(p) || GauntletEvents.barraging(p) || GauntletCarry.carrying(p)) {
                lower(p);
            } else {
                p.setSprinting(false);
            }
        }
        if (!g.up && g.heat <= 0.0F && p.serverLevel().getGameTime() >= g.brokenUntil) GUARDS.remove(p.getUUID());
    }

    // ---------------------------------------------------------------- blocking

    /** Is the source in front (the half circle a shield covers)? */
    private static boolean inFront(ServerPlayer p, Vec3 from) {
        Vec3 to = from.vectorTo(p.position());
        to = new Vec3(to.x, 0.0D, to.z);
        if (to.lengthSqr() < 1.0E-6D) return true;
        return to.normalize().dot(p.getViewVector(1.0F)) < 0.0D;
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Guard g = GUARDS.get(p.getUUID());
        if (g == null || !g.up) return;
        DamageSource s = event.getSource();
        boolean sonic = s.is(DamageTypes.SONIC_BOOM);
        boolean blast = s.is(DamageTypeTags.IS_EXPLOSION);
        boolean projectile = !blast && s.is(DamageTypeTags.IS_PROJECTILE);
        if (!sonic && !blast && (s.getDirectEntity() == null || s.is(DamageTypeTags.BYPASSES_SHIELD))) return;
        Vec3 from = s.getSourcePosition();
        if (from == null || !inFront(p, from)) return;
        if (event.getAmount() <= 0.0F || p.getAbilities().invulnerable) return;   // snowballs, eggs, creative: nothing to block

        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        boolean parry = now - g.start <= GauntletItem.PARRY_TICKS;
        int kind = sonic ? SONIC : blast ? BLAST : projectile ? PROJECTILE : HIT;
        float cost = switch (kind) {
            case SONIC -> parry ? GauntletItem.GUARD_SONIC_PARRY : GauntletItem.GUARD_SONIC;
            case BLAST -> parry ? GauntletItem.GUARD_BLAST_PARRY : GauntletItem.GUARD_BLAST_BASE + GauntletItem.GUARD_BLAST_PER_DAMAGE * event.getAmount();
            case PROJECTILE -> parry ? 0.0F : GauntletItem.GUARD_PROJECTILE;
            default -> parry ? 0.0F : GauntletItem.GUARD_MELEE_BASE + GauntletItem.GUARD_MELEE_PER_DAMAGE * event.getAmount();
        };
        Vec3 dir = from.vectorTo(p.position());                                // from the attacker to the wearer
        dir = dir.lengthSqr() < 1.0E-6D ? p.getViewVector(1.0F).scale(-1.0D) : dir.normalize();

        if (cost > 0.0F && g.heat + cost >= GauntletItem.GUARD_HEAT_MAX) {
            // the hydraulics give out: part of this hit is still stopped, the rest gets through
            float stopped = Mth.clamp((GauntletItem.GUARD_HEAT_MAX - g.heat) / cost, 0.0F, 1.0F);
            LEAK.put(p.getUUID(), 1.0F - stopped);
            breakGuard(p, g, now);
            return;
        }
        event.setCanceled(true);
        g.heat += cost;
        if (!p.getAbilities().instabuild) p.causeFoodExhaustion(0.4F);

        if (kind == HIT) {
            if (parry && s.getEntity() instanceof LivingEntity attacker && attacker != p) {
                attacker.knockback(1.0D, p.getX() - attacker.getX(), p.getZ() - attacker.getZ());
                attacker.hurtMarked = true;                                    // a player attacker needs the shove sent
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            } else {                                                           // the wearer slides back a little
                p.setDeltaMovement(p.getDeltaMovement().add(dir.x * 0.3D, 0.05D, dir.z * 0.3D));
                p.hurtMarked = true;
            }
        }
        if (kind == SONIC || kind == BLAST) p.hurtMarked = true;               // a boom or a blast still shoves: send that motion
        effects(level, p, dir, kind, parry);
        GauntletEvents.send(p, GauntletAnimPacket.BLOCK, kind + (parry ? 10 : 0), Math.round(g.heat));
    }

    /** The hit that broke the guard only does the part the guard could not stop. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Float leak = LEAK.remove(p.getUUID());
        if (leak != null) event.setAmount(event.getAmount() * leak);
    }

    private static void breakGuard(ServerPlayer p, Guard g, long now) {
        g.up = false;
        g.heat = GauntletItem.GUARD_HEAT_MAX;
        g.brokenUntil = now + GauntletItem.GUARD_BREAK_TICKS;
        slow(p, false);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
        ServerLevel level = p.serverLevel();
        GauntletEvents.vent(p, level);
        GauntletEvents.sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BREAK, 1.0F, 0.8F);
        GauntletEvents.send(p, GauntletAnimPacket.GUARD_BREAK, GauntletItem.GUARD_BREAK_TICKS, 0);
    }

    /** Sparks off the forearms, the clang, and a shockwave ring for the big ones. */
    private static void effects(ServerLevel level, ServerPlayer p, Vec3 dir, int kind, boolean parry) {
        Vec3 at = p.position().add(0.0D, p.getBbHeight() * 0.62D, 0.0D).subtract(dir.scale(0.55D));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, parry ? 18 : 10, 0.15D, 0.15D, 0.15D, 0.45D);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, parry ? 10 : 5, 0.2D, 0.2D, 0.2D, 0.35D);
        if (kind == SONIC || kind == BLAST || parry) GauntletEvents.shockwave(level, p, dir, true);
        switch (kind) {
            case BLAST -> {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.3D, 0.3D, 0.3D, 0.05D);
                GauntletEvents.sound(level, at.x, at.y, at.z, ModSounds.GAUNTLET_IMPACT.get(), 1.4F, 0.6F);
                GauntletEvents.sound(level, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, 1.0F, 0.5F);
            }
            case SONIC -> {
                GauntletEvents.sound(level, at.x, at.y, at.z, ModSounds.GAUNTLET_IMPACT.get(), 1.3F, 0.7F);
                GauntletEvents.sound(level, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, 1.0F, 0.6F);
            }
            case PROJECTILE -> {
                GauntletEvents.sound(level, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, 0.9F, 1.4F);
                GauntletEvents.sound(level, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, 0.2F, 2.0F);
            }
            default -> {
                GauntletEvents.sound(level, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, 0.35F, parry ? 2.0F : 1.6F);
                GauntletEvents.sound(level, at.x, at.y, at.z, ModSounds.GAUNTLET_HIT.get(), 0.6F, 1.4F);
            }
        }
        if (parry) GauntletEvents.sound(level, at.x, at.y, at.z, ModSounds.GAUNTLET_PUNCH.get(), 1.0F, 1.3F);
    }

    // ---------------------------------------------------------------- cleanup

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GUARDS.remove(event.getEntity().getUUID());
        LEAK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GUARDS.clear();
        LEAK.clear();
    }
}
