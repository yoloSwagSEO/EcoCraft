package net.ecocraft.core.gametest;

import net.ecocraft.core.EcoCraftCoreMod;
import net.ecocraft.core.registry.EcoRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests for economy entities (exchanger NPC).
 * Run via: ./gradlew :economy-core:runGameTestServer
 */
@GameTestHolder(EcoCraftCoreMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class EcoEntityGameTest {

    @GameTest(template = "empty3x3x3")
    public static void testExchangerEntitySpawn(GameTestHelper helper) {
        helper.spawn(EcoRegistries.EXCHANGER.get(), new BlockPos(1, 1, 1));
        helper.assertEntityPresent(EcoRegistries.EXCHANGER.get());
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void testExchangerEntityPersistence(GameTestHelper helper) {
        var exchanger = helper.spawn(EcoRegistries.EXCHANGER.get(), new BlockPos(1, 1, 1));

        if (!exchanger.requiresCustomPersistence()) {
            helper.fail("Exchanger entity should require custom persistence");
        }

        helper.succeed();
    }
}
