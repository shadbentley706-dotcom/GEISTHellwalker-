package com.geist.client.mt7c;

import com.geist.item.Mt7cItem;
import com.geist.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** The rising low hum while an MT-7C charges: follows the holder, grows louder and higher, stops the moment they let go. */
public class Mt7cChargeSound extends AbstractTickableSoundInstance {
    private final LivingEntity holder;

    public Mt7cChargeSound(LivingEntity holder) {
        super(ModSounds.MT7C_CHARGE.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.holder = holder;
        this.looping = false;
        this.delay = 0;
        this.volume = 0.25F;
        this.pitch = 0.75F;
        this.x = holder.getX();
        this.y = holder.getY();
        this.z = holder.getZ();
    }

    @Override
    public void tick() {
        if (holder.isRemoved() || !Mt7cAnim.charging(holder)) {
            stop();
            return;
        }
        float c = Mth.clamp(holder.getTicksUsingItem() / (float) Mt7cItem.FULL_CHARGE, 0.0F, 1.0F);
        this.volume = 0.25F + 0.6F * c;
        this.pitch = 0.75F + 0.45F * c;
        this.x = holder.getX();
        this.y = holder.getY();
        this.z = holder.getZ();
    }
}
