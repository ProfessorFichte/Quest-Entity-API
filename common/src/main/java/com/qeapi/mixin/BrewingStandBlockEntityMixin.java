package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Tracks potion brewing for quest tasks. doBrew runs exactly once per completed brew cycle - by
// the TAIL injection point the output slots (0-2) already hold the resulting potions.
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {

    private static final int BREW_ATTRIBUTION_RADIUS = 8;

    @Inject(method = "doBrew", at = @At("TAIL"))
    private static void qe_api$onDoBrew(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(
                ServerPlayer.class,
                new AABB(pos).inflate(BREW_ATTRIBUTION_RADIUS)
        );
        if (nearbyPlayers.isEmpty()) return;

        for (int slot = 0; slot < 3; slot++) {
            ItemStack potionStack = items.get(slot);
            if (potionStack.isEmpty()) continue;

            for (ServerPlayer player : nearbyPlayers) {
                QuestEventHandler.onPotionBrewed(player, potionStack);
            }
        }
    }
}
