package net.ecocraft.ah.compat.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public class AHKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(AHEventGroup.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("AHAuctions", AHBindings.class);
    }
}
