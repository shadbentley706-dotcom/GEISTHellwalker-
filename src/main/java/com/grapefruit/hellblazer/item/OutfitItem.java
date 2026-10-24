package com.grapefruit.hellblazer.item;

import com.grapefruit.hellblazer.HellblazerMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The specialist's clothes: the TRENCH COAT (a tan gabardine trench worn open, the white shirt unbuttoned at the
 * throat and the red tie pulled loose - all one piece) and the BLACK TROUSERS. Cut close, like a tailored suit, on
 * their own armour models; a little tougher than leather. Leather mends them.
 */
public class OutfitItem extends ArmorItem {
    public static final ArmorMaterial CLOTH = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return type == Type.CHESTPLATE ? 260 : 240;
        }

        @Override
        public int getDefenseForType(Type type) {
            return type == Type.CHESTPLATE ? 4 : 3;
        }

        @Override
        public int getEnchantmentValue() {
            return 15;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_LEATHER;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.LEATHER);
        }

        @Override
        public String getName() {
            return HellblazerMod.MODID + ":cloth";
        }

        @Override
        public float getToughness() {
            return 0.5F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    };

    public OutfitItem(Type type, Properties props) {
        super(CLOTH, type, props);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String layer) {
        return HellblazerMod.MODID + ":textures/models/armor/" + (getType() == Type.CHESTPLATE ? "coat" : "trousers") + ".png";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String key = getType() == Type.CHESTPLATE ? "trench_coat" : "black_trousers";
        tooltip.add(Component.translatable("tooltip.geist_hellblazer." + key).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.grapefruit.hellblazer.client.outfit.OutfitClient.EXT);
    }
}
