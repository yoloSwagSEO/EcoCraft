package net.ecocraft.core.network;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeRate;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.core.EcoServerEvents;
import net.ecocraft.core.config.EcoConfig;
import net.ecocraft.core.network.payload.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers all economy-core network payloads on the MOD event bus.
 */
public final class EcoNetworkHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EcoNetworkHandler() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Server -> Client
        registrar.playToClient(
                OpenExchangePayload.TYPE,
                OpenExchangePayload.STREAM_CODEC,
                EcoClientPayloadHandler::handleOpenExchange
        );

        registrar.playToClient(
                ExchangeDataPayload.TYPE,
                ExchangeDataPayload.STREAM_CODEC,
                EcoClientPayloadHandler::handleExchangeData
        );

        registrar.playToClient(
                ExchangeResultPayload.TYPE,
                ExchangeResultPayload.STREAM_CODEC,
                EcoClientPayloadHandler::handleExchangeResult
        );

        // Client -> Server
        registrar.playToServer(
                ExchangeRequestPayload.TYPE,
                ExchangeRequestPayload.STREAM_CODEC,
                EcoNetworkHandler::handleExchangeRequest
        );
    }

    /**
     * Sends exchange data (currencies, balances, rates, config) to a player.
     */
    public static void sendExchangeData(ServerPlayer player) {
        var ctx = EcoServerEvents.getContext();
        if (ctx == null) return;

        CurrencyRegistry registry = ctx.getCurrencyRegistry();
        var economy = ctx.getEconomyProvider();
        double feePercent = EcoConfig.CONFIG.exchangeGlobalFeePercent.get();

        List<ExchangeDataPayload.CurrencyData> currencies = new ArrayList<>();
        for (Currency c : registry.listAll()) {
            if (!c.exchangeable()) continue;
            long balance = economy.getVirtualBalance(player.getUUID(), c).longValue();
            currencies.add(new ExchangeDataPayload.CurrencyData(
                    c.id(), c.name(), c.symbol(), c.decimals(),
                    balance, c.referenceRate().doubleValue(), c.exchangeable()
            ));
        }

        PacketDistributor.sendToPlayer(player, new ExchangeDataPayload(currencies, feePercent));
    }

    /**
     * Handles a conversion request from the client.
     */
    private static void handleExchangeRequest(ExchangeRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var ctx = EcoServerEvents.getContext();
            if (ctx == null) {
                PacketDistributor.sendToPlayer(player, new ExchangeResultPayload(false, "Server not ready", 0));
                return;
            }

            CurrencyRegistry registry = ctx.getCurrencyRegistry();
            ExchangeService exchange = ctx.getExchangeService();

            Currency from = registry.getById(payload.fromCurrencyId());
            Currency to = registry.getById(payload.toCurrencyId());
            if (from == null || to == null) {
                PacketDistributor.sendToPlayer(player, new ExchangeResultPayload(false, "Unknown currency", 0));
                return;
            }

            if (payload.amount() <= 0) {
                PacketDistributor.sendToPlayer(player, new ExchangeResultPayload(false, "Invalid amount", 0));
                return;
            }

            BigDecimal amount = BigDecimal.valueOf(payload.amount());
            var result = exchange.convert(player.getUUID(), amount, from, to);

            if (result.successful()) {
                // Get the new balance to calculate converted amount
                long newToBalance = ctx.getEconomyProvider().getVirtualBalance(player.getUUID(), to).longValue();
                String fromFormatted = CurrencyFormatter.format(payload.amount(), from);
                String toFormatted = CurrencyFormatter.format(newToBalance, to);

                PacketDistributor.sendToPlayer(player, new ExchangeResultPayload(true,
                        fromFormatted + " -> " + toFormatted, newToBalance));

                // Send updated balances
                sendExchangeData(player);
            } else {
                PacketDistributor.sendToPlayer(player, new ExchangeResultPayload(false,
                        result.errorMessage() != null ? result.errorMessage() : "Conversion failed", 0));
            }
        });
    }
}
