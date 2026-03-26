package net.ecocraft.core;

import net.ecocraft.core.config.EcoConfig;
import net.ecocraft.core.registry.EcoRegistries;
import net.ecocraft.core.vault.VaultScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(EcoCraftCoreMod.MOD_ID)
public class EcoCraftCoreMod {
    public static final String MOD_ID = "ecocraft_core";

    public EcoCraftCoreMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, EcoConfig.CONFIG_SPEC);
        EcoRegistries.register(modBus);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(EcoCraftCoreMod::registerScreens);
        }
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EcoRegistries.VAULT_MENU.get(), VaultScreen::new);
    }
}
