package net.ecocraft.mail.compat.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public class MailKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(MailEventGroup.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("EcoMail", MailBindings.class);
    }
}
