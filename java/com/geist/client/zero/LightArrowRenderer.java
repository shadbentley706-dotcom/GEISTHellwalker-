package com.geist.client.zero;

import com.geist.GeistMod;
import com.geist.entity.LightArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** A light arrow: black shaft and fletching, a cyan head, drawn at full brightness (it glows). */
public class LightArrowRenderer extends ArrowRenderer<LightArrowEntity> {
    public static final ResourceLocation TEX = new ResourceLocation(GeistMod.MODID, "textures/entity/light_arrow.png");

    public LightArrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(LightArrowEntity entity) {
        return TEX;
    }

    @Override
    protected int getBlockLightLevel(LightArrowEntity entity, BlockPos pos) {
        return 15;
    }
}
