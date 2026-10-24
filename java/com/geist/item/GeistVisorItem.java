package com.geist.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The G.E.I.S.T. Visor: tactical glasses worn in the helmet slot. Wearing them runs the Entity Scanner. */
public class GeistVisorItem extends Item implements Equipable {
    public GeistVisorItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_IRON;
    }

    /** Right-click to put them on. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.geist.visor.acronym").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.geist.visor.scanner").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  \u25A0 ").withStyle(ChatFormatting.GREEN).append(Component.translatable("tooltip.geist.visor.friendly").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("  \u25A0 ").withStyle(ChatFormatting.RED).append(Component.translatable("tooltip.geist.visor.hostile").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("  \u25A0 ").withStyle(ChatFormatting.BLUE).append(Component.translatable("tooltip.geist.visor.player").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable("tooltip.geist.visor.hud").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.geist.visor.wear").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
