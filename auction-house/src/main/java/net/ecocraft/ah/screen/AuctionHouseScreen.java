package net.ecocraft.ah.screen;

import com.mojang.logging.LogUtils;
import net.ecocraft.ah.network.payload.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * Main auction house screen. Stub for Task 2; fully implemented in Task 3.
 */
public class AuctionHouseScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    public AuctionHouseScreen() {
        super(Component.literal("Auction House"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Static methods called by ClientPayloadHandler ---

    public static void open() {
        Minecraft.getInstance().setScreen(new AuctionHouseScreen());
    }

    public static void receiveListings(ListingsResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveListings(payload);
        }
    }

    public static void receiveListingDetail(ListingDetailResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveListingDetail(payload);
        }
    }

    public static void receiveActionResult(AHActionResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveActionResult(payload);
        }
    }

    public static void receiveMyListings(MyListingsResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveMyListings(payload);
        }
    }

    public static void receiveLedger(LedgerResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveLedger(payload);
        }
    }

    // --- Instance methods (stubs, implemented in Task 3) ---

    protected void onReceiveListings(ListingsResponsePayload payload) {
        LOGGER.debug("onReceiveListings stub");
    }

    protected void onReceiveListingDetail(ListingDetailResponsePayload payload) {
        LOGGER.debug("onReceiveListingDetail stub");
    }

    protected void onReceiveActionResult(AHActionResultPayload payload) {
        LOGGER.debug("onReceiveActionResult stub");
    }

    protected void onReceiveMyListings(MyListingsResponsePayload payload) {
        LOGGER.debug("onReceiveMyListings stub");
    }

    protected void onReceiveLedger(LedgerResponsePayload payload) {
        LOGGER.debug("onReceiveLedger stub");
    }
}
