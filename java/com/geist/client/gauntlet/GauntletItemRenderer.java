package com.geist.client.gauntlet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The 3D gauntlet item: the armour on a white line-art mannequin that loops a little show - idle, wind-up,
 * a burst of punches, then the vent-and-cool-down - so the inventory icon is alive.
 */
public class GauntletItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static GauntletItemRenderer instance;
    private static final int LOOP = 110;
    private static final float PUNCH_EVERY = 4.5F;
    private GauntletModel model;
    private final GauntletClient.Anim demo = new GauntletClient.Anim();
    private final GauntletAnimator.Out out = new GauntletAnimator.Out();

    private GauntletItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static GauntletItemRenderer get() {
        if (instance == null) instance = new GauntletItemRenderer();
        return instance;
    }

    /** Sets the looping demo to what it should be doing at {@code t}; returns the time within the demo. */
    private float stage(float t) {
        GauntletClient.Anim d = demo;
        float base = (float) Math.floor(t / LOOP) * LOOP;
        float c = t - base;
        d.punchAt[0] = d.punchAt[1] = -1000L;
        d.lastHeavy = d.throwAt = d.slamAt = d.bootStart = -1000L;
        d.carryId = -1;
        d.cooldownLen = 40;
        if (c < 24.0F) {
            d.state = GauntletClient.IDLE;
        } else if (c < 34.0F) {
            d.state = GauntletClient.WINDUP;
            d.start = (long) (base + 24.0F);
        } else if (c < 68.0F) {
            d.state = GauntletClient.BARRAGE;
            d.start = (long) (base + 34.0F);
            int n = (int) ((c - 34.0F) / PUNCH_EVERY);
            d.punchAt[n % 2] = (long) Math.floor(base + 34.0F + n * PUNCH_EVERY);
            if (n >= 1) d.punchAt[(n + 1) % 2] = (long) Math.floor(base + 34.0F + (n - 1) * PUNCH_EVERY);
        } else {
            d.state = GauntletClient.COOLDOWN;
            d.start = (long) (base + 68.0F);
        }
        return t;
    }

    /**
     * The inventory icon: one forearm bracer, big and on the diagonal, seen from the outside so the lens, the
     * glowing knuckles and the pistons read at 16 pixels (the whole rig is mush at that size). Pistons idle.
     */
    private void icon(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float t) {
        model.setSlim(false);
        model.rightArm.resetPose();
        GauntletAnimator.animate(model, null, t);
        model.bracerOnly();
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(Axis.ZP.rotationDegrees(35.0F));                 // on the diagonal, knuckles bottom right
        pose.mulPose(Axis.XP.rotationDegrees(8.0F));                  // a touch from above
        pose.mulPose(Axis.YP.rotationDegrees(45.0F));                 // the outer plate and the front both show
        pose.scale(1.3F, 1.3F, 1.3F);
        pose.scale(1.0F, -1.0F, -1.0F);
        pose.translate(6.33D / 16.0D, -9.5D / 16.0D, 0.0D);         // the middle of the bracer at the centre
        model.rightArm.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GauntletLayer.TEX)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        float[] c = GauntletAnimator.glow(null, t);
        model.rightArm.render(pose, buffers.getBuffer(RenderType.eyes(GauntletLayer.GLOW)), 15728880, overlay, c[0], c[1], c[2], 1.0F);
        pose.popPose();
        model.hideGhost();
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (model == null) model = new GauntletModel(mc.getEntityModels().bakeLayer(GauntletModel.LAYER));
        float t = (mc.level != null ? mc.level.getGameTime() : (System.nanoTime() / 50_000_000L)) + mc.getFrameTime();
        if (ctx == ItemDisplayContext.GUI) {
            icon(pose, buffers, light, overlay, t);
            return;
        }
        model.body.resetPose();
        model.rightArm.resetPose();
        model.leftArm.resetPose();
        model.setSlim(false);
        stage(t);
        GauntletClient.Anim a = demo.state == GauntletClient.IDLE ? null : demo;
        for (int side = 0; side < 2; side++) {
            ModelPart arm = side == 0 ? model.rightArm : model.leftArm;
            GauntletAnimator.eval(a, side, t, out);
            arm.xRot = out.x * out.w;
            arm.yRot = out.y * out.w;
            arm.zRot = out.z * out.w;
            arm.z += out.push * out.w;
        }
        GauntletAnimator.animate(model, a, t);
        pose.pushPose();
        pose.translate(0.5D, 0.5D + 4.0D / 16.0D, 0.5D);          // the chest at the centre
        pose.scale(1.0F, -1.0F, -1.0F);
        model.hideGhost();
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GauntletLayer.TEX)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        float[] c = GauntletAnimator.glow(a, t);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GauntletLayer.GLOW)), 15728880, overlay, c[0], c[1], c[2], 1.0F);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GauntletLayer.GLOW_A)), 15728880, overlay, c[0] * c[3], c[1] * c[3], c[2] * c[3], 1.0F);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GauntletLayer.GLOW_B)), 15728880, overlay, c[0] * c[4], c[1] * c[4], c[2] * c[4], 1.0F);
        if (ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.FIXED) {
            model.showGhost(true);                                  // the line-art body and arms, like a schematic
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(GauntletLayer.TEX)), light, overlay, 1.0F, 1.0F, 1.0F, 0.9F);
        }
        model.hideGhost();
        pose.popPose();
    }
}
