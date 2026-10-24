package com.geist.client.exo;

import com.geist.GeistMod;
import com.geist.item.ExoFrameItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Draws the exo-frame on any player wearing it, following their exact pose, with glowing status lights. */
public class ExoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEX = new ResourceLocation(GeistMod.MODID, "textures/models/exo/exo_frame.png");
    public static final ResourceLocation GLOW = new ResourceLocation(GeistMod.MODID, "textures/models/exo/exo_frame_glow.png");
    private final ExoModel model;

    public ExoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new ExoModel(models.bakeLayer(ExoModel.LAYER));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack exo = ExoFrameItem.worn(player);
        if (exo.isEmpty() || player.isInvisible()) return;
        this.getParentModel().copyPropertiesTo(model);
        model.hideGhost();
        ExoAnimator.animate(model, player, exo, partialTick);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        float[] g = ExoAnimator.glow(player, exo, partialTick);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 15728880, OverlayTexture.NO_OVERLAY, g[0], g[1], g[2], 1.0F);
    }
}
