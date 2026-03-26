package net.ecocraft.ah.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the auctioneer NPC using a humanoid model with a villager-like texture.
 */
public class AuctioneerRenderer extends HumanoidMobRenderer<AuctioneerEntity, HumanoidModel<AuctioneerEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/steve.png");

    public AuctioneerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(AuctioneerEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(AuctioneerEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    protected boolean shouldShowName(AuctioneerEntity entity) {
        return true;
    }
}
