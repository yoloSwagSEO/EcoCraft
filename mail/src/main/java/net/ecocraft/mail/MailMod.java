package net.ecocraft.mail;

import net.ecocraft.mail.config.MailConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(MailMod.MOD_ID)
public class MailMod {
    public static final String MOD_ID = "ecocraft_mail";

    public MailMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, MailConfig.CONFIG_SPEC);
    }
}
