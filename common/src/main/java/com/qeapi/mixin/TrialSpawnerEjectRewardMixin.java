package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ejectReward fires once a wave is fully defeated and rewards are actually being granted -
// isOminous() is trivially available on `this` here, no need to separately distinguish an Ominous
// Bottle trigger from an already-ominous spawner, both funnel into isOminous() == true by this point
@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerEjectRewardMixin {

    @Inject(method = "ejectReward", at = @At("TAIL"))
    private void qe_api$onEjectReward(ServerLevel level, BlockPos pos, ResourceKey<LootTable> lootTable, CallbackInfo ci) {
        TrialSpawner self = (TrialSpawner) (Object) this;
        QuestEventHandler.onTrialSpawnerComplete(level, pos, self.isOminous());
    }
}
