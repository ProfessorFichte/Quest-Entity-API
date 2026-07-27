package com.qeapi.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Exposes Villager's private level-up logic so quest completion can grant trading XP that
// actually levels up the villager's profession immediately, instead of just incrementing the XP
// counter. Vanilla only runs this from the protected, MerchantOffer-driven rewardTradeXp()
// (deferred by a 40-tick timer after a real trade) - not usable directly.
@Mixin(Villager.class)
public interface VillagerXpAccessor {

    @Invoker("shouldIncreaseLevel")
    boolean qe_api$shouldIncreaseLevel();

    @Invoker("increaseMerchantCareer")
    void qe_api$increaseMerchantCareer();
}
