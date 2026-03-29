package net.ecocraft.mail;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(MailMod.MOD_ID)
public class MailMod {
    public static final String MOD_ID = "ecocraft_mail";

    public MailMod(IEventBus modBus, ModContainer container) {
        // Registries, config, and event handlers will be added in subsequent tasks
    }
}
