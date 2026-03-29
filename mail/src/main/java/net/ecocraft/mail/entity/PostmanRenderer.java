package net.ecocraft.mail.entity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the postman NPC using the player model with default Steve skin.
 */
public class PostmanRenderer extends MobRenderer<PostmanEntity, PlayerModel<PostmanEntity>> {

    public PostmanRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(PostmanEntity entity) {
        return DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    protected boolean shouldShowName(PostmanEntity entity) {
        return true;
    }
}
