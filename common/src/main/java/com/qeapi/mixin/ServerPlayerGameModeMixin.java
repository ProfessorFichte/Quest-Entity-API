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

// Tracks block breaking for MineBlockTask. destroyBlock is core vanilla server logic (not
// Fabric/NeoForge-specific), so a single shared mixin covers both loaders - same pattern as
// LivingEntityMixin/BrewingStandBlockEntityMixin.
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    protected ServerPlayer player;

    private BlockState qe_api$minedState;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void qe_api$onDestroyBlockStart(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        qe_api$minedState = player.level().getBlockState(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void qe_api$onDestroyBlockEnd(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && qe_api$minedState != null) {
            QuestEventHandler.onBlockMined(player, qe_api$minedState);
        }
        qe_api$minedState = null;
    }
}
