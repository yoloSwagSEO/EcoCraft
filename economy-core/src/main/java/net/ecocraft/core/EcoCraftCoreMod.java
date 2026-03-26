package net.ecocraft.core;

import net.ecocraft.core.config.EcoConfig;
import net.ecocraft.core.registry.EcoRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(EcoCraftCoreMod.MOD_ID)
public class EcoCraftCoreMod {
    public static final String MOD_ID = "ecocraft_core";

    public EcoCraftCoreMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, EcoConfig.CONFIG_SPEC);
        EcoRegistries.register(modBus);
    }
}
