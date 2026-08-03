package com.qirick.workers.client;

import com.qirick.workers.Workers;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public final class ModModelLayers {

    /** Citizen built on the 4px-arm ("Steve") player model. */
    public static final ModelLayerLocation CITIZEN_WIDE =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Workers.MOD_ID, "citizen"), "main");

    /** Citizen built on the 3px-arm ("Alex") player model. */
    public static final ModelLayerLocation CITIZEN_SLIM =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Workers.MOD_ID, "citizen_slim"), "main");

    private ModModelLayers() {
    }
}
