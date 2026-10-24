package com.geist.client.exo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The 3D exo-frame item: the suit standing on a white line-art mannequin, pistons idling, lights on. */
public class ExoItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static ExoItemRenderer instance;
    private ExoModel model;

    private ExoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static ExoItemRenderer get() {
        if (instance == null) instance = new ExoItemRenderer();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (model == null) model = new ExoModel(mc.getEntityModels().bakeLayer(ExoModel.LAYER));
        float pt = mc.getFrameTime();
        model.body.resetPose();
        model.rightLeg.resetPose();
        model.leftLeg.resetPose();
        ExoAnimator.animate(model, null, stack, pt);
        pose.pushPose();
        pose.translate(0.5D, 0.5D + 12.0D / 16.0D, 0.5D);          // the hips at the centre
        pose.scale(1.0F, -1.0F, -1.0F);
        model.showGhost(false);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ExoLayer.TEX)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        float[] g = ExoAnimator.glow(null, stack, pt);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(ExoLayer.GLOW)), 15728880, overlay, g[0], g[1], g[2], 1.0F);
        if (ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.FIXED) {
            model.showGhost(true);                                  // the line-art legs, like a schematic
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(ExoLayer.TEX)), light, overlay, 1.0F, 1.0F, 1.0F, 0.9F);
        }
        model.hideGhost();
        pose.popPose();
    }
}
