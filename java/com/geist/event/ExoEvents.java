package com.geist.event;

import com.geist.GeistMod;
import com.geist.item.ExoFrameItem;
import com.geist.network.ExoAnimPacket;
import com.geist.network.GeistNetwork;
import com.geist.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The exo-frame's rules on the server: fuel feeding, the water short and reboot, Strength I, dead weight,
 * fall protection, faster (power-draining) mining and hitting, and the leap and landing effects.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID)
public final class ExoEvents {
    public static final float MINING_BOOST = 1.5F, SAFE_FALL = 15.0F;
    private static final Map<UUID, Long> LAST_LEAP = new HashMap<>();
    private static final Set<UUID> DEAD = new HashSet<>();

    private ExoEvents() {}

    private static void tell(Player p, String key, ChatFormatting... style) {
        p.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }

    // ---------------------------------------------------------------- every tick

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        ItemStack exo = ExoFrameItem.worn(p);
        if (exo.isEmpty() || p.isSpectator()) {
            DEAD.remove(p.getUUID());
            return;
        }
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        boolean creative = p.getAbilities().instabuild;

        // water deeper than two blocks shorts the suit out until you reach dry land
        if (!ExoFrameItem.shorted(exo) && ExoFrameItem.deepWater(p)) {
            ExoFrameItem.setShorted(exo, true);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.7D, p.getZ(), 30, 0.35D, 0.5D, 0.35D, 0.35D);
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, p.getX(), p.getY() + 0.4D, p.getZ(), 20, 0.3D, 0.3D, 0.3D, 0.1D);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.EXO_SHORT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            tell(p, "message.geist.exo.shorted", ChatFormatting.BLUE, ChatFormatting.BOLD);
        } else if (ExoFrameItem.shorted(exo) && p.onGround() && !p.isInWater()) {
            ExoFrameItem.setShorted(exo, false);
            ExoFrameItem.setRebootUntil(exo, now + ExoFrameItem.REBOOT_TICKS);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.EXO_REBOOT.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
            tell(p, "message.geist.exo.rebooting", ChatFormatting.AQUA);
        } else if (ExoFrameItem.rebootUntil(exo) == now) {
            tell(p, "message.geist.exo.online", ChatFormatting.AQUA);
        }

        // feed itself from the inventory when it runs too low to leap
        if (!creative && ExoFrameItem.power(exo) < ExoFrameItem.LEAP_COST && ExoFrameItem.autoFeed(p, exo)) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.EXO_FUEL.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
            if (ExoFrameItem.power(exo) <= ExoFrameItem.REDSTONE_POWER + ExoFrameItem.COPPER_POWER) {
                tell(p, "message.geist.exo.fuel_loaded", ChatFormatting.DARK_AQUA);
            }
        }

        if (now % 20 == 0) {
            if (ExoFrameItem.active(exo, now)) {                     // Strength I while it runs
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 50, 0, true, false, true));
            }
            if (ExoFrameItem.power(exo) <= 0 && !creative) {         // out of fuel: dead weight
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1, true, false, true));
                if (DEAD.add(p.getUUID())) {
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.EXO_SHORT.get(), SoundSource.PLAYERS, 0.6F, 0.6F);
                    tell(p, "message.geist.exo.dead_weight", ChatFormatting.RED, ChatFormatting.BOLD);
                }
            } else {
                DEAD.remove(p.getUUID());
            }
        }
    }

    // ---------------------------------------------------------------- fall protection, strength, mining

    /** Shock-absorbing pistons: no fall damage up to 15 blocks, half damage beyond. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player p) || !ExoFrameItem.activeOn(p)) return;
        if (event.getDistance() <= SAFE_FALL) event.setDamageMultiplier(0.0F);
        else event.setDamageMultiplier(event.getDamageMultiplier() * 0.5F);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (ExoFrameItem.activeOn(event.getEntity())) event.setNewSpeed(event.getNewSpeed() * MINING_BOOST);
    }

    /** Heavy lifting drains power: every block broken... */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player p = event.getPlayer();
        if (p == null || p.level().isClientSide || !ExoFrameItem.activeOn(p)) return;
        ExoFrameItem.drain(p, ExoFrameItem.worn(p), ExoFrameItem.MINE_COST);
    }

    /** ...and every swing that lands. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || !ExoFrameItem.activeOn(p)) return;
        ExoFrameItem.drain(p, ExoFrameItem.worn(p), ExoFrameItem.HIT_COST);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_LEAP.remove(event.getEntity().getUUID());
        DEAD.remove(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------- leap and landing (sent by the client)

    public static void leap(ServerPlayer p) {
        ItemStack exo = ExoFrameItem.worn(p);
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (exo.isEmpty() || !ExoFrameItem.active(exo, now)) return;
        Long last = LAST_LEAP.get(p.getUUID());
        if (last != null && now - last < ExoFrameItem.LEAP_COOLDOWN - 4) return;
        if (!p.getAbilities().instabuild && ExoFrameItem.power(exo) < ExoFrameItem.LEAP_COST) return;
        ExoFrameItem.drain(p, exo, ExoFrameItem.LEAP_COST);
        LAST_LEAP.put(p.getUUID(), now);
        double x = p.getX(), y = p.getY(), z = p.getZ();
        // steam explodes out of the boots
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2.0D / 18.0D;
            level.sendParticles(ParticleTypes.CLOUD, x, y + 0.1D, z, 0, Math.cos(a), 0.08D, Math.sin(a), 0.32D);
        }
        level.sendParticles(ParticleTypes.POOF, x, y + 0.2D, z, 16, 0.25D, 0.1D, 0.25D, 0.08D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.1D, z, 8, 0.2D, 0.05D, 0.2D, 0.04D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.1D, z, 2, 0.15D, 0.0D, 0.15D, 0.01D);
        dust(level, x, y, z, 26, 0.18D);
        level.playSound(null, x, y, z, ModSounds.EXO_LEAP.get(), SoundSource.PLAYERS, 1.3F, 0.95F + level.random.nextFloat() * 0.1F);
        GeistNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new ExoAnimPacket(p.getId(), ExoAnimPacket.LEAP, 1.0F));
    }

    public static void landed(ServerPlayer p, float drop) {
        ItemStack exo = ExoFrameItem.worn(p);
        if (exo.isEmpty()) return;
        ServerLevel level = p.serverLevel();
        float d = Mth.clamp(drop, 0.0F, 40.0F);
        double x = p.getX(), y = p.getY(), z = p.getZ();
        // a ring of dirt kicked out by the impact, and the pistons venting
        BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.2D, z));
        if (!ground.isAir()) {
            for (int i = 0; i < 28; i++) {
                double a = i * Math.PI * 2.0D / 28.0D;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y + 0.05D, z, 0, Math.cos(a), 0.25D, Math.sin(a), 0.25D + d * 0.012D);
            }
        }
        dust(level, x, y, z, 20 + Math.round(d), 0.15D);
        level.sendParticles(ParticleTypes.CLOUD, x, y + 0.5D, z, 14, 0.35D, 0.15D, 0.35D, 0.06D);
        level.sendParticles(ParticleTypes.POOF, x, y + 0.1D, z, 10 + Math.round(d * 0.5F), 0.5D, 0.05D, 0.5D, 0.05D);
        level.playSound(null, x, y, z, ModSounds.EXO_LAND.get(), SoundSource.PLAYERS, 0.9F + Math.min(d, 25.0F) * 0.03F, 1.05F - Math.min(d, 25.0F) * 0.01F);
        GeistNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new ExoAnimPacket(p.getId(), ExoAnimPacket.LAND, d));
    }

    private static void dust(ServerLevel level, double x, double y, double z, int count, double speed) {
        BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.2D, z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y + 0.1D, z, count, 0.45D, 0.05D, 0.45D, speed);
        }
    }
}
