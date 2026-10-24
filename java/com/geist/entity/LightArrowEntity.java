package com.geist.entity;

import com.geist.item.ZeroLightItem;
import com.geist.network.GeistNetwork;
import com.geist.network.ZeroAnimPacket;
import com.geist.registry.ModEntities;
import com.geist.registry.ModSounds;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * A Zero Light arrow: fast, flat (a little over half the gravity of a normal arrow) and glowing, with a trail of
 * light. Three kinds:
 *  - standard: just a very good arrow;
 *  - shockwave: bursts into a compressed energy pulse where it hits (radius 3): full damage and a hard shove at the
 *    centre, less further out, and anyone drawing a bow, raising a shield or eating is interrupted;
 *  - ricochet: bounces off blocks up to 3 times (each bounce keeps 88% of the speed and 80% of the damage) and never
 *    hits the archer who fired it.
 */
public class LightArrowEntity extends AbstractArrow {
    public static final int STANDARD = 0, SHOCKWAVE = 1, RICOCHET = 2;
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(LightArrowEntity.class, EntityDataSerializers.BYTE);
    private static final Vector3f[] TRAIL = {new Vector3f(0.3F, 0.88F, 1.0F), new Vector3f(0.85F, 0.97F, 1.0F), new Vector3f(0.78F, 0.45F, 1.0F)};
    private int bounces;
    /** Where the last bounce left it: the rest of that tick's move is skipped, so the new path is collision-checked from here. */
    private Vec3 bounceAt;

    public LightArrowEntity(EntityType<? extends LightArrowEntity> type, Level level) {
        super(type, level);
    }

    public LightArrowEntity(Level level, LivingEntity shooter, int kind) {
        super(ModEntities.LIGHT_ARROW.get(), shooter, level);
        this.entityData.set(KIND, (byte) kind);
        this.setNoGravity(true);                                                  // its own, lighter gravity is applied in tick()
        this.setSoundEvent(ModSounds.ZERO_HIT.get());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(KIND, (byte) STANDARD);
    }

    public int kind() {
        return Mth.clamp(this.entityData.get(KIND), STANDARD, RICOCHET);
    }

    public int bounces() {
        return bounces;
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Kind", (byte) kind());
        tag.putInt("Bounces", bounces);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(KIND, tag.getByte("Kind"));
        this.bounces = tag.getInt("Bounces");
    }

    // ---------------------------------------------------------------- flight

    @Override
    public void tick() {
        super.tick();
        if (bounceAt != null) {
            this.setPos(bounceAt.x, bounceAt.y, bounceAt.z);
            bounceAt = null;
        }
        if (this.isRemoved() || this.inGround) return;
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.x, v.y - ZeroLightItem.GRAVITY, v.z);
        if (this.level().isClientSide) trail();
    }

    /** A thin line of light behind it (brighter and sparking for a shockwave arrow). */
    private void trail() {
        Level level = this.level();
        int k = kind();
        DustParticleOptions dust = new DustParticleOptions(TRAIL[k], k == SHOCKWAVE ? 1.0F : 0.7F);
        for (int i = 0; i < 3; i++) {
            double f = i / 3.0D;
            level.addParticle(dust, Mth.lerp(f, this.xo, this.getX()), Mth.lerp(f, this.yo, this.getY()), Mth.lerp(f, this.zo, this.getZ()), 0.0D, 0.0D, 0.0D);
        }
        if (k == SHOCKWAVE && this.random.nextFloat() < 0.4F) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (kind() == RICOCHET && e == this.getOwner()) return false;          // a bounce never comes back to bite the archer
        return super.canHitEntity(e);
    }

    // ---------------------------------------------------------------- hits

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (kind() == RICOCHET && bounces < ZeroLightItem.MAX_BOUNCES && this.getDeltaMovement().lengthSqr() > 0.25D) {
            bounce(hit);
            return;
        }
        super.onHitBlock(hit);
        if (kind() == SHOCKWAVE && !this.level().isClientSide) {
            pulse(hit.getLocation(), null, this.getDeltaMovement());
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        int k = kind();
        Vec3 where = this.position(), flight = this.getDeltaMovement();
        super.onHitEntity(hit);
        if (this.level().isClientSide) return;
        if (this.getOwner() instanceof ServerPlayer shooter && target instanceof LivingEntity) {   // a hit marker for the archer
            GeistNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> shooter), new ZeroAnimPacket(shooter.getId(), ZeroAnimPacket.HIT, bounces, k));
        }
        if (k == SHOCKWAVE) {
            pulse(where, target, flight);
            this.discard();
        }
    }

    /** Reflect off the face it hit, a little slower and weaker, with a spark and a ping. */
    private void bounce(BlockHitResult hit) {
        Vec3 v = this.getDeltaMovement();
        Vec3i n = hit.getDirection().getNormal();
        Vec3 normal = new Vec3(n.getX(), n.getY(), n.getZ());
        Vec3 out = v.subtract(normal.scale(2.0D * v.dot(normal))).scale(ZeroLightItem.RICOCHET_SPEED);
        Vec3 at = hit.getLocation().add(normal.scale(0.05D));
        this.setPos(at.x, at.y, at.z);
        this.bounceAt = at;
        this.setDeltaMovement(out);
        double h = out.horizontalDistance();
        this.setYRot((float) (Mth.atan2(out.x, out.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(out.y, h) * Mth.RAD_TO_DEG));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.bounces++;
        this.setBaseDamage(this.getBaseDamage() * ZeroLightItem.RICOCHET_DAMAGE);
        this.hasImpulse = true;
        if (this.level() instanceof ServerLevel level) {
            BlockState state = level.getBlockState(hit.getBlockPos());
            state.onProjectileHit(level, state, hit, this);                     // bells ring, targets light up
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.05D, 0.05D, 0.05D, 0.25D);
            level.sendParticles(new DustParticleOptions(TRAIL[RICOCHET], 1.0F), at.x, at.y, at.z, 5, 0.1D, 0.1D, 0.1D, 0.0D);
            level.playSound(null, at.x, at.y, at.z, ModSounds.ZERO_RICOCHET.get(), SoundSource.NEUTRAL, 0.9F, 1.1F + 0.15F * bounces);
        }
    }

    /** The shockwave: a compressed energy pulse, not a blast. Hurts and shoves what is close, interrupts what it shoves. */
    private void pulse(Vec3 at, Entity direct, Vec3 flight) {
        if (!(this.level() instanceof ServerLevel level)) return;
        Entity owner = this.getOwner();
        DamageSource src = this.damageSources().explosion(this, owner);
        double r = ZeroLightItem.SHOCK_RADIUS;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), x -> x.isAlive() && !x.isSpectator() && x != owner)) {
            if (owner instanceof Player op) {
                if (e instanceof Player tp && !op.canHarmPlayer(tp)) continue;
                if (e instanceof TamableAnimal pet && pet.isOwnedBy(op)) continue;
            }
            Vec3 c = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
            double d = e == direct ? 0.0D : c.distanceTo(at);
            if (d > r) continue;
            float fall = (float) (1.0D - d / r);
            e.invulnerableTime = 0;
            e.hurt(src, Math.max(1.0F, ZeroLightItem.SHOCK_DAMAGE * fall));
            double kx = at.x - e.getX(), kz = at.z - e.getZ();
            if (e == direct || kx * kx + kz * kz < 1.0E-4D) {                    // the one it hit: shoved the way the arrow flew
                kx = -flight.x;
                kz = -flight.z;
            }
            e.knockback(ZeroLightItem.SHOCK_KNOCKBACK * (0.4D + 0.6D * fall), kx, kz);
            e.setDeltaMovement(e.getDeltaMovement().add(0.0D, 0.25D * fall, 0.0D));
            e.hurtMarked = true;
            if (e.isUsingItem()) e.stopUsingItem();                                // knocked out of aim
        }
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        for (int i = 0; i < 28; i++) {                                         // a flat ring racing outward
            double a = i * Math.PI * 2.0D / 28.0D;
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.1D, at.z, 0, Math.cos(a), 0.0D, Math.sin(a), 0.38D);
        }
        level.sendParticles(new DustParticleOptions(TRAIL[SHOCKWAVE], 1.6F), at.x, at.y, at.z, 30, r * 0.35D, 0.4D, r * 0.35D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 24, 0.4D, 0.4D, 0.4D, 0.6D);
        level.playSound(null, at.x, at.y, at.z, ModSounds.ZERO_SHOCKWAVE.get(), SoundSource.NEUTRAL, 1.3F, 0.95F + 0.1F * this.random.nextFloat());
    }
}
