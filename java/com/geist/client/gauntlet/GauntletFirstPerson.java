package com.geist.client.gauntlet;

import com.geist.GeistMod;
import com.geist.item.GauntletItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The gauntlet in first person. Vanilla draws one bare arm; this puts the armoured bracer on it, and while a
 * wind-up, barrage, cool-down or throw is on it also draws the second arm and moves both: hands up in a guard,
 * fists snapping out one after the other, sagging and shaking while the steam vents. Fading copies of each fist
 * give the motion blur.
 *
 * It rides on vanilla's own arm placement (the event fires once the pose stack is already at the arm), so it
 * is right at every field of view; the second arm is the same placement mirrored.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class GauntletFirstPerson {
    private static final Matrix4f MX4 = new Matrix4f().scaling(-1.0F, 1.0F, 1.0F);
    private static final Matrix3f MX3 = new Matrix3f().scaling(-1.0F, 1.0F, 1.0F);
    private static final float[] TRAIL_AGE = {0.5F, 1.0F, 1.6F};
    private static final float[] TRAIL_ALPHA = {0.36F, 0.22F, 0.1F};
    private static final GauntletAnimator.Out OA = new GauntletAnimator.Out(), OB = new GauntletAnimator.Out(), OT = new GauntletAnimator.Out();
    private static GauntletModel model;
    private static boolean failed, drawing;

    private GauntletFirstPerson() {}

    /** While the gauntlet is busy both fists are drawn by {@link #onArm}, so an item in the other hand stays out of the way. */
    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.OFF_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || GauntletItem.worn(p).isEmpty()) return;
        float t = mc.level.getGameTime() + event.getPartialTick();
        if (GauntletAnimator.busy(GauntletClient.peek(p.getId()), t)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onArm(RenderArmEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer p = event.getPlayer();
        if (p != mc.player || mc.level == null || GauntletItem.worn(p).isEmpty()) return;
        // drawing the second arm goes through PlayerRenderer.renderLeftHand / renderRightHand, which fires this very
        // event again: ignore that inner one, or the two arms call each other forever (v12.0.0 crash on G)
        if (failed || drawing) return;
        drawing = true;
        try {
            draw(event, mc, p);
        } catch (RuntimeException ex) {
            failed = true;                                          // never take the game down over a hand: vanilla still draws the arm
            LogUtils.getLogger().warn("G.E.I.S.T.: first-person gauntlet disabled after an error", ex);
        } finally {
            drawing = false;
        }
    }

    private static void draw(RenderArmEvent event, Minecraft mc, AbstractClientPlayer p) {
        if (model == null) model = new GauntletModel(mc.getEntityModels().bakeLayer(GauntletModel.LAYER));
        PoseStack ps = event.getPoseStack();
        MultiBufferSource buf = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float t = mc.level.getGameTime() + mc.getFrameTime();
        GauntletClient.Anim a = GauntletClient.peek(p.getId());
        boolean busy = GauntletAnimator.busy(a, t);
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        float f = right ? 1.0F : -1.0F;
        boolean slim = "slim".equals(p.getModelName());
        model.setSlim(slim);
        model.hideGhost();
        model.sRight.visible = model.sLeft.visible = false;               // the pauldrons would sit in front of the camera
        GauntletAnimator.animate(model, a, t);

        Matrix4f base = new Matrix4f(ps.last().pose());
        Matrix3f baseN = new Matrix3f(ps.last().normal());
        GauntletAnimator.eval(a, right ? 0 : 1, t, OA);
        place(ps, base, baseN, OA, f);
        bracer(ps, buf, light, right, a, t);
        if (!busy) return;
        trails(ps, buf, light, right, a, t, base, baseN, f);

        // the other arm: vanilla placement mirrored left-right
        Matrix4f baseB = new Matrix4f(base).mulLocal(MX4).mul(MX4);
        Matrix3f baseBN = new Matrix3f(baseN).mulLocal(MX3).mul(MX3);
        ps.pushPose();
        GauntletAnimator.eval(a, right ? 1 : 0, t, OB);
        place(ps, baseB, baseBN, OB, -f);
        PlayerRenderer pr = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(p);
        if (right) pr.renderLeftHand(ps, buf, light, p);
        else pr.renderRightHand(ps, buf, light, p);
        bracer(ps, buf, light, !right, a, t);
        ps.popPose();
        trails(ps, buf, light, !right, a, t, baseB, baseBN, -f);
    }

    /** The arm's placement for this frame: vanilla's, moved and turned in camera space by the animation. */
    private static void place(PoseStack ps, Matrix4f base, Matrix3f baseN, GauntletAnimator.Out o, float f) {
        Matrix4f m = offset(o, f);
        PoseStack.Pose last = ps.last();
        last.pose().set(base).mulLocal(m);
        last.normal().set(baseN).mulLocal(new Matrix3f(m));
    }

    private static Matrix4f offset(GauntletAnimator.Out o, float f) {
        float cx = f * 0.5F, cy = -0.45F, cz = -0.8F;               // roughly where the fist is: turn about that
        return new Matrix4f()
                .translate(o.fpx, o.fpy, o.fpz)
                .translate(cx, cy, cz)
                .rotateZ(o.fpRoll * Mth.DEG_TO_RAD)
                .rotateX(o.fpPitch * Mth.DEG_TO_RAD)
                .translate(-cx, -cy, -cz);
    }

    /** The armour on one arm (the pose stack is already at that arm). */
    private static void bracer(PoseStack ps, MultiBufferSource buf, int light, boolean right, GauntletClient.Anim a, float t) {
        ModelPart arm = right ? model.rightArm : model.leftArm;
        arm.resetPose();
        arm.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(GauntletLayer.TEX)), light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        float[] c = GauntletAnimator.glow(a, t);
        arm.render(ps, buf.getBuffer(RenderType.eyes(GauntletLayer.GLOW)), 15728880, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1.0F);
        arm.render(ps, buf.getBuffer(RenderType.eyes(GauntletLayer.GLOW_A)), 15728880, OverlayTexture.NO_OVERLAY, c[0] * c[3], c[1] * c[3], c[2] * c[3], 1.0F);
        arm.render(ps, buf.getBuffer(RenderType.eyes(GauntletLayer.GLOW_B)), 15728880, OverlayTexture.NO_OVERLAY, c[0] * c[4], c[1] * c[4], c[2] * c[4], 1.0F);
    }

    /** Motion blur: fading copies of the bracer where it was a moment ago. */
    private static void trails(PoseStack ps, MultiBufferSource buf, int light, boolean right, GauntletClient.Anim a, float t,
                               Matrix4f base, Matrix3f baseN, float f) {
        if (a == null || a.state != GauntletClient.BARRAGE) return;
        int side = right ? 0 : 1;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        GauntletAnimator.eval(a, side, t, OT);
        float nowExt = OT.ext;
        for (int i = 0; i < TRAIL_AGE.length; i++) {
            GauntletAnimator.eval(a, side, t - TRAIL_AGE[i], OT);
            float moved = Math.abs(nowExt - OT.ext);
            if (moved < 0.12F) continue;
            ps.pushPose();
            place(ps, base, baseN, OT, f);
            arm.resetPose();
            arm.render(ps, buf.getBuffer(RenderType.entityTranslucent(GauntletLayer.TEX)), light, OverlayTexture.NO_OVERLAY,
                    0.55F, 0.9F, 1.0F, TRAIL_ALPHA[i] * Mth.clamp(moved * 1.6F, 0.0F, 1.0F));
            ps.popPose();
        }
    }
}
