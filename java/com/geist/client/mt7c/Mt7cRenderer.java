package com.geist.client.mt7c;

import com.geist.GeistMod;
import com.geist.item.Mt7cItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The 3D MT-7C, animated: whoever is holding it (first person: you; third person: the player being drawn) drives
 * the mode change, the charge, the thrust and the vent. Laid out like a tool sprite (handle bottom left, head top
 * right) so the usual hand-held positions fit; in the inventory it is tipped a little so the hood and LED show.
 */
public class Mt7cRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation TEX = new ResourceLocation(GeistMod.MODID, "textures/models/mt7c/mt7c.png");
    public static final ResourceLocation GLOW = new ResourceLocation(GeistMod.MODID, "textures/models/mt7c/mt7c_glow.png");
    /** Model pixels to item space, and the middle of the tool along its length. */
    private static final float SCALE = 0.57F, CENTER = 17.6F;
    private static Mt7cRenderer instance;
    private Mt7cModel model;
    private final Mt7cAnim.Pose pose = new Mt7cAnim.Pose();

    private Mt7cRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static Mt7cRenderer get() {
        if (instance == null) instance = new Mt7cRenderer();
        return instance;
    }

    private static boolean thirdPersonHand(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (model == null) model = new Mt7cModel(mc.getEntityModels().bakeLayer(Mt7cModel.LAYER));
        float pt = mc.getFrameTime();
        float t = (mc.level != null ? mc.level.getGameTime() : (System.nanoTime() / 50_000_000L)) + pt;
        boolean fp = ctx.firstPerson();
        LivingEntity holder = fp ? mc.player : thirdPersonHand(ctx) ? Mt7cClient.drawing() : null;
        // first person: always yours; third person: only if the player being drawn really holds this one
        if (holder != null && !fp && holder.getMainHandItem() != stack && holder.getOffhandItem() != stack) holder = null;
        Mt7cClient.Anim a = holder == null ? null : Mt7cClient.peek(holder.getId());
        int mode = Mt7cItem.mode(stack);
        boolean inMainHand = holder != null && (fp ? holder.getMainHandItem().getItem() instanceof Mt7cItem : holder.getMainHandItem() == stack);
        Mt7cAnim.Pose p = Mt7cAnim.pose(pose, a, mode, inMainHand ? Mt7cAnim.charge(holder, pt) : -1.0F, t);

        // ---- the parts
        model.reset(mode);
        model.mount.x += p.mount;
        model.collar.xRot = p.collar;
        model.ventL.xRot = 1.1F * p.flaps;
        model.ventR.xRot = -1.1F * p.flaps;
        if (p.switchK >= 0.0F && p.fromMode != p.toMode) {
            float k = p.switchK;
            ModelPart from = model.head(p.fromMode), to = model.head(p.toMode);
            for (int i = 0; i < 3; i++) model.head(i).visible = false;
            if (k < 0.45F) {                                                     // the old head rolls away into the collar
                float e = Mt7cAnim.ease(k / 0.45F);
                from.visible = true;
                from.x -= 2.4F * e;
                from.xRot = 1.5708F * e;
                from.xScale = from.yScale = from.zScale = 1.0F - 0.85F * e;
            } else {                                                             // the new one rolls out and locks
                float e = Mt7cAnim.ease((k - 0.45F) / 0.55F);
                to.visible = true;
                to.x -= 2.4F * (1.0F - e);
                to.xRot = -1.5708F * (1.0F - e);
                to.xScale = to.yScale = to.zScale = 0.15F + 0.85F * e;
            }
        }

        // ---- placement: a diagonal like a tool sprite, then the motion
        float motion = fp ? 1.0F : holder != null ? 0.45F : 0.0F;
        ps.pushPose();
        ps.translate(0.5D, 0.5D, 0.5D);
        ps.mulPose(Axis.ZP.rotationDegrees(45.0F));
        if (ctx == ItemDisplayContext.GUI) ps.mulPose(Axis.XP.rotationDegrees(25.0F));
        if (motion > 0.0F) {
            ps.translate(p.dx * motion * SCALE / 16.0F, p.dy * motion * SCALE / 16.0F, 0.0D);
            ps.mulPose(Axis.ZP.rotation(p.tilt * motion));
            ps.mulPose(Axis.XP.rotation(p.roll * motion));
        }
        ps.scale(SCALE, SCALE, SCALE);
        ps.scale(1.0F, -1.0F, -1.0F);
        ps.translate(-CENTER / 16.0F, 0.0D, 0.0D);
        model.renderToBuffer(ps, ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEX), false, stack.hasFoil()),
                light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        model.renderToBuffer(ps, buffers.getBuffer(RenderType.eyes(GLOW)), 15728880, overlay, p.r, p.g, p.b, 1.0F);
        ps.popPose();
    }
}
