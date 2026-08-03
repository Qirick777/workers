package com.qirick.workers.client;

import com.qirick.workers.Workers;
import com.qirick.workers.client.renderer.CitizenRenderer;
import com.qirick.workers.entity.ModEntities;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Workers.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {

    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.CITIZEN_WIDE, () -> LayerDefinition.create(
                PlayerModel.createMesh(CubeDeformation.NONE, false), TEXTURE_WIDTH, TEXTURE_HEIGHT));
        event.registerLayerDefinition(ModModelLayers.CITIZEN_SLIM, () -> LayerDefinition.create(
                PlayerModel.createMesh(CubeDeformation.NONE, true), TEXTURE_WIDTH, TEXTURE_HEIGHT));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CITIZEN.get(), CitizenRenderer::new);
    }
}
