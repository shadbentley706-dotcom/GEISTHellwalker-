package com.geist.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The CIA MT-7C modular multitool: one diamond-tier tool with three heads (pickaxe, shovel, hoe), swapped with the
 * mode key. It digs a little faster than diamond and quietly: blocks it breaks make no vibration, so sculk sensors,
 * shriekers and the Warden do not hear it.
 *
 * The breach thrust: hold use to charge (the spindle draws back, the collar spins up), let go to drive the head
 * in. Pickaxe mode bores a player-sized tunnel 2 to 5 blocks deep (a shaft when aimed straight up or down) through
 * stone and soil; shovel mode clears a 3x3 face of soft ground 1 to 3 deep; hoe mode a 3x3 of leaves, moss and
 * the like. It never takes containers, fluids, bedrock or anything harder than its head is rated for, and it
 * respects claims (every block goes through the normal break rules). Every block costs durability; the thrust
 * costs hunger and leaves the tool venting (the item cooldown) for longer the more it took.
 * Sneak + use on a block: shovel mode makes a path, hoe mode tills; otherwise sneak + use falls through to the other
 * hand (so a torch in the off hand still goes down while you dig).
 */
public class Mt7cItem extends DiggerItem {
    public static final int PICK = 0, SHOVEL = 1, HOE = 2;
    public static final String[] MODE_NAMES = {"PICKAXE", "SHOVEL", "HOE"};
    public static final int DURABILITY = 1700;
    /** Mining speed with the right head (diamond is 8). */
    public static final float SPEED = 8.5F;
    // ---- the mode change
    public static final int SWITCH_TICKS = 12;
    // ---- the breach thrust
    /** A shorter charge does nothing; the depth steps up every 10 ticks or so; FULL_CHARGE is the deepest. */
    public static final int MIN_CHARGE = 8, FULL_CHARGE = 40;
    public static final double REACH = 4.5D;
    /** Hardest block each head will breach (deepslate ores are 4.5, obsidian 50; dirt 0.5, sand 0.5, clay 0.6). */
    public static final float[] MAX_HARDNESS = {5.0F, 1.5F, 1.5F};
    /** The recovery after a breach: a base plus per block taken (ticks), capped. A charge that hit nothing still vents a little. */
    public static final int VENT_BASE = 24, VENT_PER_BLOCK = 6, VENT_MAX = 140, VENT_WHIFF = 16;
    public static final int THRUST_DURABILITY = 3;
    public static final float THRUST_EXHAUSTION = 0.4F, BLOCK_EXHAUSTION = 0.08F;

    public Mt7cItem(Properties properties) {
        super(2.0F, -3.0F, Tiers.DIAMOND, BlockTags.MINEABLE_WITH_PICKAXE, properties);
    }

    // ---------------------------------------------------------------- modes

    public static int mode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? PICK : Mth.clamp(tag.getInt("Mode"), PICK, HOE);
    }

    public static void setMode(ItemStack stack, int mode) {
        stack.getOrCreateTag().putInt("Mode", Math.floorMod(mode, 3));
    }

    public static TagKey<Block> mineable(int mode) {
        return switch (mode) {
            case SHOVEL -> BlockTags.MINEABLE_WITH_SHOVEL;
            case HOE -> BlockTags.MINEABLE_WITH_HOE;
            default -> BlockTags.MINEABLE_WITH_PICKAXE;
        };
    }

    /** How deep (pickaxe: tunnel length; shovel and hoe: layers of 3x3) a charge of this many ticks breaches. 0 = too short. */
    public static int depthFor(int mode, float charge) {
        if (charge < MIN_CHARGE) return 0;
        if (mode == PICK) return charge < 20 ? 2 : charge < 30 ? 3 : charge < FULL_CHARGE ? 4 : 5;
        return charge < 20 ? 1 : charge < 32 ? 2 : 3;
    }

    public static int maxDepth(int mode) {
        return mode == PICK ? 5 : 3;
    }

    // ---------------------------------------------------------------- digging

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return state.is(mineable(mode(stack))) ? SPEED : 1.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(mineable(mode(stack))) && TierSortingRegistry.isCorrectTierForDrops(getTier(), state);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return switch (mode(stack)) {
            case SHOVEL -> ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action);
            case HOE -> ToolActions.DEFAULT_HOE_ACTIONS.contains(action);
            default -> ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(action);
        };
    }

    /** Sneak + use on a block: a path in shovel mode, tilled soil in hoe mode (the vanilla tools' own logic). */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null || !p.isShiftKeyDown() || ctx.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        return switch (mode(ctx.getItemInHand())) {
            case SHOVEL -> Items.DIAMOND_SHOVEL.useOn(ctx);
            case HOE -> Items.DIAMOND_HOE.useOn(ctx);
            default -> InteractionResult.PASS;
        };
    }

    // ---------------------------------------------------------------- the breach thrust

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;                                                     // the brace is the MT-7C's own pose
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int charged = getUseDuration(stack) - remaining;
        if (!level.isClientSide && charged == FULL_CHARGE) {                     // a soft click: full power
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), com.geist.registry.ModSounds.MT7C_READY.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.3F);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p)) return;
        com.geist.event.Mt7cEvents.release(p, stack, getUseDuration(stack) - remaining);
    }

    // ---------------------------------------------------------------- the rest

    /** Every field unit carries a serial. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && (stack.getTag() == null || !stack.getTag().contains("Serial"))) {
            RandomSource r = level.random;
            String serial = "7C-" + (char) ('A' + r.nextInt(26)) + (char) ('A' + r.nextInt(26)) + "-" + (1000 + r.nextInt(9000));
            stack.getOrCreateTag().putString("Serial", serial);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();          // a mode change is animated by the tool itself
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        String serial = tag != null && tag.contains("Serial") ? tag.getString("Serial") : "UNASSIGNED";
        tooltip.add(Component.translatable("tooltip.geist.mt7c.issue", serial).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.geist.mt7c.mode", MODE_NAMES[mode(stack)]).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.geist.mt7c.switch").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.mt7c.breach").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.mt7c.quiet").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.geist.mt7c.cost").withStyle(ChatFormatting.DARK_RED));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    /** The animated 3D model, and the brace pose while charging. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.geist.client.mt7c.Mt7cRenderer.get();
            }
        });
    }
}
