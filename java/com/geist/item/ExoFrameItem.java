package com.geist.item;

import com.geist.registry.ModItems;
import com.geist.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The G.E.I.S.T. Exo-Frame MK-I: a prototype hydraulic leg exoskeleton worn in the leggings slot.
 * Runs on Redstone Dust or Copper Ingots (it feeds itself from your inventory, or right-click fuel onto
 * it). While powered and dry it gives the hydraulic leap, fall protection, Strength I and faster mining.
 * Empty, it is dead weight (Slowness II). Deep water shorts it out until you reach dry land.
 * State lives on the stack (synced to every client): Power, Shorted, RebootUntil.
 */
public class ExoFrameItem extends Item implements Equipable {
    public static final int MAX_POWER = 1000, REDSTONE_POWER = 50, COPPER_POWER = 100;
    public static final int LEAP_COST = 60, MINE_COST = 2, HIT_COST = 3;
    public static final int REBOOT_TICKS = 60, LEAP_COOLDOWN = 24, COMPRESS_TICKS = 5, LANDING_TICKS = 10;
    public static final float LOW_POWER = 0.2F;

    public ExoFrameItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- state (safe to read on either side)

    private static CompoundTag tag(ItemStack s) {
        return s.hasTag() ? s.getTag() : new CompoundTag();
    }

    public static int power(ItemStack s) { return tag(s).getInt("Power"); }
    public static boolean shorted(ItemStack s) { return tag(s).getBoolean("Shorted"); }
    public static long rebootUntil(ItemStack s) { return tag(s).getLong("RebootUntil"); }
    public static boolean rebooting(ItemStack s, long now) { return !shorted(s) && now < rebootUntil(s); }

    public static void setPower(ItemStack s, int power) {
        s.getOrCreateTag().putInt("Power", Mth.clamp(power, 0, MAX_POWER));
    }

    public static void setShorted(ItemStack s, boolean shorted) {
        s.getOrCreateTag().putBoolean("Shorted", shorted);
    }

    public static void setRebootUntil(ItemStack s, long until) {
        s.getOrCreateTag().putLong("RebootUntil", until);
    }

    /** Powered, dry and finished booting: every ability works. */
    public static boolean active(ItemStack s, long now) {
        return !s.isEmpty() && power(s) > 0 && !shorted(s) && !rebooting(s, now);
    }

    /** The exo-frame a player is wearing, or EMPTY. */
    public static ItemStack worn(LivingEntity e) {
        ItemStack s = e.getItemBySlot(EquipmentSlot.LEGS);
        return s.getItem() instanceof ExoFrameItem ? s : ItemStack.EMPTY;
    }

    /** Is this player wearing a working exo-frame? */
    public static boolean activeOn(Player p) {
        ItemStack s = worn(p);
        return !s.isEmpty() && active(s, p.level().getGameTime());
    }

    public static int fuelValue(ItemStack s) {
        if (s.is(Items.REDSTONE)) return REDSTONE_POWER;
        if (s.is(Items.COPPER_INGOT)) return COPPER_POWER;
        return 0;
    }

    /** Drain power (creative players run for free). */
    public static void drain(Player p, ItemStack s, int amount) {
        if (!p.getAbilities().instabuild) setPower(s, power(s) - amount);
    }

    /** Counts the fuel the player carries: {redstone, copper}. */
    public static int[] fuelCarried(Player p) {
        int r = 0, c = 0;
        for (ItemStack it : p.getInventory().items) {
            if (it.is(Items.REDSTONE)) r += it.getCount();
            else if (it.is(Items.COPPER_INGOT)) c += it.getCount();
        }
        return new int[]{r, c};
    }

    /** Feeds one fuel item from the inventory into the suit (redstone first). Returns true if it did. */
    public static boolean autoFeed(Player p, ItemStack exo) {
        for (net.minecraft.world.item.Item want : new net.minecraft.world.item.Item[]{Items.REDSTONE, Items.COPPER_INGOT}) {
            for (ItemStack it : p.getInventory().items) {
                if (it.is(want)) {
                    int v = fuelValue(it);
                    if (power(exo) + v > MAX_POWER) return false;
                    it.shrink(1);
                    setPower(exo, power(exo) + v);
                    return true;
                }
            }
        }
        return false;
    }

    /** Standing in water more than two blocks deep? */
    public static boolean deepWater(Player p) {
        if (!p.isInWater()) return false;
        Level level = p.level();
        BlockPos feet = p.blockPosition();
        BlockPos start = level.getFluidState(feet).is(FluidTags.WATER) ? feet : feet.above();
        if (!level.getFluidState(start).is(FluidTags.WATER)) return false;
        int depth = 1;
        for (BlockPos up = start.above(); depth < 4 && level.getFluidState(up).is(FluidTags.WATER); up = up.above()) depth++;
        for (BlockPos down = start.below(); depth < 4 && level.getFluidState(down).is(FluidTags.WATER); down = down.below()) depth++;
        return depth > 2;
    }

    // ---------------------------------------------------------------- equipping and fuelling

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.LEGS;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_NETHERITE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    /** Right-click Redstone Dust or Copper Ingots onto the suit in your inventory to load it. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack exo, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        int value = fuelValue(other);
        if (value <= 0) return false;
        int n = Math.min(other.getCount(), (MAX_POWER - power(exo)) / value);
        if (n <= 0) {
            player.playSound(SoundEvents.DISPENSER_FAIL, 0.5F, 1.4F);
            return true;
        }
        setPower(exo, power(exo) + n * value);
        other.shrink(n);
        player.playSound(ModSounds.EXO_FUEL.get(), 0.8F, 1.0F);
        return true;
    }

    /** Charge and battery level wobble constantly; don't replay the equip animation for it. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return power(stack) < MAX_POWER;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * power(stack) / MAX_POWER);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = power(stack) / (float) MAX_POWER;
        return f < LOW_POWER ? 0xFF9F2B : 0x3FD3FF;
    }

    /** The animated 3D model in the inventory, in hand and on the ground. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.geist.client.exo.ExoItemRenderer.get();
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int pct = Math.round(100.0F * power(stack) / MAX_POWER);
        tooltip.add(Component.translatable("tooltip.geist.exo.prototype").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.geist.exo.power", pct).withStyle(pct < LOW_POWER * 100 ? ChatFormatting.RED : ChatFormatting.AQUA));
        if (shorted(stack)) tooltip.add(Component.translatable("tooltip.geist.exo.shorted").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.geist.exo.leap").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.exo.fall").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.exo.strength").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.exo.fuel").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.geist.exo.drawbacks").withStyle(ChatFormatting.DARK_RED));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    /** A copy with a full battery (for the creative tab). */
    public static ItemStack charged() {
        ItemStack s = new ItemStack(ModItems.EXO_FRAME.get());
        setPower(s, MAX_POWER);
        return s;
    }
}
