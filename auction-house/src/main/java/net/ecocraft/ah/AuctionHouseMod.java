package net.ecocraft.ah;

import net.ecocraft.ah.registry.AHRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(AuctionHouseMod.MOD_ID)
public class AuctionHouseMod {

    public static final String MOD_ID = "ecocraft_ah";

    public AuctionHouseMod(IEventBus modBus, ModContainer container) {
        AHRegistries.register(modBus);
    }
}
