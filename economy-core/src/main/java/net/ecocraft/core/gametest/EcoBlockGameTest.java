package net.ecocraft.core.gametest;

import net.ecocraft.core.EcoCraftCoreMod;
import net.ecocraft.core.registry.EcoRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests for economy blocks (vault, exchange).
 * Run via: ./gradlew :economy-core:runGameTestServer
 */
@GameTestHolder(EcoCraftCoreMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class EcoBlockGameTest {

    @GameTest(template = "empty3x3x3")
    public static void testVaultBlockPlacement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, EcoRegistries.VAULT_BLOCK.get());
        helper.assertBlockPresent(EcoRegistries.VAULT_BLOCK.get(), pos);
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void testExchangeBlockPlacement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, EcoRegistries.EXCHANGE_BLOCK.get());
        helper.assertBlockPresent(EcoRegistries.EXCHANGE_BLOCK.get(), pos);
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void testVaultBlockHasBlockEntity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, EcoRegistries.VAULT_BLOCK.get());
        var blockEntity = helper.getBlockEntity(pos);
        if (blockEntity == null) {
            helper.fail("Vault block should have a block entity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void testExchangeBlockHasBlockEntity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, EcoRegistries.EXCHANGE_BLOCK.get());
        var blockEntity = helper.getBlockEntity(pos);
        if (blockEntity == null) {
            helper.fail("Exchange block should have a block entity");
        }
        helper.succeed();
    }
}
