package com.geist.item;

import com.geist.entity.LightArrowEntity;
import com.geist.network.GeistNetwork;
import com.geist.network.ZeroAnimPacket;
import com.geist.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The G.E.I.S.T. Zero Light: a compound bow that turns ordinary arrows into light arrows. It draws faster than a
 * bow (0.8 s), the arrows fly faster (3.6 against 3.0), flatter and almost dead straight, so full-draw shots hit
 * hard and land where you aim.
 *
 * Three firing modes, cycled with the mode key:
 *  - STANDARD: the pure precision shot.
 *  - SHOCKWAVE: a full-draw shot bursts into a short energy pulse where it lands (radius 3: 5 damage and a hard shove
 *    at the centre, less further out, interrupting bows, shields and eating). Costs 40 of the bow's 100 energy and
 *    needs 2.5 s between pulses; if either is short, the shot goes out as a standard arrow.
 *  - RICOCHET: arrows bounce off blocks up to 3 times; they start at 75% damage and lose 20% per bounce.
 * Energy refills on its own (5 a second, wherever the bow is in your inventory).
 */
public class ZeroLightItem extends BowItem {
    public static final int STANDARD = LightArrowEntity.STANDARD, SHOCKWAVE = LightArrowEntity.SHOCKWAVE, RICOCHET = LightArrowEntity.RICOCHET;
    public static final String[] MODE_NAMES = {"STANDARD", "SHOCKWAVE", "RICOCHET"};
    public static final int DURABILITY = 640;
    // ---- the shot
    public static final int DRAW_TICKS = 16;
    public static final float SPEED = 3.6F, INACCURACY = 0.15F;
    public static final double GRAVITY = 0.03D, BASE_DAMAGE = 2.0D;
    // ---- shockwave
    public static final int ENERGY_MAX = 100, SHOCK_COST = 40, SHOCK_COOLDOWN = 50, ENERGY_PER_SECOND = 5;
    public static final double SHOCK_RADIUS = 3.0D, SHOCK_KNOCKBACK = 1.1D;
    public static final float SHOCK_DAMAGE = 5.0F;
    // ---- ricochet
    public static final int MAX_BOUNCES = 3;
    public static final double RICOCHET_START = 0.75D, RICOCHET_DAMAGE = 0.8D, RICOCHET_SPEED = 0.88D;

    public ZeroLightItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- state on the stack

    public static int mode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? STANDARD : Mth.clamp(tag.getInt("Mode"), STANDARD, RICOCHET);
    }

    public static int energy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains("Energy") ? ENERGY_MAX : Mth.clamp(tag.getInt("Energy"), 0, ENERGY_MAX);
    }

    private static void setEnergy(ItemStack stack, int e) {
        stack.getOrCreateTag().putInt("Energy", Mth.clamp(e, 0, ENERGY_MAX));
    }

    /** Game time when the next shockwave may go. */
    public static long shockReady(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0L : tag.getLong("ShockReady");
    }

    /** Can a full-draw shot right now be a shockwave? */
    public static boolean shockArmed(ItemStack stack, long now) {
        return energy(stack) >= SHOCK_COST && now >= shockReady(stack);
    }

    /** Draw power 0..1 after this many ticks (the vanilla curve, over 16 ticks instead of 20). */
    public static float power(float ticks) {
        float f = ticks / DRAW_TICKS;
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(f, 1.0F);
    }

    // ---------------------------------------------------------------- mode key

    public static void switchMode(ServerPlayer p) {
        InteractionHand hand = p.getMainHandItem().getItem() instanceof ZeroLightItem ? InteractionHand.MAIN_HAND
                : p.getOffhandItem().getItem() instanceof ZeroLightItem ? InteractionHand.OFF_HAND : null;
        if (hand == null || p.isSpectator()) return;
        ItemStack stack = p.getItemInHand(hand);
        int to = (mode(stack) + 1) % 3;
        stack.getOrCreateTag().putInt("Mode", to);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ZERO_MODE.get(), SoundSource.PLAYERS, 0.6F, 1.0F + 0.1F * to);
        GeistNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new ZeroAnimPacket(p.getId(), ZeroAnimPacket.MODE, to, 0));
    }

    // ---------------------------------------------------------------- drawing and firing

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResultHolder<ItemStack> r = super.use(level, player, hand);
        if (r.getResult().consumesAction() && !level.isClientSide) {             // the cams turn over, the limbs load
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.ZERO_DRAW.get(), SoundSource.PLAYERS, 0.55F, 1.0F);
        }
        return r;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!(entity instanceof Player player)) return;
        boolean creative = player.getAbilities().instabuild;
        boolean infiniteBow = creative || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack) > 0;
        ItemStack ammo = player.getProjectile(stack);
        int charge = this.getUseDuration(stack) - remaining;
        charge = net.minecraftforge.event.ForgeEventFactory.onArrowLoose(stack, level, player, charge, !ammo.isEmpty() || infiniteBow);
        if (charge < 0) return;
        if (ammo.isEmpty() && !infiniteBow) return;
        if (ammo.isEmpty()) ammo = new ItemStack(Items.ARROW);
        float power = power(charge);
        if (power < 0.1F) return;
        boolean free = creative || (ammo.getItem() instanceof ArrowItem arrowItem && arrowItem.isInfinite(ammo, stack, player));

        int kind = mode(stack);
        long now = level.getGameTime();
        if (kind == SHOCKWAVE && (power < 1.0F || !shockArmed(stack, now))) kind = STANDARD;   // not charged: a plain shot
        if (!level.isClientSide) {
            LightArrowEntity arrow = new LightArrowEntity(level, player, kind);
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * SPEED, INACCURACY);
            if (power >= 1.0F) arrow.setCritArrow(true);
            double damage = BASE_DAMAGE * (kind == RICOCHET ? RICOCHET_START : 1.0D);
            int pw = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
            if (pw > 0) damage += pw * 0.5D + 0.5D;
            arrow.setBaseDamage(damage);
            int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack);
            if (punch > 0) arrow.setKnockback(punch);
            if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack) > 0) arrow.setSecondsOnFire(100);
            stack.hurtAndBreak(1, player, pl -> pl.broadcastBreakEvent(player.getUsedItemHand()));
            if (free || (creative && (ammo.is(Items.SPECTRAL_ARROW) || ammo.is(Items.TIPPED_ARROW)))) {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
            if (kind == SHOCKWAVE) {
                setEnergy(stack, energy(stack) - SHOCK_COST);
                stack.getOrCreateTag().putLong("ShockReady", now + SHOCK_COOLDOWN);
            }
            level.addFreshEntity(arrow);
            if (player instanceof ServerPlayer sp) {
                GeistNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                        new ZeroAnimPacket(sp.getId(), ZeroAnimPacket.FIRE, kind, Math.round(power * 100.0F)));
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.ZERO_FIRE.get(), SoundSource.PLAYERS,
                0.9F, 1.15F - 0.2F * power + (kind == SHOCKWAVE ? -0.1F : 0.0F));
        if (!free && !creative) {
            ammo.shrink(1);
            if (ammo.isEmpty()) player.getInventory().removeItem(ammo);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /** The energy cell refills on its own: 5 a second, wherever the bow is. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20L != 0L) return;
        int e = energy(stack);
        if (e < ENERGY_MAX) setEnergy(stack, e + ENERGY_PER_SECOND);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();          // energy ticking up is not a new bow
    }

    @Override
    public int getDefaultProjectileRange() {
        return 20;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.geist.zero_light.mode", MODE_NAMES[mode(stack)]).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.geist.zero_light.energy", energy(stack), ENERGY_MAX).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.geist.zero_light.core").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.zero_light.shockwave").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.zero_light.ricochet").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.zero_light.switch").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.geist.client.zero.ZeroRenderer.get();
            }

            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.player.LocalPlayer player,
                                                   net.minecraft.world.entity.HumanoidArm arm, ItemStack itemInHand, float partialTick,
                                                   float equipProcess, float swingProcess) {
                return com.geist.client.zero.ZeroRenderer.handTransform(poseStack, player, arm, itemInHand, partialTick, equipProcess, swingProcess);
            }
        });
    }
}
