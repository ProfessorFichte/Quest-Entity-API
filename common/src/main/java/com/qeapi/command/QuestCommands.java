package com.qeapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.qeapi.QuestEntityAPI;
import com.qeapi.api.QuestEntityAccess;
import com.qeapi.client.gui.QuestScreen;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.component.PlayerQuestData;
import com.qeapi.data.EntityQuestTag;
import com.qeapi.data.QuestManager;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestPool;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Test commands for the Quest Entity API.
public class QuestCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("qe_api")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give_tag")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .then(Commands.argument("tag", ResourceLocationArgument.id())
                                        .executes(QuestCommands::giveQuestTag))))
                .then(Commands.literal("open_gui")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(QuestCommands::openGui)))
                .then(Commands.literal("list_pools")
                        .executes(QuestCommands::listPools))
                .then(Commands.literal("reload")
                        .executes(QuestCommands::reloadQuests))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("entity_type", ResourceLocationArgument.id())
                                .then(Commands.argument("tag", ResourceLocationArgument.id())
                                        .executes(QuestCommands::spawnWithTag))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(ctx -> resetQuestProgress(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> resetQuestProgress(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("force_complete")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                        .executes(ctx -> forceCompleteQuest(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> forceCompleteQuest(ctx, EntityArgument.getPlayer(ctx, "player")))))))
        );
    }

    // Summons an entity of the given type at the command source's position and immediately gives
    // it a quest tag - give_tag still needs an existing entity, this is the "just let me test a
    // quest" one-step version.
    private static int spawnWithTag(CommandContext<CommandSourceStack> context) {
        try {
            ResourceLocation entityTypeId = ResourceLocationArgument.getId(context, "entity_type");
            ResourceLocation tagId = ResourceLocationArgument.getId(context, "tag");

            Optional<EntityType<?>> entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId);
            if (entityTypeOpt.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Unknown entity type: " + entityTypeId));
                return 0;
            }

            if (!QuestManager.hasEntityQuestTag(tagId)) {
                context.getSource().sendFailure(Component.literal("Quest tag not found: " + tagId));
                return 0;
            }

            ServerLevel level = context.getSource().getLevel();
            BlockPos pos = BlockPos.containing(context.getSource().getPosition());
            Entity entity = entityTypeOpt.get().spawn(level, pos, MobSpawnType.COMMAND);
            if (entity == null) {
                context.getSource().sendFailure(Component.literal("Failed to spawn entity: " + entityTypeId));
                return 0;
            }

            setEntityQuestComponent(entity, EntityQuestComponent.create(tagId));

            context.getSource().sendSuccess(() ->
                Component.literal("Spawned " + entityTypeId + " (id " + entity.getId() + ") with quest tag '" + tagId + "'"),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // Forgets everything a player has done with an entity's quests: any in-progress quest,
    // completed quests (so they can be re-offered and re-completed), and the accept/decline
    // cooldown - as if that player had never interacted with it.
    private static int resetQuestProgress(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        try {
            Entity entity = EntityArgument.getEntity(context, "entity");

            EntityQuestComponent component = getEntityQuestComponent(entity);
            if (component == null) {
                context.getSource().sendFailure(Component.literal("Entity has no quest data to reset."));
                return 0;
            }

            EntityQuestComponent updated = component
                    .withoutActiveQuest(player.getUUID())
                    .withoutCompletedQuests(player.getUUID())
                    .withoutChosenQuestGroup(player.getUUID())
                    .withoutCooldown(player.getUUID());
            setEntityQuestComponent(entity, updated);

            PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
            playerData.clearEntityProgress(entity.getUUID());
            QuestEntityAccess.setPlayerData(player, playerData);

            context.getSource().sendSuccess(() ->
                Component.literal("Reset quest progress for " + player.getName().getString() + " on entity " + entity.getId()),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // Grants a quest's rewards directly, bypassing task/requirement progress entirely - for
    // testing reward output without having to actually complete the quest. Reward choice pools
    // are left unpicked (nothing from them is granted) and target-item rewards (e.g.
    // enchant_specific, spell_bind) are skipped with a warning, since there's no GUI picker
    // context here to choose an item from - test those two through the normal accept+claim flow.
    private static int forceCompleteQuest(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        try {
            Entity entity = EntityArgument.getEntity(context, "entity");
            ResourceLocation questId = ResourceLocationArgument.getId(context, "quest_id");

            EntityQuestComponent component = getEntityQuestComponent(entity);
            if (component == null) {
                context.getSource().sendFailure(Component.literal("Entity has no quests."));
                return 0;
            }

            Quest quest = null;
            for (QuestPool pool : component.getAllQuestPools()) {
                quest = QuestEntityAccess.findQuestById(pool, questId);
                if (quest != null) break;
            }
            if (quest == null) {
                context.getSource().sendFailure(Component.literal("Quest not found on this entity: " + questId));
                return 0;
            }

            List<List<Integer>> emptyPoolChoices = quest.rewardChoicePools().stream()
                    .map(pool -> List.<Integer>of())
                    .toList();
            quest.grantRewards(player, entity, emptyPoolChoices);
            com.qeapi.event.QuestEventHandler.grantVillagerTradeXp(entity, quest.tier());

            PlayerQuestData playerData = QuestEntityAccess.getPlayerData(player);
            playerData.clearEntityProgress(entity.getUUID());
            QuestEntityAccess.setPlayerData(player, playerData);

            // Re-fetch rather than reusing the pre-grant `component` - an EntityAwareReward may
            // have already written its own update onto the entity during grantRewards above.
            EntityQuestComponent postGrantComponent = getEntityQuestComponent(entity);
            if (postGrantComponent == null) {
                postGrantComponent = component;
            }
            EntityQuestComponent updated = postGrantComponent.withCompletedQuest(player.getUUID(), questId,
                    player.serverLevel().getDayTime());
            setEntityQuestComponent(entity, updated);
            QuestEntityAccess.resolveQuestLineIfNeeded(player, entity, updated, quest);
            QuestEntityAccess.forceResyncNearbyPlayers(entity);

            Quest finalQuest = quest;
            context.getSource().sendSuccess(() ->
                Component.literal("Force-completed '" + finalQuest.id() + "' for " + player.getName().getString()
                        + " - reward_choice_pools and target-item rewards were skipped, see other rewards granted above"),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            QuestEntityAPI.LOGGER.error("Error force-completing quest", e);
            return 0;
        }
    }

    private static int giveQuestTag(CommandContext<CommandSourceStack> context) {
        try {
            Entity entity = EntityArgument.getEntity(context, "entity");
            ResourceLocation tagId = ResourceLocationArgument.getId(context, "tag");

            if (!QuestManager.hasEntityQuestTag(tagId)) {
                context.getSource().sendFailure(Component.literal("Quest tag not found: " + tagId));
                var availableTags = QuestManager.getAllEntityQuestTags();
                if (availableTags.isEmpty()) {
                    context.getSource().sendFailure(Component.literal("No tags loaded! Check data/[namespace]/tags/entity_quests/ files."));
                } else {
                    context.getSource().sendFailure(Component.literal("Available tags: " +
                            availableTags.stream().map(t -> t.getId().toString()).reduce((a, b) -> a + ", " + b).orElse("none")));
                }
                return 0;
            }

            EntityQuestComponent component = EntityQuestComponent.create(tagId);
            setEntityQuestComponent(entity, component);

            context.getSource().sendSuccess(() ->
                Component.literal("Gave quest tag '" + tagId + "' to entity " + entity.getName().getString()),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int openGui(CommandContext<CommandSourceStack> context) {
        try {
            Entity entity = EntityArgument.getEntity(context, "entity");
            ServerPlayer player = context.getSource().getPlayerOrException();

            EntityQuestComponent component = getEntityQuestComponent(entity);
            if (component == null) {
                context.getSource().sendFailure(Component.literal("Entity has no quests. Use /qe_api give_tag first."));
                return 0;
            }

            // supports tags, not just single pools
            List<QuestPool> allPools = component.getAllQuestPools();
            if (allPools.isEmpty()) {
                context.getSource().sendFailure(Component.literal("No quest pools found for: " + component.questPoolId()));
                return 0;
            }

            List<Quest> availableQuests = new ArrayList<>();
            for (QuestPool pool : allPools) {
                availableQuests.addAll(pool.getAllQuests());
            }

            openQuestGui(player, entity.getId(), component, availableQuests);

            context.getSource().sendSuccess(() ->
                Component.literal("Opening quest GUI with " + availableQuests.size() + " quests from " + allPools.size() + " pool(s)."),
                false
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            QuestEntityAPI.LOGGER.error("Error opening quest GUI", e);
            return 0;
        }
    }

    // Called from platform-specific code; on Fabric this goes through FabricNetworking.sendOpenQuestMenu.
    public static void openQuestGui(ServerPlayer player, int entityId,
                                     EntityQuestComponent component, List<Quest> quests) {
        QuestGuiOpener.open(player, entityId, component, quests);
    }

    public interface QuestGuiOpenerImpl {
        void open(ServerPlayer player, int entityId, EntityQuestComponent component, List<Quest> quests);
    }

    public static class QuestGuiOpener {
        private static QuestGuiOpenerImpl impl = (player, entityId, component, quests) -> {
            QuestEntityAPI.LOGGER.warn("Quest GUI opener not initialized!");
        };

        public static void setImpl(QuestGuiOpenerImpl implementation) {
            impl = implementation;
        }

        public static void open(ServerPlayer player, int entityId,
                                EntityQuestComponent component, List<Quest> quests) {
            impl.open(player, entityId, component, quests);
        }
    }

    private static int listPools(CommandContext<CommandSourceStack> context) {
        var quests = QuestManager.getAllQuests();
        var tags = QuestManager.getAllEntityQuestTags();

        if (quests.isEmpty() && tags.isEmpty()) {
            context.getSource().sendSuccess(() ->
                Component.literal("No quests or tags loaded. Check data/[namespace]/entity_quest/ and data/[namespace]/tags/entity_quests/ files."),
                false
            );
            return 0;
        }

        context.getSource().sendSuccess(() ->
            Component.literal("Loaded quests (" + quests.size() + "):"),
            false
        );

        // a tag's referenced quests, grouped by tier, form its pool
        if (!tags.isEmpty()) {
            context.getSource().sendSuccess(() ->
                Component.literal("Loaded entity quest tags (" + tags.size() + "):"),
                false
            );

            for (var tag : tags) {
                int questCount = tag.getQuestIds().size();
                int tagRefCount = tag.getReferencedTags().size();
                context.getSource().sendSuccess(() ->
                    Component.literal("  - #" + tag.getId() + " (" + questCount + " quests, " + tagRefCount + " tag refs)"),
                    false
                );
            }
        }

        return 1;
    }

    private static int reloadQuests(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() ->
            Component.literal("Use /reload to reload quest data."),
            false
        );
        return 1;
    }

    public static void setEntityQuestComponent(Entity entity, EntityQuestComponent component) {
        QuestEntityAccess.setEntityQuestComponent(entity, component);
    }

    public static EntityQuestComponent getEntityQuestComponent(Entity entity) {
        return QuestEntityAccess.getEntityQuestComponent(entity);
    }
}
