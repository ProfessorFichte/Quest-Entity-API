package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

// Reward that executes a command on completion. Supports {player}, {uuid}, {x}/{y}/{z} placeholders.
public record CommandReward(
        String command,
        Optional<String> displayName
) implements QuestReward {

    public static final MapCodec<CommandReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("command").forGetter(CommandReward::command),
                    Codec.STRING.optionalFieldOf("display_name").forGetter(CommandReward::displayName)
            ).apply(instance, CommandReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("command");
    }

    @Override
    public void grant(ServerPlayer player) {
        String processedCommand = command
                .replace("{player}", player.getName().getString())
                .replace("{uuid}", player.getUUID().toString())
                .replace("{x}", String.valueOf((int) player.getX()))
                .replace("{y}", String.valueOf((int) player.getY()))
                .replace("{z}", String.valueOf((int) player.getZ()));

        // level 2 permission, same as command blocks
        try {
            CommandSourceStack source = player.getServer().createCommandSourceStack()
                    .withPermission(2)
                    .withSuppressedOutput();

            player.getServer().getCommands().performPrefixedCommand(source, processedCommand);

            QuestEntityAPI.LOGGER.debug("Executed command reward: {} for player {}",
                    processedCommand, player.getName().getString());
        } catch (Exception e) {
            QuestEntityAPI.LOGGER.error("Failed to execute command reward: {} - {}",
                    processedCommand, e.getMessage());
        }
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.command",
                displayName.orElse("Command Reward"));
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.of(new ItemStack(Items.COMMAND_BLOCK));
    }

    public static CommandReward of(String command) {
        return new CommandReward(command, Optional.empty());
    }

    public static CommandReward of(String command, String displayName) {
        return new CommandReward(command, Optional.of(displayName));
    }
}
