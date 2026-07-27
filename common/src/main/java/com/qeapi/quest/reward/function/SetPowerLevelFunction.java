package com.qeapi.quest.reward.function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.DungeonDifficultyCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// applies Dungeon Difficulty's power level scaling; no-op with a warning if that mod isn't loaded
public record SetPowerLevelFunction(int level) implements ItemFunction {

    public static final MapCodec<SetPowerLevelFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("level").forGetter(SetPowerLevelFunction::level)
            ).apply(instance, SetPowerLevelFunction::new)
    );

    @Override
    public ItemStack apply(ItemStack stack, ServerPlayer player) {
        if (!DungeonDifficultyCompat.isLoaded()) {
            QuestEntityAPI.LOGGER.warn("[SetPowerLevelFunction] Dungeon Difficulty isn't loaded - skipping power level {}", level);
            return stack;
        }
        DungeonDifficultyCompat.applyPowerLevel(stack, level);
        return stack;
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("set_power_level");
    }

    @Override
    public MapCodec<? extends ItemFunction> getCodec() {
        return CODEC;
    }
}
