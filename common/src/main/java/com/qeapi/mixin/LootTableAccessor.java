package com.qeapi.mixin;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// Lets ConditionalDropLootSupport append a pool to an already-built LootTable on NeoForge, where
// LootTableLoadEvent only ever hands out the raw built table (unlike Fabric's LootTableEvents.MODIFY,
// which hands out a still-open LootTable.Builder with the existing pools already merged in).
@Mixin(LootTable.class)
public interface LootTableAccessor {

    @Accessor("pools")
    List<LootPool> qe_api$getPools();

    @Accessor("pools")
    @Mutable
    void qe_api$setPools(List<LootPool> pools);
}
