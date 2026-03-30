package net.ecocraft.core.gametest;

import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests for economy commands.
 * Run via: ./gradlew :economy-core:runGameTestServer
 */
@GameTestHolder(EcoCraftCoreMod.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public class EcoCommandGameTest {

    @GameTest(template = "empty3x3x3")
    public static void testBalanceCommand(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();

        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack(), "balance");

        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void testEcoGiveCommand(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();

        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4),
                "eco give " + player.getName().getString() + " 100");

        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void testPayCommand(GameTestHelper helper) {
        var player1 = helper.makeMockServerPlayerInLevel();
        var player2 = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();

        // Give player1 some money first
        server.getCommands().performPrefixedCommand(
                player1.createCommandSourceStack().withPermission(4),
                "eco give " + player1.getName().getString() + " 1000");

        // Pay player2
        server.getCommands().performPrefixedCommand(
                player1.createCommandSourceStack(),
                "pay " + player2.getName().getString() + " 100");

        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void testCurrencyListCommand(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();

        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack(), "currency list");

        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void testCreateCurrencyCommand(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();

        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4),
                "eco createcurrency testgems TestGems TG 2.0");

        helper.succeed();
    }
}
