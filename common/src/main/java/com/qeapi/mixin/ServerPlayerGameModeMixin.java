package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Tracks block breaking for MineBlockTask; destroyBlock is core vanilla logic (not loader-specific)
// so one shared mixin covers both loaders, same as LivingEntityMixin/BrewingStandBlockEntityMixin.
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    protected ServerPlayer player;

    private BlockState quest_api$minedState;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void quest_api$onDestroyBlockStart(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        quest_api$minedState = player.level().getBlockState(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void quest_api$onDestroyBlockEnd(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && quest_api$minedState != null) {
            QuestEventHandler.onBlockMined(player, quest_api$minedState);
        }
        quest_api$minedState = null;
    }
}
