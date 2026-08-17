package com.qeapi.item;

import com.qeapi.QuestAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// vanilla's GrindstoneMenu only accepts damageable/enchanted items in its repair slots, so this is a standalone right-click interaction instead of a menu slot
public final class QuestItemRecycling {

    private QuestItemRecycling() {}

    public static boolean tryRecycle(ServerPlayer player, InteractionHand hand, Level level, BlockPos pos) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(QuestItems.QUEST_ITEM)) {
            return false;
        }

        stack.shrink(1);
        player.giveExperiencePoints(1);
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);

        QuestAPI.LOGGER.debug("Player {} recycled a quest item at a grindstone for 1 xp",
                player.getName().getString());
        return true;
    }
}
