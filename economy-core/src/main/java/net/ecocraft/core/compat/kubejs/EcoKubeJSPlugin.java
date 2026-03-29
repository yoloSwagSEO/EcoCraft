package net.ecocraft.core.compat.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public class EcoKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(EcoEventGroup.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("EcoEconomy", EcoBindings.class);
    }
}
