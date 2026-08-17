package com.qeapi.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Exposes Villager's private level-up logic so quest completion can grant trading XP that actually
// levels up the villager immediately, instead of just incrementing the XP counter - vanilla only
// runs this from the protected, MerchantOffer-driven rewardTradeXp(), deferred 40 ticks after a
// real trade.
@Mixin(Villager.class)
public interface VillagerXpAccessor {

    @Invoker("shouldIncreaseLevel")
    boolean quest_api$shouldIncreaseLevel();

    @Invoker("increaseMerchantCareer")
    void quest_api$increaseMerchantCareer();
}
