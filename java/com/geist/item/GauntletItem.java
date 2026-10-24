package com.geist.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The G.E.I.S.T. Gauntlet MK-I: hydraulic forearm bracers on a back-plate rig, worn in the chest slot.
 *
 * Worn: +4 armor, +3 toughness, 10% slower. With an empty main hand a punch hits for 10 HP at 1.4 swings a
 * second (a sword swings 1.6 times a second). Hold attack (or the barrage key) for the signature move:
 * 0.5 s wind-up, then an alternating rapid-fire barrage (3 HP per hit, about 4.4 hits a second, ignores hit
 * cooldown, drains hunger fast) for at most 4 seconds, then the gauntlet vents steam and locks out for 3 to 7
 * seconds. Sneak + use on a mob picks it up overhead; hold use to wind up and release to throw it like a football,
 * or attack to slam it into the ground. The rules live in GauntletEvents and GauntletCarry.
 */
public class GauntletItem extends Item implements Equipable {
    // ---- the signature barrage
    public static final int WINDUP_TICKS = 10, MAX_BARRAGE_TICKS = 80;
    /** Ticks between barrage punches, alternating (4 and 5: about 4.4 punches a second). */
    public static final int[] PUNCH_GAPS = {4, 5};
    public static final int COOLDOWN_BASE = 60, ABORT_COOLDOWN = 15;
    public static final float BARRAGE_DAMAGE = 3.0F, REACH = 3.4F, BARRAGE_KNOCKBACK = 0.06F;
    public static final float WINDUP_EXHAUSTION = 1.0F, BARRAGE_EXHAUSTION = 0.9F;
    public static final int START_FOOD = 8, STOP_FOOD = 2;
    // ---- the normal punch
    public static final float PUNCH_DAMAGE = 10.0F;
    /** Swings per second (a sword is 1.6). */
    public static final double PUNCH_SPEED = 1.4D;
    public static final float HEAVY_KNOCKBACK = 0.7F;
    // ---- grab, carry and throw (GauntletCarry)
    public static final int THROW_COOLDOWN = 100, DROP_COOLDOWN = 40, THROW_MIN_FOOD = 5;
    /** Longest a mob can be held overhead before it wriggles free (10 s), and the wind-up for a full-power throw (1 s). */
    public static final int MAX_CARRY_TICKS = 200, FULL_CHARGE_TICKS = 20;
    public static final float THROW_EXHAUSTION = 3.0F, CARRY_EXHAUSTION = 0.02F, THROW_IMPACT_DAMAGE = 6.0F;
    /** Throw speed in blocks per tick at no wind-up and at full wind-up (about 8 and 30 blocks for a zombie). */
    public static final double THROW_SPEED_MIN = 1.2D, THROW_SPEED_MAX = 3.2D;
    /** The ball is lofted this many degrees above where you look; the launch never goes steeper than MAX_LAUNCH_UP. */
    public static final float THROW_LOFT = 12.0F, MAX_LAUNCH_UP = 32.0F;
    /** Biggest mob you can lift: width x width x height (an iron golem is 5.3, a ravager 8.4). */
    public static final double MAX_CARRY_SIZE = 6.0D;
    public static final float SLAM_DAMAGE = 7.0F, TACKLE_DAMAGE = 5.0F;
    // ---- the guard (GauntletGuard): hold use with both hands empty
    /** Heat the hydraulics soak up before the guard breaks; how fast it bleeds off with the guard up and down (per tick). */
    public static final float GUARD_HEAT_MAX = 100.0F, GUARD_COOL_UP = 1.0F, GUARD_COOL_DOWN = 3.0F;
    /** Heat per blocked hit: melee costs a base plus some per point of damage; arrows and the Warden's boom a flat amount. */
    public static final float GUARD_MELEE_BASE = 6.0F, GUARD_MELEE_PER_DAMAGE = 5.0F, GUARD_PROJECTILE = 10.0F,
            GUARD_SONIC = 55.0F, GUARD_SONIC_PARRY = 20.0F;
    /** Explosions (creepers, TNT, fireballs) cost a base plus some per point of damage; a parried blast a flat amount. */
    public static final float GUARD_BLAST_BASE = 10.0F, GUARD_BLAST_PER_DAMAGE = 2.5F, GUARD_BLAST_PARRY = 25.0F;
    /** A block this soon after raising the guard is a parry (no heat, the attacker reels). */
    public static final int PARRY_TICKS = 5, GUARD_BREAK_TICKS = 60, GUARD_HOLD_TICKS = 3;
    public static final double GUARD_SLOW = 0.45D;
    // ---- worn
    public static final double ARMOR = 4.0D, TOUGHNESS = 3.0D, WEIGHT = -0.10D;
    private static final UUID ARMOR_ID = UUID.fromString("5b0d6e1a-7c34-4d0e-9a52-1c8f3e6b7a01");
    private static final UUID TOUGHNESS_ID = UUID.fromString("5b0d6e1a-7c34-4d0e-9a52-1c8f3e6b7a02");
    private static final UUID WEIGHT_ID = UUID.fromString("5b0d6e1a-7c34-4d0e-9a52-1c8f3e6b7a03");

    private final Multimap<Attribute, AttributeModifier> worn;

    public GauntletItem(Properties properties) {
        super(properties);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(Attributes.ARMOR, new AttributeModifier(ARMOR_ID, "Gauntlet armor", ARMOR, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_ID, "Gauntlet toughness", TOUGHNESS, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(WEIGHT_ID, "Gauntlet weight", WEIGHT, AttributeModifier.Operation.MULTIPLY_BASE));
        this.worn = b.build();
    }

    /** The gauntlet a creature is wearing, or EMPTY. */
    public static ItemStack worn(LivingEntity e) {
        ItemStack s = e.getItemBySlot(EquipmentSlot.CHEST);
        return s.getItem() instanceof GauntletItem ? s : ItemStack.EMPTY;
    }

    /** How long the lock-out lasts after a barrage that ran this many ticks: 3 s up to 7 s. */
    public static int cooldownFor(int ranTicks) {
        return COOLDOWN_BASE + Math.max(0, Math.min(ranTicks, MAX_BARRAGE_TICKS));
    }

    /** Where a bracer is in the world (side 0 = the player's right arm, 1 = the left): used for steam. */
    public static Vec3 bracerPos(LivingEntity e, int side) {
        double yaw = Math.toRadians(e.yBodyRot);
        double sx = side == 0 ? 1.0D : -1.0D;
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw), fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw);
        return new Vec3(e.getX() + sx * 0.42D * rightX + 0.28D * fwdX, e.getY() + e.getBbHeight() * 0.58D,
                e.getZ() + sx * 0.42D * rightZ + 0.28D * fwdZ);
    }

    // ---------------------------------------------------------------- equipping

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.CHEST;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_NETHERITE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.CHEST ? worn : super.getDefaultAttributeModifiers(slot);
    }

    /** The animated 3D model in the inventory, in hand and on the ground. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.geist.client.gauntlet.GauntletItemRenderer.get();
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.geist.gauntlet.prototype").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.geist.gauntlet.punch").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.gauntlet.barrage").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.geist.gauntlet.cost").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.gauntlet.throw").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.gauntlet.guard").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.gauntlet.drawbacks").withStyle(ChatFormatting.DARK_RED));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
