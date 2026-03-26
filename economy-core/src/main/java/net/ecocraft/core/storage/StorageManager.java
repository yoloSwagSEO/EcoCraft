package net.ecocraft.core.storage;

import net.ecocraft.core.config.EcoConfig;

import java.nio.file.Path;

public class StorageManager {

    private DatabaseProvider provider;

    public void initialize(Path worldDir) {
        String type = EcoConfig.CONFIG.storageType.get();
        if ("sqlite".equalsIgnoreCase(type)) {
            provider = new SqliteDatabaseProvider(worldDir.resolve("ecocraft.db"));
        } else {
            throw new IllegalArgumentException("Unsupported storage type: " + type + ". Use 'sqlite'.");
        }
        provider.initialize();
    }

    public void shutdown() {
        if (provider != null) {
            provider.shutdown();
        }
    }

    public DatabaseProvider getProvider() {
        if (provider == null) {
            throw new IllegalStateException("StorageManager not initialized");
        }
        return provider;
    }
}
