package com.geist.entity;

import com.geist.registry.ModEntities;
import com.geist.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** The thrown smoke pellet: it pops into a Smoke Pellet Matrix cloud the moment it lands. */
public class SmokePelletEntity extends ThrowableItemProjectile {
    public SmokePelletEntity(EntityType<? extends SmokePelletEntity> type, Level level) {
        super(type, level);
    }

    public SmokePelletEntity(Level level, LivingEntity thrower) {
        super(ModEntities.SMOKE_PELLET.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SMOKE_PELLET.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > 40) pop();      // never fly off forever
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) pop();
    }

    private void pop() {
        if (this.isRemoved()) return;
        ServerLevel level = (ServerLevel) this.level();
        SmokeCloudEntity cloud = new SmokeCloudEntity(ModEntities.SMOKE_CLOUD.get(), level);
        cloud.setPos(this.getX(), Math.floor(this.getY() + 0.01D), this.getZ());
        cloud.blindInside();                                                   // instantly
        level.addFreshEntity(cloud);
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2D, getZ(), 20, 0.4D, 0.3D, 0.4D, 0.08D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3D, getZ(), 30, 0.8D, 0.5D, 0.8D, 0.05D);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.8F);
        this.discard();
    }
}
