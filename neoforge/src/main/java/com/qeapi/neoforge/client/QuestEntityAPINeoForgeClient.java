package com.qeapi.neoforge.client;

import com.qeapi.QuestEntityAPI;
import com.qeapi.client.ClientQuestCache;
import com.qeapi.client.QuestKeybinds;
import com.qeapi.network.ClientPacketSender;
import com.qeapi.network.packet.AcceptQuestPacket;
import com.qeapi.network.packet.ActiveQuestsPacket;
import com.qeapi.network.packet.CancelQuestLinePacket;
import com.qeapi.network.packet.ChooseQuestLinePacket;
import com.qeapi.network.packet.ClaimQuestLineRootPacket;
import com.qeapi.network.packet.ClaimRewardsPacket;
import com.qeapi.network.packet.DismissQuestLineRootPacket;
import com.qeapi.network.packet.DismissQuestPacket;
import com.qeapi.network.packet.OpenQuestMenuPacket;
import com.qeapi.network.packet.QuestProgressPacket;
import com.qeapi.network.packet.RequestActiveQuestsPacket;
import com.qeapi.network.packet.RequestMerchantMenuPacket;
import com.qeapi.network.packet.RequestQuestMenuPacket;
import com.qeapi.network.packet.SyncEntityQuestsPacket;
import com.qeapi.network.packet.SyncDeliveryTargetPacket;
import com.qeapi.config.QuestEntityAPIConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// Mirrors fabric.client.QuestEntityAPIFabricClient. Only ever constructed on the client
// physical side (@Mod(dist = Dist.CLIENT)), so this class and everything it references
// can safely touch Minecraft client classes without crashing a dedicated server.
@Mod(value = QuestEntityAPI.MOD_ID, dist = Dist.CLIENT)
public final class QuestEntityAPINeoForgeClient {

    public QuestEntityAPINeoForgeClient(IEventBus modEventBus) {
        QuestEntityAPI.LOGGER.info("Initializing NeoForge client for Quest Entity API");

        ClientPacketSender.setInstance(new ClientPacketSender.PacketSender() {
            @Override
            public void sendAcceptQuest(int entityId, net.minecraft.resources.ResourceLocation questId) {
                PacketDistributor.sendToServer(new AcceptQuestPacket(entityId, questId));
            }

            @Override
            public void sendDismissQuest(int entityId) {
                PacketDistributor.sendToServer(new DismissQuestPacket(entityId));
            }

            @Override
            public void sendClaimRewards(int entityId, java.util.List<java.util.List<Integer>> poolChoices,
                                          java.util.List<Integer> rewardTargetSlots, java.util.List<java.util.List<Integer>> bringItemSlots) {
                PacketDistributor.sendToServer(new ClaimRewardsPacket(entityId, poolChoices, rewardTargetSlots, bringItemSlots));
            }

            @Override
            public void sendRequestQuestMenu(int entityId) {
                PacketDistributor.sendToServer(new RequestQuestMenuPacket(entityId));
            }

            @Override
            public void sendRequestMerchantMenu(int entityId) {
                PacketDistributor.sendToServer(new RequestMerchantMenuPacket(entityId));
            }

            @Override
            public void sendRequestActiveQuests() {
                PacketDistributor.sendToServer(new RequestActiveQuestsPacket());
            }

            @Override
            public void sendChooseQuestLine(int entityId, net.minecraft.resources.ResourceLocation rootQuestId, String lineId) {
                PacketDistributor.sendToServer(new ChooseQuestLinePacket(entityId, rootQuestId, lineId));
            }

            @Override
            public void sendClaimQuestLineRoot(int entityId, net.minecraft.resources.ResourceLocation rootQuestId) {
                PacketDistributor.sendToServer(new ClaimQuestLineRootPacket(entityId, rootQuestId));
            }

            @Override
            public void sendCancelQuestLine(int entityId, net.minecraft.resources.ResourceLocation rootQuestId, String lineId) {
                PacketDistributor.sendToServer(new CancelQuestLinePacket(entityId, rootQuestId, lineId));
            }

            @Override
            public void sendDismissQuestLineRoot(int entityId, net.minecraft.resources.ResourceLocation rootQuestId) {
                PacketDistributor.sendToServer(new DismissQuestLineRootPacket(entityId, rootQuestId));
            }
        });

        modEventBus.addListener(this::registerPayloadHandlers);
        modEventBus.addListener(this::registerKeyMappings);

        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                () -> (modContainer, parent) -> AutoConfig.getConfigScreen(QuestEntityAPIConfig.class, parent).get());

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            QuestEntityAPI.LOGGER.debug("Clearing client quest cache on disconnect");
            ClientQuestCache.clear();
        });

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> QuestKeybinds.tick());
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(QuestKeybinds.OPEN_ACTIVE_QUESTS);
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        QuestEntityAPI.LOGGER.info("Registering client-side Quest Entity API packets");

        var registrar = event.registrar("1");

        registrar.playToClient(OpenQuestMenuPacket.TYPE, OpenQuestMenuPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleOpenQuestMenu(packet)));

        registrar.playToClient(QuestProgressPacket.TYPE, QuestProgressPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleQuestProgress(packet)));

        registrar.playToClient(SyncEntityQuestsPacket.TYPE, SyncEntityQuestsPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleSyncEntityQuests(packet)));

        registrar.playToClient(SyncDeliveryTargetPacket.TYPE, SyncDeliveryTargetPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleSyncDeliveryTarget(packet)));

        registrar.playToClient(ActiveQuestsPacket.TYPE, ActiveQuestsPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleActiveQuests(packet)));
    }

    // ==================== Client Handlers ====================

    private static void handleOpenQuestMenu(OpenQuestMenuPacket packet) {
        QuestEntityAPI.LOGGER.debug("Opening quest menu for entity {}", packet.entityId());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        net.minecraft.world.entity.Entity entity = minecraft.level.getEntity(packet.entityId());
        if (entity != null) {
            ClientQuestCache.markEntityHasQuests(
                    entity.getUUID(),
                    packet.entityId(),
                    packet.questComponent()
            );
        }

        minecraft.setScreen(new com.qeapi.client.gui.QuestScreen(
                packet.entityId(),
                packet.availableQuests(),
                packet.questComponent(),
                minecraft.player.getUUID(),
                packet.activeLine(),
                packet.resolvedLines(),
                packet.claimedRoots(),
                packet.acceptedRoots()
        ));
    }

    private static void handleQuestProgress(QuestProgressPacket packet) {
        QuestEntityAPI.LOGGER.debug("Received quest progress update for entity {}", packet.entityId());
        // Update is handled by refreshing the GUI when needed
    }

    private static void handleSyncEntityQuests(SyncEntityQuestsPacket packet) {
        QuestEntityAPI.LOGGER.debug("Received entity quest sync for entity {} (UUID: {}), active={}, complete={}",
                packet.entityId(), packet.entityUuid(), packet.hasActiveQuest(), packet.isQuestComplete());

        ClientQuestCache.markEntityHasQuestsSimple(
                packet.entityUuid(),
                packet.entityId(),
                packet.hasActiveQuest(),
                packet.isQuestComplete(),
                packet.allQuestsCompleted(),
                packet.enraged()
        );
    }

    private static void handleActiveQuests(ActiveQuestsPacket packet) {
        Minecraft.getInstance().setScreen(new com.qeapi.client.gui.ActiveQuestScreen(packet.entries()));
    }

    private static void handleSyncDeliveryTarget(SyncDeliveryTargetPacket packet) {
        if (!packet.active()) {
            ClientQuestCache.clearDeliveryTarget(packet.entityUuid());
            return;
        }

        net.minecraft.world.item.ItemStack stack;
        if (packet.questItem().isPresent()) {
            stack = packet.questItem().get().createStack(1);
        } else if (packet.itemId().isPresent()) {
            stack = new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(packet.itemId().get()));
        } else {
            stack = net.minecraft.world.item.ItemStack.EMPTY;
        }

        ClientQuestCache.setDeliveryTarget(packet.entityUuid(), stack);
    }
}
