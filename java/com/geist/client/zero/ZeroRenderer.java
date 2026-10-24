package com.geist.client.zero;

import com.geist.GeistMod;
import com.geist.client.mt7c.Mt7cClient;
import com.geist.item.ZeroLightItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The 3D Zero Light. While it is drawn the string halves pull back to the nock, the limbs flex, the cams turn and a
 * light arrow sits on the rest; after a shot the string snaps and shivers. The cores glow in the mode's colour
 * (cyan standard, white-blue shockwave - dim when the pulse is not ready - violet ricochet), brighter as you draw.
 *
 * Placement is done here rather than in the item JSON: in the hand the bow stands upright with the string toward
 * the archer and the arrow pointing where they aim (first person, and third person while drawing); carried, it
 * hangs along the arm; in the inventory it shows its side profile like the reference picture.
 */
public class ZeroRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation TEX = new ResourceLocation(GeistMod.MODID, "textures/models/zero_light/zero_light.png");
    public static final ResourceLocation GLOW = new ResourceLocation(GeistMod.MODID, "textures/models/zero_light/zero_light_glow.png");
    /** How far the string comes back at full draw (model pixels), and where the grip is. */
    private static final float PULL = 6.2F, GRIP_Z = 2.2F;
    private static ZeroRenderer instance;
    private ZeroModel model;

    private ZeroRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static ZeroRenderer get() {
        if (instance == null) instance = new ZeroRenderer();
        return instance;
    }

    private static float ease(float v) {
        v = Mth.clamp(v, 0.0F, 1.0F);
        return v * v * (3.0F - 2.0F * v);
    }

    /**
     * First person, in place of vanilla's bow pose (which is made for a flat bow sprite): the usual hand position and
     * swing, and while drawing the bow comes up toward the middle of the view and steadies, with a faint tremble at
     * full draw. The renderer then stands the bow upright with the arrow pointing at the crosshair.
     */
    public static boolean handTransform(PoseStack ps, net.minecraft.client.player.LocalPlayer player, net.minecraft.world.entity.HumanoidArm arm,
                                        ItemStack stack, float pt, float equip, float swing) {
        float side = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1.0F : -1.0F;
        boolean drawing = player.isUsingItem() && player.getUseItem() == stack;
        if (!drawing && swing > 0.0F) {                                          // the vanilla swing
            float sq = Mth.sqrt(swing);
            ps.translate(side * -0.4F * Mth.sin(sq * (float) Math.PI), 0.2F * Mth.sin(sq * (float) Math.PI * 2.0F), -0.2F * Mth.sin(swing * (float) Math.PI));
        }
        ps.translate(side * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        if (drawing) {
            float ticks = player.getTicksUsingItem() + pt;
            float up = ease(ticks / 5.0F), k = ZeroLightItem.power(ticks);
            ps.translate(side * -0.26F * up, 0.14F * up, 0.1F * up);
            if (k >= 1.0F) {                                                     // holding at full draw: a faint tremble
                float tr = Math.min(1.0F, (ticks - ZeroLightItem.DRAW_TICKS) / 30.0F) * 0.004F;
                ps.translate(tr * Mth.sin(ticks * 1.7F), tr * Mth.sin(ticks * 2.3F + 1.0F), 0.0F);
            }
        } else if (swing > 0.0F) {
            float f = Mth.sin(swing * swing * (float) Math.PI), f1 = Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
            ps.mulPose(Axis.YP.rotationDegrees(side * (45.0F + f * -20.0F)));
            ps.mulPose(Axis.ZP.rotationDegrees(side * f1 * -20.0F));
            ps.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
            ps.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
        }
        return true;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (model == null) model = new ZeroModel(mc.getEntityModels().bakeLayer(ZeroModel.LAYER));
        float pt = mc.getFrameTime();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        float t = now + pt;
        boolean fp = ctx.firstPerson();
        boolean thirdHand = ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        LivingEntity holder = fp ? mc.player : thirdHand ? Mt7cClient.drawing() : null;
        if (holder != null && !fp && holder.getMainHandItem() != stack && holder.getOffhandItem() != stack) holder = null;
        boolean drawing = ZeroClient.drawing(holder) && (fp || holder.getUseItem() == stack);
        float k = drawing ? ZeroLightItem.power(holder.getTicksUsingItem() + pt) : 0.0F;
        ZeroClient.Anim a = holder == null ? null : ZeroClient.peek(holder.getId());

        // ---- the moving parts
        model.reset();
        float pull = PULL * ease(k);
        if (drawing) {
            model.arrow.visible = true;
            model.arrow.z -= pull;
        }
        model.stringU.xRot = -(float) Math.atan2(pull, ZeroModel.STRING_U);
        model.stringU.yScale = (float) Math.sqrt(ZeroModel.STRING_U * ZeroModel.STRING_U + pull * pull) / ZeroModel.STRING_U;
        model.stringL.xRot = (float) Math.atan2(pull, ZeroModel.STRING_L);
        model.stringL.yScale = (float) Math.sqrt(ZeroModel.STRING_L * ZeroModel.STRING_L + pull * pull) / ZeroModel.STRING_L;
        model.limbU.xRot = 0.1F * k;
        model.limbL.xRot = -0.1F * k;
        model.camU.xRot = 2.4F * k;
        model.camL.xRot = -2.4F * k;
        if (a != null) {                                                         // the string snaps forward and shivers
            float s = t - a.fireAt;
            if (s >= 0.0F && s < 10.0F) {
                float v = 0.09F * Mth.sin(s * 3.2F) * (float) Math.exp(-s / 2.5F);
                float back = 0.07F * (float) Math.exp(-s / 2.0F);
                model.stringU.xRot += v;
                model.stringL.xRot -= v;
                model.limbU.xRot -= back;
                model.limbL.xRot += back;
            }
        }

        // ---- the glow: the mode's colour, brighter as you draw
        int mode = ZeroLightItem.mode(stack);
        float bright = 0.65F + 0.35F * k;
        float r, g, b;
        if (mode == ZeroLightItem.SHOCKWAVE) {
            boolean armed = ZeroLightItem.shockArmed(stack, now);
            float pulse = armed ? 0.8F + 0.2F * Mth.sin(t * (k >= 1.0F ? 1.4F : 0.35F)) : 0.35F;
            r = 0.75F * pulse;
            g = 0.95F * pulse;
            b = 1.0F * pulse;
        } else if (mode == ZeroLightItem.RICOCHET) {
            r = 0.78F;
            g = 0.45F;
            b = 1.0F;
        } else {
            r = 0.3F;
            g = 0.88F;
            b = 1.0F;
        }

        // ---- placement
        ps.pushPose();
        ps.translate(0.5D, 0.5D, 0.5D);
        float scale;
        if (fp) {
            float side = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? 1.0F : -1.0F;
            ps.translate(-0.1D * side, 0.12D, 0.0D);
            ps.mulPose(Axis.ZP.rotationDegrees(12.0F * side));
            ps.mulPose(Axis.YP.rotationDegrees(-10.0F * side));
            scale = 0.35F;
        } else if (thirdHand) {
            if (!drawing) ps.mulPose(Axis.XP.rotationDegrees(90.0F));             // carried: hanging along the arm
            scale = 0.5F;
        } else {
            ps.mulPose(Axis.YP.rotationDegrees(-70.0F));                         // the side profile
            scale = 0.42F;
        }
        ps.scale(scale, scale, scale);
        ps.scale(1.0F, -1.0F, -1.0F);
        if (fp || thirdHand) ps.translate(0.0D, 0.0D, -GRIP_Z / 16.0F);           // the grip in the hand
        else ps.translate(0.0D, 0.9D / 16.0D, -1.7D / 16.0D);                    // centred in the slot
        model.renderToBuffer(ps, ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEX), false, stack.hasFoil()),
                light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        model.renderToBuffer(ps, buffers.getBuffer(RenderType.eyes(GLOW)), 15728880, overlay, r * bright, g * bright, b * bright, 1.0F);
        ps.popPose();
    }
}
