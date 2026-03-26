package net.ecocraft.ah.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the auctioneer NPC using the cow model as a placeholder.
 * The cow model is simple and guaranteed to work with any Mob entity.
 */
public class AuctioneerRenderer extends MobRenderer<AuctioneerEntity, CowModel<AuctioneerEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png");

    public AuctioneerRenderer(EntityRendererProvider.Context context) {
        super(context, new CowModel<>(context.bakeLayer(ModelLayers.COW)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(AuctioneerEntity entity) {
        return TEXTURE;
    }

    @Override
    protected boolean shouldShowName(AuctioneerEntity entity) {
        return true;
    }

    @Override
    public void render(AuctioneerEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Scale down to roughly human proportions
        poseStack.pushPose();
        poseStack.scale(0.7f, 0.7f, 0.7f);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
