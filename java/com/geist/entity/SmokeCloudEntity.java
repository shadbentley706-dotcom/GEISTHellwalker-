package com.geist.entity;

import com.geist.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Smoke Pellet Matrix: a 7x7 cloud of dense dark smoke (about 4 blocks tall) lasting 10 seconds.
 * Creatures inside are blinded and lose track of their targets. Visor wearers see straight through it.
 */
public class SmokeCloudEntity extends Entity {
    public static final int LIFE = 200;
    public static final double HALF = 3.5D, HEIGHT = 4.0D;

    public SmokeCloudEntity(EntityType<? extends SmokeCloudEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public AABB area() {
        return new AABB(getX() - HALF, getY() - 0.5D, getZ() - HALF, getX() + HALF, getY() + HEIGHT, getZ() + HALF);
    }

    /** 1 at full strength, fading to 0 over the last two seconds. */
    public float strength() {
        return Math.max(0.0F, Math.min(1.0F, (LIFE - this.tickCount) / 40.0F));
    }

    public static boolean wearsVisor(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GEIST_VISOR.get());
    }

    /** Blind everything inside and make mobs forget who they were chasing. */
    public void blindInside() {
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, area())) {
            if (e instanceof Player p && (wearsVisor(p) || p.isSpectator())) continue;    // tactical gear sees through it
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, true));
            if (e instanceof Mob m && m.getTarget() != null) {
                m.setTarget(null);
                m.getNavigation().stop();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.geist.client.SmokeVision.cloudTick(this));
            return;
        }
        if (this.tickCount % 5 == 0) blindInside();
        if (this.tickCount >= LIFE) this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.tickCount = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.tickCount);
    }
}
