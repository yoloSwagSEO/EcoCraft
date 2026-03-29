package net.ecocraft.mail;

import net.ecocraft.mail.config.MailConfig;
import net.ecocraft.mail.entity.PostmanEntity;
import net.ecocraft.mail.entity.PostmanRenderer;
import net.ecocraft.mail.network.MailNetworkHandler;
import net.ecocraft.mail.registry.MailRegistries;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(MailMod.MOD_ID)
public class MailMod {
    public static final String MOD_ID = "ecocraft_mail";

    public MailMod(IEventBus modBus, ModContainer container) {
        MailRegistries.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, MailConfig.CONFIG_SPEC);
        modBus.register(MailNetworkHandler.class);
        modBus.addListener(this::registerRenderers);
        modBus.addListener(this::registerAttributes);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MailRegistries.POSTMAN.get(), PostmanRenderer::new);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(MailRegistries.POSTMAN.get(),
                PostmanEntity.createMobAttributes()
                        .add(Attributes.MAX_HEALTH, 20.0)
                        .add(Attributes.MOVEMENT_SPEED, 0.0)
                        .build()
        );
    }
}
