package net.ecocraft.mail.block;

import net.ecocraft.mail.registry.MailRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minimal block entity for the mailbox block.
 * No stored data — the mailbox is a public terminal.
 */
public class MailboxBlockEntity extends BlockEntity {

    public MailboxBlockEntity(BlockPos pos, BlockState state) {
        super(MailRegistries.MAILBOX_BLOCK_ENTITY.get(), pos, state);
    }
}
