package net.ecocraft.ah.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the auctioneer NPC as a floating nametag only.
 * No body is rendered — the nametag "§6Commissaire-priseur" is always visible.
 */
public class AuctioneerRenderer extends EntityRenderer<AuctioneerEntity> {

    public AuctioneerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AuctioneerEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/entity/steve.png");
    }

    @Override
    protected boolean shouldShowName(AuctioneerEntity entity) {
        return true;
    }
}
