package com.geist.client;

import com.geist.GeistMod;
import com.geist.registry.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeistMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRenderers {
    private ClientRenderers() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SMOKE_PELLET.get(), ThrownItemRenderer::new);   // shows the 3D pellet model
        event.registerEntityRenderer(ModEntities.SMOKE_CLOUD.get(), NoopRenderer::new);           // the cloud is all particles
        event.registerEntityRenderer(ModEntities.LIGHT_ARROW.get(), com.geist.client.zero.LightArrowRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(com.geist.client.exo.ExoModel.LAYER, com.geist.client.exo.ExoModel::createLayer);
        event.registerLayerDefinition(com.geist.client.gauntlet.GauntletModel.LAYER, com.geist.client.gauntlet.GauntletModel::createLayer);
        event.registerLayerDefinition(com.geist.client.mt7c.Mt7cModel.LAYER, com.geist.client.mt7c.Mt7cModel::createLayer);
        event.registerLayerDefinition(com.geist.client.zero.ZeroModel.LAYER, com.geist.client.zero.ZeroModel::createLayer);
    }

    /** The exo-frame is drawn on players (default and slim skins) by its own render layer. */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) renderer.addLayer(new com.geist.client.exo.ExoLayer(renderer, event.getEntityModels()));
            if (renderer != null) renderer.addLayer(new com.geist.client.gauntlet.GauntletLayer(renderer, event.getEntityModels()));
        }
    }
}
