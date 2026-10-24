package com.geist.client.gauntlet;

import com.geist.GeistMod;
import com.geist.item.GauntletItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the gauntlet on any player wearing it, following their exact pose: black plating and silver panels,
 * three glow textures (the fixed lights, and the two alternating halves of the spine) tinted at run time,
 * and while a barrage is on, fading ghost copies of each fist for motion blur.
 */
public class GauntletLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEX = tex("gauntlet");
    public static final ResourceLocation GLOW = tex("gauntlet_glow");
    public static final ResourceLocation GLOW_A = tex("gauntlet_glow_a");
    public static final ResourceLocation GLOW_B = tex("gauntlet_glow_b");
    private static final float[] TRAIL_AGE = {0.55F, 1.1F, 1.7F};
    private static final float[] TRAIL_ALPHA = {0.34F, 0.2F, 0.1F};
    private static final GauntletAnimator.Out TMP = new GauntletAnimator.Out();

    private final GauntletModel model;

    public GauntletLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new GauntletModel(models.bakeLayer(GauntletModel.LAYER));
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(GeistMod.MODID, "textures/models/gauntlet/" + name + ".png");
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack g = GauntletItem.worn(player);
        if (g.isEmpty() || player.isInvisible()) return;
        GauntletClient.Anim a = GauntletClient.peek(player.getId());
        float t = player.level().getGameTime() + partialTick;
        this.getParentModel().copyPropertiesTo(model);
        model.hideGhost();
        model.setSlim("slim".equals(player.getModelName()));
        GauntletAnimator.animate(model, a, t);

        GauntletAnimator.eval(a, 0, t, TMP);
        float heat = TMP.heat;
        GauntletAnimator.eval(a, 1, t, TMP);
        heat = Math.max(heat, TMP.heat);
        float g1 = 1.0F - 0.25F * heat, b1 = 1.0F - 0.5F * heat;
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY, 1.0F, g1, b1, 1.0F);
        float[] c = GauntletAnimator.glow(a, t);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 15728880, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1.0F);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW_A)), 15728880, OverlayTexture.NO_OVERLAY, c[0] * c[3], c[1] * c[3], c[2] * c[3], 1.0F);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW_B)), 15728880, OverlayTexture.NO_OVERLAY, c[0] * c[4], c[1] * c[4], c[2] * c[4], 1.0F);

        if (a != null && a.state == GauntletClient.BARRAGE) trails(pose, buffers, light, a, t);
    }

    /** Motion blur: each fist leaves fading copies of itself where it was a moment ago. */
    private void trails(PoseStack pose, MultiBufferSource buffers, int light, GauntletClient.Anim a, float t) {
        for (int side = 0; side < 2; side++) {
            ModelPart arm = side == 0 ? model.rightArm : model.leftArm;
            ModelPart gaunt = side == 0 ? model.gRight : model.gLeft;
            GauntletAnimator.eval(a, side, t, TMP);
            float nowExt = TMP.ext;
            float sx = arm.x, sy = arm.y, sz = arm.z, rx = arm.xRot, ry = arm.yRot, rz = arm.zRot;
            for (int i = 0; i < TRAIL_AGE.length; i++) {
                GauntletAnimator.eval(a, side, t - TRAIL_AGE[i], TMP);
                float moved = Math.abs(nowExt - TMP.ext);
                if (moved < 0.12F || TMP.w < 0.99F) continue;
                arm.xRot = TMP.x;
                arm.yRot = TMP.y;
                arm.zRot = TMP.z;
                arm.z = sz + TMP.push;
                float alpha = TRAIL_ALPHA[i] * Mth.clamp(moved * 1.6F, 0.0F, 1.0F);
                pose.pushPose();
                arm.translateAndRotate(pose);
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEX));
                gaunt.render(pose, vc, light, OverlayTexture.NO_OVERLAY, 0.55F, 0.9F, 1.0F, alpha);
                pose.popPose();
            }
            arm.x = sx;
            arm.y = sy;
            arm.z = sz;
            arm.xRot = rx;
            arm.yRot = ry;
            arm.zRot = rz;
        }
    }
}
