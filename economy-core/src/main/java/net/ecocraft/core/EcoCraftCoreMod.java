package net.ecocraft.core;

import net.ecocraft.core.config.EcoConfig;
import net.ecocraft.core.exchange.ExchangerEntity;
import net.ecocraft.core.exchange.ExchangerRenderer;
import net.ecocraft.core.network.EcoNetworkHandler;
import net.ecocraft.core.registry.EcoRegistries;
import net.ecocraft.core.vault.VaultScreen;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(EcoCraftCoreMod.MOD_ID)
public class EcoCraftCoreMod {
    public static final String MOD_ID = "ecocraft_core";

    public EcoCraftCoreMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, EcoConfig.CONFIG_SPEC);
        EcoRegistries.register(modBus);
        modBus.register(EcoNetworkHandler.class);
        modBus.addListener(this::registerAttributes);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(EcoCraftCoreMod::registerScreens);
            modBus.addListener(EcoCraftCoreMod::registerRenderers);
        }
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EcoRegistries.VAULT_MENU.get(), VaultScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EcoRegistries.EXCHANGER.get(), ExchangerRenderer::new);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(EcoRegistries.EXCHANGER.get(),
                ExchangerEntity.createMobAttributes()
                        .add(Attributes.MAX_HEALTH, 20.0)
                        .add(Attributes.MOVEMENT_SPEED, 0.0)
                        .build()
        );
    }
}
