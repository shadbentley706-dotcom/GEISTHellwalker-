package com.geist.item;

import com.geist.entity.SmokePelletEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Smoke Pellet: right-click to drop it at your feet and vanish into a 7x7 cloud of smoke. */
public class SmokePelletItem extends Item {
    public SmokePelletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.6F);
        if (!level.isClientSide) {
            SmokePelletEntity pellet = new SmokePelletEntity(level, player);
            pellet.setItem(stack);
            Vec3 look = player.getLookAngle();
            pellet.shoot(look.x * 0.3D, -1.0D, look.z * 0.3D, 0.5F, 0.0F);     // straight down at your feet
            level.addFreshEntity(pellet);
        }
        player.getCooldowns().addCooldown(this, 20);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.geist.smoke.what").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.geist.smoke.blind").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.geist.smoke.visor").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
