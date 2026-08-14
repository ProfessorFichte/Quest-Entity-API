package com.qeapi.client;

import com.qeapi.component.EntityQuestComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Client-side cache for quest entity data; used for rendering quest markers and tracking local state.
public final class ClientQuestCache {

    // Entity UUID -> has quests (simplified for rendering)
    private static final Set<UUID> ENTITIES_WITH_QUESTS = ConcurrentHashMap.newKeySet();

    // Entity UUID -> has active quest with local player
    private static final Set<UUID> ENTITIES_WITH_ACTIVE_QUEST = ConcurrentHashMap.newKeySet();

    // Entity UUID -> quest is complete and ready to claim
    private static final Set<UUID> ENTITIES_WITH_COMPLETE_QUEST = ConcurrentHashMap.newKeySet();

    // Entity UUID -> every quest this entity offers has been completed (nothing left to accept)
    private static final Set<UUID> ENTITIES_ALL_QUESTS_COMPLETE = ConcurrentHashMap.newKeySet();

    // Entity UUID -> refuses to interact right now (on hit cooldown for the local player)
    private static final Set<UUID> ENTITIES_ENRAGED = ConcurrentHashMap.newKeySet();

    // Entity ID (network) -> Quest component (for current session)
    private static final Map<Integer, EntityQuestComponent> ENTITY_COMPONENTS = new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> UUID_TO_ID = new ConcurrentHashMap<>();

    private ClientQuestCache() {}

    // called when packet received
    public static void markEntityHasQuests(UUID entityUuid, int entityId, EntityQuestComponent component) {
        ENTITIES_WITH_QUESTS.add(entityUuid);
        ENTITY_COMPONENTS.put(entityId, component);
        UUID_TO_ID.put(entityUuid, entityId);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && component.hasActiveQuest(mc.player.getUUID())) {
            ENTITIES_WITH_ACTIVE_QUEST.add(entityUuid);
        } else {
            ENTITIES_WITH_ACTIVE_QUEST.remove(entityUuid);
        }
    }

    // simplified variant for sync packets, when we only have basic quest state, not the full component
    public static void markEntityHasQuestsSimple(UUID entityUuid, int entityId, boolean hasActiveQuest, boolean isQuestComplete,
                                                  boolean allQuestsCompleted, boolean enraged) {
        ENTITIES_WITH_QUESTS.add(entityUuid);
        UUID_TO_ID.put(entityUuid, entityId);

        if (hasActiveQuest) {
            ENTITIES_WITH_ACTIVE_QUEST.add(entityUuid);
        } else {
            ENTITIES_WITH_ACTIVE_QUEST.remove(entityUuid);
        }

        if (isQuestComplete) {
            ENTITIES_WITH_COMPLETE_QUEST.add(entityUuid);
        } else {
            ENTITIES_WITH_COMPLETE_QUEST.remove(entityUuid);
        }

        if (allQuestsCompleted) {
            ENTITIES_ALL_QUESTS_COMPLETE.add(entityUuid);
        } else {
            ENTITIES_ALL_QUESTS_COMPLETE.remove(entityUuid);
        }

        if (enraged) {
            ENTITIES_ENRAGED.add(entityUuid);
        } else {
            ENTITIES_ENRAGED.remove(entityUuid);
        }
    }

    public static boolean hasQuests(UUID entityUuid) {
        return ENTITIES_WITH_QUESTS.contains(entityUuid);
    }

    public static boolean hasActiveQuest(UUID entityUuid) {
        return ENTITIES_WITH_ACTIVE_QUEST.contains(entityUuid);
    }

    public static boolean isQuestReadyToClaim(UUID entityUuid) {
        return ENTITIES_WITH_COMPLETE_QUEST.contains(entityUuid);
    }

    public static boolean allQuestsCompleted(UUID entityUuid) {
        return ENTITIES_ALL_QUESTS_COMPLETE.contains(entityUuid);
    }

    public static boolean isEnraged(UUID entityUuid) {
        return ENTITIES_ENRAGED.contains(entityUuid);
    }

    public static void setActiveQuest(UUID entityUuid, boolean active) {
        if (active) {
            ENTITIES_WITH_ACTIVE_QUEST.add(entityUuid);
        } else {
            ENTITIES_WITH_ACTIVE_QUEST.remove(entityUuid);
        }
    }

    public static EntityQuestComponent getComponent(int entityId) {
        return ENTITY_COMPONENTS.get(entityId);
    }

    public static Integer getEntityId(UUID entityUuid) {
        return UUID_TO_ID.get(entityUuid);
    }

    // called on disconnect
    public static void clear() {
        ENTITIES_WITH_QUESTS.clear();
        ENTITIES_WITH_ACTIVE_QUEST.clear();
        ENTITIES_WITH_COMPLETE_QUEST.clear();
        ENTITIES_ALL_QUESTS_COMPLETE.clear();
        ENTITIES_ENRAGED.clear();
        ENTITY_COMPONENTS.clear();
        UUID_TO_ID.clear();
        DELIVERY_TARGET_ITEMS.clear();
    }

    // called when entity is removed from the world
    public static void removeEntity(UUID entityUuid, int entityId) {
        ENTITIES_WITH_QUESTS.remove(entityUuid);
        ENTITIES_WITH_ACTIVE_QUEST.remove(entityUuid);
        ENTITIES_WITH_COMPLETE_QUEST.remove(entityUuid);
        ENTITIES_ALL_QUESTS_COMPLETE.remove(entityUuid);
        ENTITIES_ENRAGED.remove(entityUuid);
        ENTITY_COMPONENTS.remove(entityId);
        UUID_TO_ID.remove(entityUuid);
        DELIVERY_TARGET_ITEMS.remove(entityUuid);
    }

    // ==================== Delivery Target Marker (deliver_item) ====================

    // Entity UUID -> the item this entity wants, for the floating item marker - see
    // QuestMarkerRenderer.renderDeliveryItem and SyncDeliveryTargetPacket
    private static final Map<UUID, ItemStack> DELIVERY_TARGET_ITEMS = new ConcurrentHashMap<>();

    public static void setDeliveryTarget(UUID entityUuid, ItemStack item) {
        DELIVERY_TARGET_ITEMS.put(entityUuid, item);
    }

    public static void clearDeliveryTarget(UUID entityUuid) {
        DELIVERY_TARGET_ITEMS.remove(entityUuid);
    }

    // null if this entity isn't (currently known to be) a delivery target
    public static ItemStack getDeliveryTargetItem(UUID entityUuid) {
        return DELIVERY_TARGET_ITEMS.get(entityUuid);
    }

    // ==================== Scroll Position Caching ====================

    // Entity ID -> scroll positions (preserved when screen is refreshed)
    private static final Map<Integer, ScrollPositions> SCROLL_POSITIONS = new ConcurrentHashMap<>();

    public record ScrollPositions(int questScrollOffset, int infoScrollOffset, int selectedQuestIndex) {}

    public static void saveScrollPositions(int entityId, int questScroll, int infoScroll, int selectedIndex) {
        SCROLL_POSITIONS.put(entityId, new ScrollPositions(questScroll, infoScroll, selectedIndex));
    }

    public static ScrollPositions getScrollPositions(int entityId) {
        return SCROLL_POSITIONS.get(entityId);
    }

    public static void clearScrollPositions(int entityId) {
        SCROLL_POSITIONS.remove(entityId);
    }

    // ==================== Merchant Tracking ====================

    // Last merchant entity ID that was interacted with (for merchant screen integration)
    private static int lastMerchantEntityId = -1;
    private static UUID lastMerchantEntityUuid = null;
    // Track if quest screen was opened from merchant (for back button)
    private static boolean openedFromMerchant = false;

    // called when player interacts with a villager/wandering trader
    public static void setLastMerchantEntity(int entityId, UUID entityUuid) {
        lastMerchantEntityId = entityId;
        lastMerchantEntityUuid = entityUuid;
    }

    public static int getLastMerchantEntityId() {
        return lastMerchantEntityId;
    }

    public static UUID getLastMerchantEntityUuid() {
        return lastMerchantEntityUuid;
    }

    public static void clearLastMerchantEntity() {
        lastMerchantEntityId = -1;
        lastMerchantEntityUuid = null;
        openedFromMerchant = false;
    }

    public static void setOpenedFromMerchant(boolean value) {
        openedFromMerchant = value;
    }

    public static boolean wasOpenedFromMerchant() {
        return openedFromMerchant;
    }
}
