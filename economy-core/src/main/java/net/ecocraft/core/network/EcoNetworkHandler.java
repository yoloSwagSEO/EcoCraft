package net.ecocraft.core.network;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.currency.SubUnit;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.core.EcoServerEvents;
import net.ecocraft.core.config.EcoConfig;
import net.ecocraft.core.network.payload.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

        // --- Exchange payloads ---

        // Server -> Client (lambdas defer client class loading for dist safety)
        registrar.playToClient(
                OpenExchangePayload.TYPE,
                OpenExchangePayload.STREAM_CODEC,
                (payload, ctx) -> EcoClientPayloadHandler.handleOpenExchange(payload, ctx)
        );

        registrar.playToClient(
                ExchangeDataPayload.TYPE,
                ExchangeDataPayload.STREAM_CODEC,
                (payload, ctx) -> EcoClientPayloadHandler.handleExchangeData(payload, ctx)
        );

        registrar.playToClient(
                ExchangeResultPayload.TYPE,
                ExchangeResultPayload.STREAM_CODEC,
                (payload, ctx) -> EcoClientPayloadHandler.handleExchangeResult(payload, ctx)
        );

        registrar.playToClient(
                ExchangerSkinPayload.TYPE,
                ExchangerSkinPayload.STREAM_CODEC,
                (payload, ctx) -> EcoClientPayloadHandler.handleExchangerSkin(payload, ctx)
        );

        // Client -> Server
        registrar.playToServer(
                ExchangeRequestPayload.TYPE,
                ExchangeRequestPayload.STREAM_CODEC,
                EcoNetworkHandler::handleExchangeRequest
        );

        registrar.playToServer(
                UpdateExchangerSkinPayload.TYPE,
                UpdateExchangerSkinPayload.STREAM_CODEC,
                EcoNetworkHandler::handleUpdateExchangerSkin
        );

        // --- Vault payloads ---

        // Server -> Client (lambdas defer client class loading for dist safety)
        registrar.playToClient(
                OpenVaultPayload.TYPE,
                OpenVaultPayload.STREAM_CODEC,
                (payload, ctx) -> EcoClientPayloadHandler.handleOpenVault(payload, ctx)
        );

        registrar.playToClient(
                VaultDataPayload.TYPE,
                VaultDataPayload.STREAM_CODEC,
                (payload, ctx) -> EcoClientPayloadHandler.handleVaultData(payload, ctx)
        );

        registrar.playToClient(
                VaultResultPayload.TYPE,
                VaultResultPayload.STREAM_CODEC,
                (payload, ctx) -> EcoClientPayloadHandler.handleVaultResult(payload, ctx)
        );

        // Client -> Server
        registrar.playToServer(
                VaultWithdrawPayload.TYPE,
                VaultWithdrawPayload.STREAM_CODEC,
                EcoNetworkHandler::handleVaultWithdraw
        );

        registrar.playToServer(
                VaultDepositPayload.TYPE,
                VaultDepositPayload.STREAM_CODEC,
                EcoNetworkHandler::handleVaultDeposit
        );
    }

    // ========== Exchange handlers ==========

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
        LOGGER.info("[Exchange] Registry has {} currencies", registry.listAll().size());
        for (Currency c : registry.listAll()) {
            LOGGER.info("[Exchange] Currency: id={}, exchangeable={}, rate={}", c.id(), c.exchangeable(), c.referenceRate());
            if (!c.exchangeable()) continue;
            long balance = economy.getVirtualBalance(player.getUUID(), c).longValue();
            currencies.add(new ExchangeDataPayload.CurrencyData(
                    c.id(), c.name(), c.symbol(), c.decimals(),
                    balance, c.referenceRate().doubleValue(), c.exchangeable()
            ));
        }
        LOGGER.info("[Exchange] Sending {} exchangeable currencies to {}", currencies.size(), player.getName().getString());

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
                long newToBalance = ctx.getEconomyProvider().getVirtualBalance(player.getUUID(), to).longValue();
                String fromFormatted = CurrencyFormatter.format(payload.amount(), from);
                String toFormatted = CurrencyFormatter.format(newToBalance, to);

                PacketDistributor.sendToPlayer(player, new ExchangeResultPayload(true,
                        fromFormatted + " -> " + toFormatted, newToBalance));

                sendExchangeData(player);
            } else {
                PacketDistributor.sendToPlayer(player, new ExchangeResultPayload(false,
                        result.errorMessage() != null ? result.errorMessage() : "Conversion failed", 0));
            }
        });
    }

    // ========== Exchanger skin handlers ==========

    /**
     * Sends the current skin player name for an Exchanger NPC to a player.
     */
    public static void sendExchangerSkin(ServerPlayer player, int entityId) {
        if (entityId <= 0) return;
        var entity = player.level().getEntity(entityId);
        if (entity instanceof net.ecocraft.core.exchange.ExchangerEntity exchanger) {
            PacketDistributor.sendToPlayer(player, new ExchangerSkinPayload(entityId, exchanger.getSkinPlayerName()));
        }
    }

    /**
     * Handles a skin update request from an admin client.
     */
    private static void handleUpdateExchangerSkin(UpdateExchangerSkinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                return;
            }

            var entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof net.ecocraft.core.exchange.ExchangerEntity exchanger)) {
                return;
            }

            String skinName = payload.skinPlayerName().trim();
            exchanger.setSkinPlayerName(skinName);

            if (skinName.isEmpty()) {
                exchanger.setSkinProfile(null);
                LOGGER.info("[Exchange] Exchanger skin reset for entity {}", payload.entityId());
                return;
            }

            // Resolve GameProfile asynchronously
            var server = player.getServer();
            if (server == null) return;

            net.minecraft.Util.backgroundExecutor().execute(() -> {
                try {
                    var profileCache = server.getProfileCache();
                    if (profileCache == null) {
                        LOGGER.warn("[Exchange] Profile cache unavailable for skin resolution");
                        return;
                    }
                    var optProfile = profileCache.get(skinName);
                    if (optProfile.isEmpty()) {
                        LOGGER.warn("[Exchange] Player not found for skin: {}", skinName);
                        return;
                    }

                    var profile = optProfile.get();
                    var profileResult = server.getSessionService().fetchProfile(profile.getId(), true);
                    if (profileResult == null) {
                        LOGGER.warn("[Exchange] Could not fetch profile for skin: {}", skinName);
                        return;
                    }
                    var filledProfile = profileResult.profile();

                    server.execute(() -> {
                        exchanger.setSkinProfile(filledProfile);
                        LOGGER.info("[Exchange] Exchanger skin updated to '{}' for entity {}", skinName, payload.entityId());
                    });
                } catch (Exception e) {
                    LOGGER.error("Error resolving exchanger skin for " + skinName, e);
                }
            });
        });
    }

    // ========== Vault handlers ==========

    /**
     * Sends vault data (all currencies with balances and physical info) to a player.
     */
    public static void sendVaultData(ServerPlayer player) {
        var ctx = EcoServerEvents.getContext();
        if (ctx == null) return;

        CurrencyRegistry registry = ctx.getCurrencyRegistry();
        var economy = ctx.getEconomyProvider();

        List<VaultDataPayload.VaultCurrencyData> currencies = new ArrayList<>();
        for (Currency c : registry.listAll()) {
            long balance = economy.getVirtualBalance(player.getUUID(), c).longValue();

            boolean isPhysical = c.physical() || hasPhysicalSubUnits(c);
            List<VaultDataPayload.VaultSubUnitData> subUnitData = new ArrayList<>();
            if (isPhysical) {
                // If the currency has sub-units with itemIds, use those
                for (SubUnit su : c.subUnits()) {
                    if (su.itemId() != null && !su.itemId().isBlank()) {
                        subUnitData.add(new VaultDataPayload.VaultSubUnitData(
                                su.code(), su.name(), su.multiplier(), su.itemId()));
                    }
                }
                // If the currency itself is physical but has no sub-units, create a single entry
                if (subUnitData.isEmpty() && c.physical() && c.itemId() != null) {
                    subUnitData.add(new VaultDataPayload.VaultSubUnitData(
                            c.symbol(), c.name(), 1, c.itemId()));
                }
            }

            currencies.add(new VaultDataPayload.VaultCurrencyData(
                    c.id(), c.name(), c.symbol(), c.decimals(),
                    balance, !subUnitData.isEmpty(), subUnitData
            ));
        }

        PacketDistributor.sendToPlayer(player, new VaultDataPayload(currencies));
    }

    private static boolean hasPhysicalSubUnits(Currency currency) {
        for (SubUnit su : currency.subUnits()) {
            if (su.itemId() != null && !su.itemId().isBlank()) return true;
        }
        return false;
    }

    /**
     * Handles a vault withdraw request: deduct balance, spawn physical items.
     */
    private static void handleVaultWithdraw(VaultWithdrawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var ctx = EcoServerEvents.getContext();
            if (ctx == null) {
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, "Server not ready"));
                return;
            }

            CurrencyRegistry registry = ctx.getCurrencyRegistry();
            Currency currency = registry.getById(payload.currencyId());
            if (currency == null) {
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, "Unknown currency"));
                return;
            }

            long amount = payload.amount();
            if (amount <= 0) {
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, "Invalid amount"));
                return;
            }

            // Find physical sub-units to give items
            List<SubUnit> physicalUnits = new ArrayList<>();
            for (SubUnit su : currency.subUnits()) {
                if (su.itemId() != null && !su.itemId().isBlank()) {
                    physicalUnits.add(su);
                }
            }
            // If no sub-units, check currency itself
            if (physicalUnits.isEmpty() && currency.physical() && currency.itemId() != null) {
                physicalUnits.add(new SubUnit(currency.symbol(), currency.name(), 1, currency.itemId(), null));
            }

            if (physicalUnits.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, "No physical items for this currency"));
                return;
            }

            // Check balance
            var economy = ctx.getEconomyProvider();
            BigDecimal balance = economy.getVirtualBalance(player.getUUID(), currency);
            if (balance.longValue() < amount) {
                String msg = net.minecraft.network.chat.Component.translatable("ecocraft.vault.insufficient").getString();
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, msg));
                return;
            }

            // Calculate items to give (highest denomination first)
            long remaining = amount;
            List<ItemStack> itemsToGive = new ArrayList<>();
            for (SubUnit su : physicalUnits) {
                if (remaining <= 0) break;
                long count = remaining / su.multiplier();
                remaining = remaining % su.multiplier();
                if (count > 0) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(su.itemId()));
                    if (item == net.minecraft.world.item.Items.AIR) continue;
                    // Split into stacks of max 64
                    while (count > 0) {
                        int stackSize = (int) Math.min(count, 64);
                        itemsToGive.add(new ItemStack(item, stackSize));
                        count -= stackSize;
                    }
                }
            }

            // Check inventory space (rough check)
            int freeSlots = 0;
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                if (player.getInventory().items.get(i).isEmpty()) freeSlots++;
            }
            if (freeSlots < itemsToGive.size()) {
                String msg = net.minecraft.network.chat.Component.translatable("ecocraft.vault.inventory_full").getString();
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, msg));
                return;
            }

            // Deduct balance (use the amount minus remaining, in case some couldn't be represented)
            long actualAmount = amount - remaining;
            if (actualAmount <= 0) {
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, "Amount too small"));
                return;
            }

            var result = economy.withdraw(player.getUUID(), BigDecimal.valueOf(actualAmount), currency);
            if (!result.successful()) {
                String msg = net.minecraft.network.chat.Component.translatable("ecocraft.vault.insufficient").getString();
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, msg));
                return;
            }

            // Give items
            for (ItemStack stack : itemsToGive) {
                if (!player.getInventory().add(stack)) {
                    // Drop on ground as fallback
                    player.drop(stack, false);
                }
            }

            String formatted = CurrencyFormatter.format(actualAmount, currency);
            String msg = net.minecraft.network.chat.Component.translatable("ecocraft.vault.withdraw_success", formatted).getString();
            PacketDistributor.sendToPlayer(player, new VaultResultPayload(true, msg));

            // Refresh data
            sendVaultData(player);
        });
    }

    /**
     * Handles a vault deposit request: remove physical money items from inventory, add to balance.
     */
    private static void handleVaultDeposit(VaultDepositPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var ctx = EcoServerEvents.getContext();
            if (ctx == null) {
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, "Server not ready"));
                return;
            }

            int slotIndex = payload.slotIndex();
            int depositCount = payload.amount();

            if (slotIndex < 0 || slotIndex >= player.getInventory().items.size()) {
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, "Invalid slot"));
                return;
            }

            ItemStack stack = player.getInventory().items.get(slotIndex);
            if (stack.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, "Empty slot"));
                return;
            }

            if (depositCount <= 0 || depositCount > stack.getCount()) {
                depositCount = stack.getCount();
            }

            // Find which currency/sub-unit this item matches
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            CurrencyRegistry registry = ctx.getCurrencyRegistry();
            Currency matchedCurrency = null;
            long valuePerItem = 0;

            for (Currency c : registry.listAll()) {
                // Check sub-units
                for (SubUnit su : c.subUnits()) {
                    if (itemId.equals(su.itemId())) {
                        matchedCurrency = c;
                        valuePerItem = su.multiplier();
                        break;
                    }
                }
                if (matchedCurrency != null) break;

                // Check currency itself
                if (c.physical() && itemId.equals(c.itemId())) {
                    matchedCurrency = c;
                    valuePerItem = 1;
                    break;
                }
            }

            if (matchedCurrency == null) {
                PacketDistributor.sendToPlayer(player, new VaultResultPayload(false, "Not a money item"));
                return;
            }

            long totalValue = valuePerItem * depositCount;

            // Remove items
            stack.shrink(depositCount);
            if (stack.isEmpty()) {
                player.getInventory().items.set(slotIndex, ItemStack.EMPTY);
            }

            // Add to balance
            var economy = ctx.getEconomyProvider();
            economy.deposit(player.getUUID(), BigDecimal.valueOf(totalValue), matchedCurrency);

            String formatted = CurrencyFormatter.format(totalValue, matchedCurrency);
            String msg = net.minecraft.network.chat.Component.translatable("ecocraft.vault.deposit_success", formatted).getString();
            PacketDistributor.sendToPlayer(player, new VaultResultPayload(true, msg));

            // Refresh data
            sendVaultData(player);
        });
    }
}
