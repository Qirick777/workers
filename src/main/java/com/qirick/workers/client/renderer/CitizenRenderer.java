package com.qirick.workers.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qirick.workers.client.ModModelLayers;
import com.qirick.workers.entity.citizen.CitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a Citizen with the vanilla player model.
 *
 * <p>A renderer normally owns a single model instance, but a Citizen's model
 * depends on its gender, so both variants are baked up front and the active one
 * is swapped in per entity before rendering. The render layers read the model
 * back through {@link #getModel()}, so they follow the swap automatically.
 */
public class CitizenRenderer extends LivingEntityRenderer<CitizenEntity, PlayerModel<CitizenEntity>> {

    /** The player model is authored slightly larger than one block wide; vanilla scales it down. */
    private static final float MODEL_SCALE = 0.9375F;

    private final PlayerModel<CitizenEntity> wideModel;
    private final PlayerModel<CitizenEntity> slimModel;

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModModelLayers.CITIZEN_WIDE), false), 0.5F);

        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModModelLayers.CITIZEN_SLIM), true);

        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
    }

    @Override
    public void render(CitizenEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        this.model = entity.getGender().isSlim() ? this.slimModel : this.wideModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CitizenEntity entity) {
        return entity.getGender().getTexture();
    }

    @Override
    protected void scale(CitizenEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }
}
