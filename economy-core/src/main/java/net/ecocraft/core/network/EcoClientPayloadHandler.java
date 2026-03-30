package net.ecocraft.core.network;

import net.ecocraft.core.network.payload.*;
import net.ecocraft.core.screen.ExchangeScreen;
import net.ecocraft.core.vault.VaultScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Client-side payload handlers for economy-core network messages.
 */
public final class EcoClientPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EcoClientPayloadHandler() {}

    // ========== Exchange handlers ==========

    public static void handleOpenExchange(OpenExchangePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("[Exchange Client] Opening exchange screen, entityId={}", payload.entityId());
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new ExchangeScreen(payload.entityId()));
        });
    }

    public static void handleExchangeData(ExchangeDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("[Exchange Client] Received exchange data: {} currencies, fee={}%",
                    payload.currencies().size(), payload.feePercent());
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ExchangeScreen screen) {
                screen.receiveExchangeData(payload);
            }
        });
    }

    public static void handleExchangeResult(ExchangeResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("[Exchange Client] Received exchange result: success={}, message='{}'",
                    payload.success(), payload.message());
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ExchangeScreen screen) {
                screen.receiveExchangeResult(payload);
            }
        });
    }

    // ========== Vault handlers ==========

    public static void handleOpenVault(OpenVaultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("[Vault Client] Opening vault screen");
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new VaultScreen());
        });
    }

    public static void handleVaultData(VaultDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("[Vault Client] Received vault data: {} currencies", payload.currencies().size());
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof VaultScreen screen) {
                screen.receiveVaultData(payload);
            }
        });
    }

    public static void handleVaultResult(VaultResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("[Vault Client] Received vault result: success={}, message='{}'",
                    payload.success(), payload.message());
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof VaultScreen screen) {
                screen.receiveVaultResult(payload);
            }
        });
    }
}
