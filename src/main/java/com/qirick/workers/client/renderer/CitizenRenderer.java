package com.qirick.workers.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qirick.workers.client.ModModelLayers;
import com.qirick.workers.entity.citizen.CitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a Citizen with the vanilla player model.
 *
 * <p>A renderer normally owns a single model instance, but a Citizen's model
 * depends on its gender, so both variants are baked up front and the active one
 * is swapped in per entity before rendering. The render layers read the model
 * back through {@link #getModel()}, so they follow the swap automatically.
 *
 * <p>Extends {@code MobRenderer} rather than {@code LivingEntityRenderer}: the latter
 * shows a name tag for any entity in range, which is the behaviour players want and
 * mobs do not. {@code MobRenderer} adds the check that limits name tags to mobs that
 * were actually named, and brings leash rendering along with it.
 */
public class CitizenRenderer extends MobRenderer<CitizenEntity, PlayerModel<CitizenEntity>> {

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
        PlayerModel<CitizenEntity> activeModel = entity.getGender().isSlim() ? this.slimModel : this.wideModel;
        this.model = activeModel;

        // ItemInHandLayer draws the held item, but only the arm pose makes the citizen
        // actually grip it instead of letting it float beside a straight arm.
        boolean mainHandOnRight = entity.getMainArm() == HumanoidArm.RIGHT;
        HumanoidModel.ArmPose mainPose = armPoseFor(entity.getItemInHand(InteractionHand.MAIN_HAND));
        HumanoidModel.ArmPose offPose = armPoseFor(entity.getItemInHand(InteractionHand.OFF_HAND));
        activeModel.rightArmPose = mainHandOnRight ? mainPose : offPose;
        activeModel.leftArmPose = mainHandOnRight ? offPose : mainPose;
        activeModel.crouching = entity.isCrouching();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * Anything held gets the plain carrying pose. The specialised poses - drawing a bow,
     * raising a shield, holding a spyglass - depend on the citizen actually using those
     * items, which it cannot do yet.
     */
    private static HumanoidModel.ArmPose armPoseFor(ItemStack stack) {
        return stack.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
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
