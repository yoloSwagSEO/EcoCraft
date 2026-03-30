package net.ecocraft.core.exchange;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Renders the exchanger NPC using the player model.
 * Uses the configured skin profile if available, otherwise falls back to the default Steve skin.
 */
public class ExchangerRenderer extends MobRenderer<ExchangerEntity, PlayerModel<ExchangerEntity>> {

    public ExchangerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(ExchangerEntity entity) {
        Optional<GameProfile> profile = entity.getSkinProfile();
        if (profile.isPresent()) {
            GameProfile gp = profile.get();
            if (gp.getProperties().containsKey("textures")) {
                PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(gp);
                return skin.texture();
            }
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    protected boolean shouldShowName(ExchangerEntity entity) {
        return true;
    }
}
