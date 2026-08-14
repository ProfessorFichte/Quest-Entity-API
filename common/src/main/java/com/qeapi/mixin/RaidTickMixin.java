package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Raid has no completion event and no public status getter besides the isVictory/isLoss/isOver
// convenience booleans - the victory transition itself is a private field write buried inside
// tick() with nothing else clean to hook, so this diffs isVictory() against the previous tick
// instead and fires once on the false->true edge.
@Mixin(Raid.class)
public abstract class RaidTickMixin {

    @Unique
    private boolean qe_api$wasVictory = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void qe_api$onRaidTick(CallbackInfo ci) {
        Raid self = (Raid) (Object) this;

        boolean isVictory = self.isVictory();
        if (isVictory && !qe_api$wasVictory && self.getLevel() instanceof ServerLevel serverLevel) {
            QuestEventHandler.onRaidComplete(serverLevel, self.getCenter(), self.getRaidOmenLevel());
        }
        qe_api$wasVictory = isVictory;
    }
}
