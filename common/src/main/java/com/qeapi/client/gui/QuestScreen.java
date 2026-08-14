package com.qeapi.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qeapi.QuestEntityAPI;
import com.qeapi.client.ClientQuestCache;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.config.QuestEntityAPIConfig;
import com.qeapi.network.ClientPacketSender;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestProgress;
import com.qeapi.quest.requirement.HasItemRequirement;
import com.qeapi.quest.requirement.QuestRequirement;
import com.qeapi.quest.reward.EnchantRandomlyReward;
import com.qeapi.quest.reward.EnchantSpecificReward;
import com.qeapi.quest.reward.EnhanceItemReward;
import com.qeapi.quest.reward.EnhanceOperation;
import com.qeapi.quest.reward.ExperienceReward;
import com.qeapi.quest.reward.IncreaseEnchantSlotsReward;
import com.qeapi.quest.reward.IncreasePowerLevelReward;
import com.qeapi.quest.reward.RepairItemReward;
import com.qeapi.compat.LevelZCompat;
import com.qeapi.quest.reward.LevelZSkillLevelReward;
import com.qeapi.quest.reward.LootTableReward;
import com.qeapi.quest.reward.QuestReward;
import com.qeapi.quest.reward.RewardChoicePool;
import com.qeapi.quest.reward.SetQuestGroupReward;
import com.qeapi.quest.reward.SkillExperienceReward;
import com.qeapi.quest.reward.SkillLevelReward;
import com.qeapi.quest.reward.SpellBindReward;
import com.qeapi.quest.reward.SpellScrollReward;
import com.qeapi.quest.reward.StatusEffectReward;
import com.qeapi.quest.reward.TargetItemReward;
import com.qeapi.quest.reward.function.SetPowerLevelFunction;
import com.qeapi.quest.reward.ItemReward;
import com.qeapi.compat.DungeonDifficultyCompat;
import com.qeapi.compat.ModCompatUtil;
import com.qeapi.client.compat.SpellEngineClientCompat;
import com.qeapi.quest.task.ApplyStatusEffectTask;
import com.qeapi.quest.task.BlocksTraveledTask;
import com.qeapi.quest.task.BrewPotionTask;
import com.qeapi.quest.task.BringItemTask;
import com.qeapi.quest.task.ConditionalDropTask;
import com.qeapi.quest.task.MineBlockTask;
import com.qeapi.quest.task.SpellCastTask;
import com.qeapi.quest.task.EntityKillTask;
import com.qeapi.quest.task.FindStructureTask;
import com.qeapi.quest.task.ItemUsedTask;
import com.qeapi.quest.task.QuestTask;
import com.qeapi.quest.task.FishingTask;
import com.qeapi.quest.task.HarvestCropsTask;
import com.qeapi.quest.task.AnvilTask;
import com.qeapi.quest.task.SmithingTask;
import com.qeapi.quest.task.CraftingTask;
import com.qeapi.quest.task.EnchantingTask;
import com.qeapi.quest.task.SpellBindTask;
import com.qeapi.quest.task.SpellPoolCompleteTask;
import com.qeapi.quest.task.RaidCompleteTask;
import com.qeapi.quest.task.TrialSpawnerCompleteTask;
import com.qeapi.quest.task.DeliverItemTask;
import com.qeapi.quest.task.QuestLineChoiceTask;
import net.minecraft.world.item.Items;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class QuestScreen extends Screen {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            QuestEntityAPI.MOD_ID, "textures/gui/quests.png");

    // width is configurable, rest is fixed to the texture
    private static final int DEFAULT_GUI_WIDTH = 240;
    private static final int GUI_HEIGHT = 222;
    private static final int TEXTURE_SIZE = 256;

    private final int guiWidth;

    private static final int QUEST_LIST_X = 8;
    private static final int QUEST_LIST_Y = 15;
    private static final int CHECKBOX_SIZE = 19;
    private static final int DEFAULT_QUEST_BOX_WIDTH = 190;
    private static final int QUEST_BOX_HEIGHT = 19;
    private static final int VISIBLE_QUESTS = 3;

    // quest list scrollbar, position relative to right edge - track sits at (219, 14), 14x59 on the 240x222 texture
    private static final int SCROLLBAR_RIGHT_OFFSET = 21; // GUI_WIDTH - QUEST_SCROLLBAR_X
    private static final int QUEST_SCROLLBAR_Y = 14;
    private static final int QUEST_SCROLLBAR_WIDTH = 14;
    private static final int QUEST_SCROLLBAR_HEIGHT = 59;

    private static final int SCROLLBAR_THUMB_U = 244;
    private static final int SCROLLBAR_THUMB_V = 0;
    private static final int SCROLLBAR_THUMB_WIDTH = 12;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;

    // standalone 16x16 checkmarks, centered in the 19x19 checkbox slot instead of stretched, so the pixel art stays crisp
    private static final ResourceLocation WHITE_CHECKMARK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            QuestEntityAPI.MOD_ID, "textures/gui/marker/white_checkmark.png");
    private static final ResourceLocation GREEN_CHECKMARK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            QuestEntityAPI.MOD_ID, "textures/gui/marker/green_checkmark.png");
    private static final ResourceLocation QUEST_LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            QuestEntityAPI.MOD_ID, "textures/gui/marker/quest_locked.png");
    private static final int CHECK_SIZE = 19;
    private static final int CHECKMARK_SOURCE_SIZE = 16;
    private static final int CHECKMARK_INSET = (CHECK_SIZE - CHECKMARK_SOURCE_SIZE) / 2;

    // size of the green checkmark when overlaid as a "completed" badge (see renderCompletionBadge)
    private static final int COMPLETION_BADGE_SIZE = 8;

    // Dungeon Difficulty's power-level symbol, only rendered when that mod is loaded - see renderReward's SetPowerLevelFunction check
    private static final ResourceLocation POWER_LEVEL_ICON = ResourceLocation.fromNamespaceAndPath(
            "dungeon_difficulty", "textures/symbol/power_level.png");

    // 22x22 border with a transparent 16x16 center, offset -3,-3 so it lines up over a 16x16 icon.
    // Used for reward-choice-pool selection and the item-picker overlay's chosen/hovered slot.
    private static final ResourceLocation SELECTION_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            QuestEntityAPI.MOD_ID, "textures/gui/selection.png");
    private static final int SELECTION_SIZE = 22;
    private static final int SELECTION_INSET = (SELECTION_SIZE - 16) / 2;

    // selection.png's border is wider than the 16x16 icon it wraps, so a BringItemTask/TargetItemReward
    // icon shifts right by this much to clear the info box's left edge instead of clipping under the
    // scissor rect. Text shifts further to clear the border's own overhang - calculateInfoContentHeight
    // has the matching wrap-width math.
    private static final int SELECTION_ICON_X_OFFSET = 5;
    private static final int SELECTION_TEXT_X_OFFSET = 10;

    // shown in a TargetItemReward's slot before an item is picked - same as vanilla's empty loom/slot convention
    private static final ItemStack PICKER_PLACEHOLDER_STACK = new ItemStack(Items.BARRIER);

    // item-picker overlay background, 210x109, drawn over the info box at INFO_BOX_X/Y. Back arrow and
    // grid frame are baked into the art; only the strings and item icons/borders get drawn on top.
    private static final ResourceLocation ITEM_SELECTION_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            QuestEntityAPI.MOD_ID, "textures/gui/item_selection.png");
    private static final int ITEM_SELECTION_WIDTH = 210;
    private static final int ITEM_SELECTION_HEIGHT = 109;

    private static final int PICKER_TITLE_X = 13;
    private static final int PICKER_BACK_ARROW_X = 160;
    private static final int PICKER_BACK_ARROW_Y = 1;
    private static final int PICKER_BACK_ARROW_WIDTH = 22;
    private static final int PICKER_BACK_ARROW_HEIGHT = 16;
    private static final int PICKER_BACK_TEXT_X = PICKER_BACK_ARROW_X + PICKER_BACK_ARROW_WIDTH + 2;
    private static final int PICKER_BACK_TEXT_Y = PICKER_BACK_ARROW_Y + 4;
    private static final int PICKER_TITLE_Y = PICKER_BACK_TEXT_Y;

    private static final int PICKER_GRID_FRAME_X = 15;
    private static final int PICKER_GRID_FRAME_Y = 18;
    private static final int PICKER_GRID_FRAME_WIDTH = 180;
    private static final int PICKER_GRID_FRAME_HEIGHT = 90;
    private static final int PICKER_GRID_ICON_X = PICKER_GRID_FRAME_X + 1;
    private static final int PICKER_GRID_ICON_Y = PICKER_GRID_FRAME_Y + 1;
    private static final int PICKER_GRID_CELL_SIZE = 18;
    private static final int PICKER_GRID_COLUMNS = 10;

    // widths below get recalculated dynamically from the actual GUI width
    private static final int INFO_BOX_X = 8;
    private static final int INFO_BOX_Y = 82;
    private static final int DEFAULT_INFO_BOX_WIDTH = 210;
    private static final int INFO_BOX_HEIGHT = 109;

    // info scrollbar, position relative to right edge - track at (219, 81), 14x110, shares SCROLLBAR_RIGHT_OFFSET with the quest scrollbar
    private static final int INFO_SCROLLBAR_Y = 81;
    private static final int INFO_SCROLLBAR_WIDTH = 14;
    private static final int INFO_SCROLLBAR_HEIGHT = 110;

    // claim button - (43, 196), 154x19 on the texture
    private static final int CLAIM_BUTTON_X = 43;
    private static final int CLAIM_BUTTON_Y = 196;
    private static final int CLAIM_BUTTON_WIDTH = 154;
    private static final int CLAIM_BUTTON_HEIGHT = 19;

    // trade button, shown when opened from a merchant - top right corner, relative to right edge
    private static final int TRADE_BUTTON_RIGHT_OFFSET = 19; // GUI_WIDTH - TRADE_BUTTON_X
    private static final int TRADE_BUTTON_Y = 2;
    private static final int TRADE_BUTTON_SIZE = 12;

    // confirm-dismiss overlay, shown before a dismiss checkbox click actually fires - same
    // bundled-but-unsupplied-texture convention as every other DEFAULT_TEXTURE in this mod (see
    // e.g. FishingTask.DEFAULT_TEXTURE); the PNG itself isn't included, only the reference to it
    private static final ResourceLocation CONFIRM_DIALOG_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            QuestEntityAPI.MOD_ID, "textures/gui/confirm_dialog.png");
    private static final int CONFIRM_DIALOG_ATLAS_SIZE = 256;
    private static final int CONFIRM_DIALOG_WIDTH = 130;
    private static final int CONFIRM_DIALOG_HEIGHT = 60;
    private static final int CONFIRM_DIALOG_TEXT_BOX_X = 5;
    private static final int CONFIRM_DIALOG_TEXT_BOX_Y = 5;
    private static final int CONFIRM_DIALOG_TEXT_BOX_WIDTH = 120;
    private static final int CONFIRM_DIALOG_TEXT_BOX_HEIGHT = 27;
    private static final int CONFIRM_DIALOG_YES_X = 6;
    private static final int CONFIRM_DIALOG_NO_X = 74;
    private static final int CONFIRM_DIALOG_BUTTON_Y = 34;
    private static final int CONFIRM_DIALOG_BUTTON_WIDTH = 50;
    private static final int CONFIRM_DIALOG_BUTTON_HEIGHT = 19;
    private static final int CONFIRM_DIALOG_HOVER_U = 0;
    private static final int CONFIRM_DIALOG_HOVER_V = 60;

    private static final int SCROLL_DELAY_TICKS = 30;
    private static final int SCROLL_SPEED_TICKS = 3;
    private static final int SCROLL_PAUSE_TICKS = 40;

    private final int entityId;
    private final List<Quest> availableQuests;
    private final EntityQuestComponent questComponent;
    private final UUID playerId;
    // giver-scoped LineSelectionState fields (see PlayerQuestData) for whichever quest_line_choice
    // root(s) are in availableQuests - acceptedRoots gates the picker row becoming interactive at
    // all (mirrors the accept step every other quest requires), resolvedLines/claimedRoots gate the
    // Claim button ("fully resolved, not yet claimed"). None of this reads
    // questComponent.hasCompletedQuest for the root any more - that only flips once the root's
    // reward is actually claimed, same timing as every other quest, so a sibling root at a higher
    // tier's follow_quest_order gate isn't unlocked early.
    private final Optional<String> activeLine;
    private final Set<String> resolvedLines;
    private final Set<ResourceLocation> claimedRoots;
    private final Set<ResourceLocation> acceptedRoots;

    private int guiLeft;
    private int guiTop;

    private int questBoxWidth;
    private int questScrollbarX;
    private int infoBoxWidth;
    private int infoScrollbarX;
    private int tradeButtonX;

    private int questScrollOffset = 0;
    private boolean isDraggingQuestScrollbar = false;

    // infoScrollOffset is reused for the item-picker grid too (see renderItemPicker), since the two
    // are never shown at once. descriptionScrollOffset stashes the description's position across a
    // picker visit so coming back doesn't reset it to the top.
    private int infoScrollOffset = 0;
    private int descriptionScrollOffset = 0;
    private int maxInfoScroll = 0;
    private boolean isDraggingInfoScrollbar = false;

    private int selectedQuestIndex = -1;
    private Quest selectedQuest = null;
    private QuestProgress currentProgress;

    // session-lifetime (survives screen close/reopen, not client restart) - a quest's description
    // only ever animates the first time its detail pane is opened per client session
    private static final Set<ResourceLocation> shownDescriptions = new HashSet<>();
    private ResourceLocation typewriterQuestId = null;
    private long typewriterStartMs = 0;

    private int textScrollTicks = 0;
    private int textScrollOffset = 0;
    private boolean textScrollPaused = false;
    private int lastSelectedIndex = -1;

    private final List<HoverArea> hoverAreas = new ArrayList<>();

    private final Map<Integer, List<Integer>> selectedPoolChoices = new HashMap<>();
    private final List<ChoiceArea> choiceAreas = new ArrayList<>();
    private final List<LineOptionArea> lineOptionAreas = new ArrayList<>();

    // which TargetItemReward (index into selectedQuest.rewards()) or ambiguous BringItemTask (index
    // into selectedQuest.tasks()) the player is currently picking an inventory item for. Only one of
    // the two is ever non-null, and the normal reward/task list doesn't render while a picker is open.
    private Integer activePickerRewardIndex = null;
    private Integer activePickerTaskIndex = null;
    private final Map<Integer, Integer> selectedTargetSlots = new HashMap<>(); // rewardIndex -> chosen inventory slot
    private final Map<Integer, List<Integer>> selectedBringItemSlots = new HashMap<>(); // taskIndex -> chosen slots
    private final List<PickerCandidateArea> pickerCandidateAreas = new ArrayList<>();
    private final List<PickerOpenArea> pickerOpenAreas = new ArrayList<>();
    private int[] pickerBackButtonBounds = null;
    // cached each renderItemPicker() call so mouseClicked's drag-start can find the right track without redoing the layout math
    private int pickerGridY;
    private int pickerGridVisibleHeight;
    // whichever scrollbar was actually clicked - reused by mouseDragged for the rest of that drag
    private int dragTrackY;
    private int dragTrackHeight;

    private boolean claimButtonHovered = false;

    // true while the confirm-dismiss overlay is showing, intercepting the dismiss checkbox click
    // it was opened from until the player picks Confirm or Cancel - covers both a normal quest and
    // a quest-line step, since both dismiss through the same DismissQuestPacket path
    private boolean confirmDismissOpen = false;
    private boolean confirmDismissConfirmHovered = false;
    private boolean confirmDismissCancelHovered = false;
    // what Yes actually does - set alongside confirmDismissOpen at each trigger site, since the same
    // dialog is now reused for both a normal quest dismiss and a quest-line cancel
    private Runnable confirmDismissAction;

    private static final int ENTITY_ROTATION_TICKS = 60; // 3 seconds at 20 ticks/second
    private int entityRotationTicks = 0;
    private final Map<Integer, Integer> taskEntityIndices = new HashMap<>();
    private final Map<Integer, List<EntityType<?>>> taskEntityLists = new HashMap<>();

    // same 3-second cadence as entity rotation, but pauses while the mouse hovers the icon so a
    // player reading a spell's tooltip doesn't have it swap to a different spell mid-read
    private final Map<Integer, Integer> taskSpellIndices = new HashMap<>();
    private final Map<Integer, List<ResourceLocation>> taskSpellLists = new HashMap<>();
    private final Map<Integer, int[]> taskSpellIconBounds = new HashMap<>(); // last-rendered 16x16 icon bounds
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    // apply_status_effect's effect_ids rotation - same pause-while-hovering mechanism as
    // taskSpellIndices above, just keyed to a list of mob effect ids instead of spells
    private final Map<Integer, Integer> taskEffectIndices = new HashMap<>();
    private final Map<Integer, List<ResourceLocation>> taskEffectLists = new HashMap<>();
    private final Map<Integer, int[]> taskEffectIconBounds = new HashMap<>();

    // some reward functions (Dungeon Difficulty's SetPowerLevelFunction) roll randomized attribute
    // values on every call, so this caches the display stack per quest selection instead of re-rolling
    // (and flickering) every frame
    private final Map<QuestReward, ItemStack> rewardDisplayItemCache = new HashMap<>();

    private int lootTableRotationTicks = 0;
    private final Map<Integer, Integer> rewardItemIndices = new HashMap<>();
    private final Map<Integer, List<ItemStack>> rewardItemLists = new HashMap<>();

    // enhance_item cycles through each of its bundled operations' own icon - same 3-second/pause-
    // while-hovering cadence as taskSpellIndices above, keyed by reward index instead of task index
    private final Map<Integer, Integer> enhanceOperationIndices = new HashMap<>();
    private final Map<Integer, int[]> enhanceIconBounds = new HashMap<>();

    private boolean tradeButtonHovered = false;
    private final boolean openedFromMerchant;

    private record HoverArea(int x, int y, int width, int height, List<Component> tooltip) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record ChoiceArea(int poolIndex, int optionIndex, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record LineOptionArea(String lineId, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record PickerCandidateArea(int inventorySlot, int x, int y, int size) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
        }
    }

    // isReward: true if index is into selectedQuest.rewards() (TargetItemReward), false if it's into selectedQuest.tasks() (BringItemTask)
    private record PickerOpenArea(boolean isReward, int index, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public QuestScreen(int entityId, List<Quest> quests, EntityQuestComponent component, UUID playerId,
                        List<String> activeLine, List<String> resolvedLines, List<ResourceLocation> claimedRoots,
                        List<ResourceLocation> acceptedRoots) {
        super(Component.translatable("gui.qe_api.quest_screen.title"));
        this.entityId = entityId;
        this.availableQuests = quests;
        this.questComponent = component;
        this.playerId = playerId;
        this.activeLine = activeLine.isEmpty() ? Optional.empty() : Optional.of(activeLine.get(0));
        this.resolvedLines = new HashSet<>(resolvedLines);
        this.claimedRoots = new HashSet<>(claimedRoots);
        this.acceptedRoots = new HashSet<>(acceptedRoots);

        this.guiWidth = DEFAULT_GUI_WIDTH;

        this.openedFromMerchant = ClientQuestCache.wasOpenedFromMerchant();
        ClientQuestCache.setOpenedFromMerchant(false);

        if (component.hasActiveQuest(playerId)) {
            this.currentProgress = component.getActiveQuest(playerId)
                    .map(EntityQuestComponent.ActiveQuestData::progress)
                    .orElse(null);
        }
        // scroll position is restored in init(), after the old screen's removed() runs
    }

    @Override
    public void removed() {
        super.removed();
        ClientQuestCache.saveScrollPositions(entityId, questScrollOffset, infoScrollOffset, selectedQuestIndex);
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (width - guiWidth) / 2;
        int minTop = 5;
        int maxTop = height - GUI_HEIGHT - 45; // leaves room for the hotbar
        int centeredTop = (height - GUI_HEIGHT) / 2 - 10; // sits a bit above dead center
        guiTop = Math.max(minTop, Math.min(centeredTop, maxTop));

        int widthDiff = guiWidth - DEFAULT_GUI_WIDTH;
        questBoxWidth = DEFAULT_QUEST_BOX_WIDTH + widthDiff;
        questScrollbarX = guiWidth - SCROLLBAR_RIGHT_OFFSET;
        infoBoxWidth = DEFAULT_INFO_BOX_WIDTH + widthDiff;
        infoScrollbarX = guiWidth - SCROLLBAR_RIGHT_OFFSET;
        tradeButtonX = guiWidth - TRADE_BUTTON_RIGHT_OFFSET;

        // has to happen here, after the old screen's removed() saved them
        ClientQuestCache.ScrollPositions savedPositions = ClientQuestCache.getScrollPositions(entityId);
        if (savedPositions != null) {
            this.questScrollOffset = savedPositions.questScrollOffset();
            this.infoScrollOffset = savedPositions.infoScrollOffset();
            if (savedPositions.selectedQuestIndex() >= 0 && savedPositions.selectedQuestIndex() < availableQuests.size()) {
                this.selectedQuestIndex = savedPositions.selectedQuestIndex();
                this.selectedQuest = availableQuests.get(savedPositions.selectedQuestIndex());
            }
        }

        // active quest wins over a restored saved selection
        if (questComponent.hasActiveQuest(playerId)) {
            questComponent.getActiveQuest(playerId).ifPresent(activeData -> {
                for (int i = 0; i < availableQuests.size(); i++) {
                    if (availableQuests.get(i).id().equals(activeData.questId())) {
                        this.selectedQuestIndex = i;
                        this.selectedQuest = availableQuests.get(i);
                        // only auto-scroll if we didn't restore a saved position - the player may have scrolled manually
                        if (savedPositions == null && i >= VISIBLE_QUESTS) {
                            this.questScrollOffset = Math.min(i, availableQuests.size() - VISIBLE_QUESTS);
                        }
                        break;
                    }
                }
            });
        }
    }

    @Override
    public void tick() {
        super.tick();

        // selection changed - reset the scroll animation and per-quest caches
        if (selectedQuestIndex != lastSelectedIndex) {
            lastSelectedIndex = selectedQuestIndex;
            textScrollTicks = 0;
            textScrollOffset = 0;
            textScrollPaused = false;
            taskEntityIndices.clear();
            taskEntityLists.clear();
            taskSpellIndices.clear();
            taskSpellLists.clear();
            taskSpellIconBounds.clear();
            taskEffectIndices.clear();
            taskEffectLists.clear();
            taskEffectIconBounds.clear();
            rewardItemIndices.clear();
            rewardItemLists.clear();
            rewardDisplayItemCache.clear();
            enhanceOperationIndices.clear();
            enhanceIconBounds.clear();
            selectedPoolChoices.clear();
            selectedTargetSlots.clear();
            selectedBringItemSlots.clear();
            activePickerRewardIndex = null;
            activePickerTaskIndex = null;
        }

        if (selectedQuestIndex >= 0 && selectedQuestIndex < availableQuests.size()) {
            Quest quest = availableQuests.get(selectedQuestIndex);
            String questName = quest.getDisplayName().getString();
            int maxTextWidth = questBoxWidth - 6;
            int textWidth = font.width(questName);

            if (textWidth > maxTextWidth) {
                textScrollTicks++;

                int maxScrollOffset = textWidth - maxTextWidth + 10; // extra padding

                if (textScrollPaused) {
                    if (textScrollTicks >= SCROLL_PAUSE_TICKS) {
                        textScrollTicks = 0;
                        textScrollOffset = 0;
                        textScrollPaused = false;
                    }
                } else if (textScrollTicks < SCROLL_DELAY_TICKS) {
                    textScrollOffset = 0;
                } else {
                    int scrollTicks = textScrollTicks - SCROLL_DELAY_TICKS;
                    textScrollOffset = scrollTicks / SCROLL_SPEED_TICKS;

                    if (textScrollOffset >= maxScrollOffset) {
                        textScrollOffset = maxScrollOffset;
                        textScrollPaused = true;
                        textScrollTicks = 0;
                    }
                }
            } else {
                textScrollOffset = 0;
            }
        }

        entityRotationTicks++;
        if (entityRotationTicks >= ENTITY_ROTATION_TICKS) {
            entityRotationTicks = 0;
            for (Map.Entry<Integer, List<EntityType<?>>> entry : taskEntityLists.entrySet()) {
                int taskIndex = entry.getKey();
                List<EntityType<?>> entities = entry.getValue();
                if (entities.size() > 1) {
                    int currentIndex = taskEntityIndices.getOrDefault(taskIndex, 0);
                    taskEntityIndices.put(taskIndex, (currentIndex + 1) % entities.size());
                }
            }

            // skip rotating while the player's hovering the icon, so the tooltip doesn't change mid-read
            for (Map.Entry<Integer, List<ResourceLocation>> entry : taskSpellLists.entrySet()) {
                int taskIndex = entry.getKey();
                List<ResourceLocation> spells = entry.getValue();
                if (spells.size() <= 1) continue;

                int[] bounds = taskSpellIconBounds.get(taskIndex);
                boolean hovered = bounds != null
                        && lastMouseX >= bounds[0] && lastMouseX < bounds[0] + 16
                        && lastMouseY >= bounds[1] && lastMouseY < bounds[1] + 16;
                if (hovered) continue;

                int currentIndex = taskSpellIndices.getOrDefault(taskIndex, 0);
                taskSpellIndices.put(taskIndex, (currentIndex + 1) % spells.size());
            }

            // same pause-while-hovering rule, applied to apply_status_effect's rotating effect_ids icon
            for (Map.Entry<Integer, List<ResourceLocation>> entry : taskEffectLists.entrySet()) {
                int taskIndex = entry.getKey();
                List<ResourceLocation> effects = entry.getValue();
                if (effects.size() <= 1) continue;

                int[] bounds = taskEffectIconBounds.get(taskIndex);
                boolean hovered = bounds != null
                        && lastMouseX >= bounds[0] && lastMouseX < bounds[0] + 16
                        && lastMouseY >= bounds[1] && lastMouseY < bounds[1] + 16;
                if (hovered) continue;

                int currentIndex = taskEffectIndices.getOrDefault(taskIndex, 0);
                taskEffectIndices.put(taskIndex, (currentIndex + 1) % effects.size());
            }

            // same pause-while-hovering rule, applied to enhance_item's rotating operation icon
            if (selectedQuest != null) {
                List<QuestReward> rewards = selectedQuest.rewards();
                for (int i = 0; i < rewards.size(); i++) {
                    if (!(rewards.get(i) instanceof EnhanceItemReward enhanceReward)) continue;
                    int opCount = enhanceReward.operations().size();
                    if (opCount <= 1) continue;

                    int[] bounds = enhanceIconBounds.get(i);
                    boolean hovered = bounds != null
                            && lastMouseX >= bounds[0] && lastMouseX < bounds[0] + 16
                            && lastMouseY >= bounds[1] && lastMouseY < bounds[1] + 16;
                    if (hovered) continue;

                    int currentIndex = enhanceOperationIndices.getOrDefault(i, 0);
                    enhanceOperationIndices.put(i, (currentIndex + 1) % opCount);
                }
            }
        }

        lootTableRotationTicks++;
        if (lootTableRotationTicks >= ENTITY_ROTATION_TICKS) {
            lootTableRotationTicks = 0;
            for (Map.Entry<Integer, List<ItemStack>> entry : rewardItemLists.entrySet()) {
                int rewardIndex = entry.getKey();
                List<ItemStack> items = entry.getValue();
                if (items.size() > 1) {
                    int currentIndex = rewardItemIndices.getOrDefault(rewardIndex, 0);
                    rewardItemIndices.put(rewardIndex, (currentIndex + 1) % items.size());
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // this screen fully overrides render() instead of delegating to Screen's background dispatch,
        // so we have to dim the world manually to match vanilla inventory/container screens
        this.renderTransparentBackground(graphics);

        this.lastMouseX = mouseX; // for tick()'s spell-rotation pause-on-hover check

        this.lastMouseY = mouseY;

        hoverAreas.clear();
        choiceAreas.clear();
        pickerOpenAreas.clear();
        lineOptionAreas.clear();

        graphics.blit(TEXTURE, guiLeft, guiTop, 0, 0, guiWidth, GUI_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.drawString(font, title, guiLeft + 8, guiTop + 4, 0x404040, false);

        renderQuestList(graphics, mouseX, mouseY);

        if (selectedQuest != null) {
            if (activePickerRewardIndex != null || activePickerTaskIndex != null) {
                renderItemPicker(graphics, mouseX, mouseY);
            } else {
                renderQuestInfo(graphics, mouseX, mouseY);
            }
        }

        renderClaimButton(graphics, mouseX, mouseY);

        if (openedFromMerchant) {
            renderTradeButton(graphics, mouseX, mouseY);
        }

        renderTooltips(graphics, mouseX, mouseY);

        if (confirmDismissOpen) {
            renderConfirmDismissDialog(graphics, mouseX, mouseY);
        }
    }

    // A quest is locked when follow_quest_order is on, it's above tier 1, and some shown lower tier
    // isn't completed yet. Only relevant with show_all_quests on, where the server sends locked tiers too.
    private boolean isQuestLocked(Quest quest) {
        if (!quest.followQuestOrder() || quest.tier() <= 1) {
            return false;
        }
        for (Quest other : availableQuests) {
            if (other.tier() < quest.tier() && !questComponent.hasCompletedQuest(playerId, other.id())) {
                return true;
            }
        }
        return false;
    }

    private void renderQuestList(GuiGraphics graphics, int mouseX, int mouseY) {
        int maxScroll = Math.max(0, availableQuests.size() - VISIBLE_QUESTS);
        questScrollOffset = Math.max(0, Math.min(questScrollOffset, maxScroll));

        for (int i = 0; i < VISIBLE_QUESTS; i++) {
            int questIndex = questScrollOffset + i;
            int slotY = guiTop + QUEST_LIST_Y + (i * CHECKBOX_SIZE);
            int checkboxX = guiLeft + QUEST_LIST_X;
            int nameBoxX = guiLeft + 28;

            if (questIndex < availableQuests.size()) {
                Quest quest = availableQuests.get(questIndex);
                // a line-root never enters questComponent's active-quest map (see isLineRootQuest's
                // javadoc), so its checkbox state is driven by acceptedRoots/claimedRoots instead
                boolean isActive = isLineRootQuest(quest)
                        ? acceptedRoots.contains(quest.id()) && !claimedRoots.contains(quest.id())
                        : questComponent.getActiveQuest(playerId)
                                .map(data -> data.questId().equals(quest.id()))
                                .orElse(false);
                boolean isCompleted = questComponent.hasCompletedQuest(playerId, quest.id());

                // isActive wins over isCompleted - a repeatable quest being re-run is technically both
                // (see Quest.repeatAfterDays), but should read as active, not "already done"
                if (isActive) {
                    graphics.blit(WHITE_CHECKMARK_TEXTURE, checkboxX + CHECKMARK_INSET, slotY + CHECKMARK_INSET,
                            0, 0, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE);
                } else if (isCompleted) {
                    graphics.blit(GREEN_CHECKMARK_TEXTURE, checkboxX + CHECKMARK_INSET, slotY + CHECKMARK_INSET,
                            0, 0, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE);
                } else if (isQuestLocked(quest)) {
                    graphics.blit(QUEST_LOCKED_TEXTURE, checkboxX + CHECKMARK_INSET, slotY + CHECKMARK_INSET,
                            0, 0, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE);
                    hoverAreas.add(new HoverArea(checkboxX, slotY, CHECKBOX_SIZE, CHECKBOX_SIZE,
                            List.of(Component.translatable("gui.qe_api.quest_locked"))));
                }
                // empty checkbox is already part of the background texture

                if (selectedQuestIndex == questIndex) {
                    graphics.fill(nameBoxX + 1, slotY + 1, nameBoxX + questBoxWidth - 1,
                            slotY + QUEST_BOX_HEIGHT - 1, 0x80FFFFFF);
                }

                String questName = quest.getDisplayName().getString();
                int maxTextWidth = questBoxWidth - 6;
                int textColor = isActive ? 0xFFFFFF : (isCompleted ? 0x808080 : 0x000000);

                graphics.enableScissor(nameBoxX + 2, slotY + 1, nameBoxX + questBoxWidth - 2, slotY + QUEST_BOX_HEIGHT - 1);

                if (selectedQuestIndex == questIndex && font.width(questName) > maxTextWidth) {
                    graphics.drawString(font, questName, nameBoxX + 3 - textScrollOffset, slotY + 6, textColor, false);
                } else {
                    String displayName = questName;
                    if (font.width(questName) > maxTextWidth) {
                        displayName = font.plainSubstrByWidth(questName, maxTextWidth - 6) + "...";
                    }
                    graphics.drawString(font, displayName, nameBoxX + 3, slotY + 6, textColor, false);
                }

                graphics.disableScissor();
            }
        }

        if (availableQuests.size() > VISIBLE_QUESTS) {
            renderScrollbar(graphics, guiLeft + questScrollbarX, guiTop + QUEST_SCROLLBAR_Y,
                    QUEST_SCROLLBAR_WIDTH, QUEST_SCROLLBAR_HEIGHT,
                    questScrollOffset, maxScroll);
        }
    }

    private void renderQuestInfo(GuiGraphics graphics, int mouseX, int mouseY) {
        int infoX = guiLeft + INFO_BOX_X + 4;
        int infoY = guiTop + INFO_BOX_Y + 4;
        int infoWidth = infoBoxWidth - 8;
        int infoHeight = INFO_BOX_HEIGHT - 8;

        int contentHeight = calculateInfoContentHeight();
        maxInfoScroll = Math.max(0, contentHeight - infoHeight);
        infoScrollOffset = Math.max(0, Math.min(infoScrollOffset, maxInfoScroll));

        graphics.enableScissor(guiLeft + INFO_BOX_X + 1, guiTop + INFO_BOX_Y + 1,
                guiLeft + INFO_BOX_X + infoBoxWidth - 1, guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT - 1);

        int currentY = infoY - infoScrollOffset;

        List<FormattedCharSequence> nameLines = font.split(
                selectedQuest.getDisplayName().copy().withStyle(ChatFormatting.BOLD), infoWidth);
        for (var line : nameLines) {
            graphics.drawString(font, line, infoX, currentY, 0x000000, false);
            currentY += 10;
        }
        currentY += 4;

        List<FormattedCharSequence> descLines = font.split(getAnimatedDescription(), infoWidth);
        for (var line : descLines) {
            graphics.drawString(font, line, infoX, currentY, 0x606060, false);
            currentY += 10;
        }
        currentY += 6;

        if (!selectedQuest.requirements().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.qe_api.requirements")
                    .withStyle(ChatFormatting.UNDERLINE), infoX, currentY, 0x000000, false);
            currentY += 11;

            for (QuestRequirement requirement : selectedQuest.requirements()) {
                Component reqText = requirement.getDisplayText();
                boolean isMet = requirement.canCheckClientSide() && requirement.isMetClientSide(minecraft.player);

                int iconX = infoX;
                int textX = infoX;
                int textColor = 0x404040;
                boolean iconRendered = false;

                if (requirement instanceof HasItemRequirement hasItemRequirement) {
                    ItemStack itemStack = new ItemStack(BuiltInRegistries.ITEM.get(hasItemRequirement.itemId()));
                    if (!itemStack.isEmpty()) {
                        if (!renderTextureOverride(graphics, hasItemRequirement.textureOverrideId(), iconX, currentY - 2, isMet)) {
                            graphics.renderItem(itemStack, iconX, currentY - 2);
                            if (isMet) {
                                renderCompletionBadge(graphics, iconX, currentY - 2);
                            }
                            if (currentY - 2 >= guiTop + INFO_BOX_Y && currentY - 2 + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                                hoverAreas.add(new HoverArea(iconX, currentY - 2, 16, 16,
                                        Screen.getTooltipFromItem(minecraft, itemStack)));
                            }
                        }
                        textX = iconX + 18;
                        iconRendered = true;
                    }
                } else if (requirement.getDisplayTexture().isPresent()) {
                    // always show the actual icon, with a green checkmark badge overlaid when met, rather than swapping it out
                    if (!renderTextureOverride(graphics, requirement.textureOverrideId(), iconX, currentY - 2, isMet)) {
                        ResourceLocation texture = requirement.getDisplayTexture().get();
                        graphics.blit(texture, iconX, currentY - 2, 0, 0, 16, 16, 16, 16);
                        if (isMet) {
                            renderCompletionBadge(graphics, iconX, currentY - 2);
                        }
                    }
                    textX = iconX + 18;
                    iconRendered = true;
                }

                if (iconRendered) {
                    List<FormattedCharSequence> reqLines = font.split(reqText, infoWidth - 18);
                    for (var line : reqLines) {
                        graphics.drawString(font, line, textX, currentY, textColor, false);
                        currentY += 10;
                    }
                } else {
                    // nothing to badge, so just color the bullet text green when met
                    List<FormattedCharSequence> reqLines = font.split(
                            Component.literal("\u2022 ").append(reqText), infoWidth);
                    for (var line : reqLines) {
                        graphics.drawString(font, line, infoX, currentY, textColor, false);
                        currentY += 10;
                    }
                }
            }
            currentY += 6;
        }

        graphics.drawString(font, Component.translatable("gui.qe_api.tasks")
                .withStyle(ChatFormatting.UNDERLINE), infoX, currentY, 0x000000, false);
        currentY += 14; // extra spacing so icons don't overlap the header

        QuestProgress progress = getProgressForQuest(selectedQuest);
        for (int i = 0; i < selectedQuest.tasks().size(); i++) {
            QuestTask task = selectedQuest.tasks().get(i);
            boolean complete = task.isComplete(progress, i);
            // BringItemTask completion is recomputed live off current inventory count, so it's not a
            // permanent "done" signal like every other task's stored progress - the badge/picker below
            // are meant to react to it, but the description text staying green would misleadingly read
            // as permanent when it can flip back to false the moment the item's spent elsewhere
            int color = 0x404040;

            int iconX = infoX;
            int textX = iconX + 2;

            Component taskText;
            if (task instanceof EntityKillTask killTask) {
                EntityType<?> displayEntity = getDisplayEntityForTask(i, killTask);
                if (displayEntity != null && (killTask.entityTag().isPresent() || killTask.entityIds().size() > 1)) {
                    int current = Math.min(progress.getTaskProgress(i), killTask.amount());
                    taskText = Component.translatable("task.qe_api.entity_kill_dynamic",
                            killTask.amount(), displayEntity.getDescription().getString(), current, killTask.amount());
                } else {
                    taskText = task.getDisplayText(progress, i);
                }
            } else if (task instanceof SpellCastTask spellCastTask && SpellEngineClientCompat.isLoaded()
                    && minecraft.player != null) {
                // resolve to the actual translated spell/school name instead of the raw id/tag
                Optional<String> resolvedName = Optional.empty();
                if (spellCastTask.spellId().isPresent()) {
                    resolvedName = Optional.of(SpellEngineClientCompat.spellName(spellCastTask.spellId().get(), minecraft.player));
                } else if (spellCastTask.spellPool().isPresent()) {
                    ResourceLocation currentSpell = getDisplaySpellForPool(i, spellCastTask.spellPool().get());
                    if (currentSpell != null) {
                        resolvedName = Optional.of(SpellEngineClientCompat.spellName(currentSpell, minecraft.player));
                    }
                } else if (spellCastTask.spellSchool().isPresent()) {
                    resolvedName = Optional.of(SpellEngineClientCompat.schoolDisplayName(spellCastTask.spellSchool().get()).getString());
                }

                if (resolvedName.isPresent()) {
                    int current = Math.min(progress.getTaskProgress(i), spellCastTask.amount());
                    taskText = Component.translatable("task.qe_api.spell_cast_dynamic",
                            spellCastTask.amount(), resolvedName.get(), current, spellCastTask.amount());
                } else {
                    taskText = task.getDisplayText(progress, i);
                }
            } else {
                taskText = task.getDisplayText(progress, i);
            }

            if (task instanceof EntityKillTask killTask
                    && renderTextureOverride(graphics, killTask.textureOverrideId(), iconX, currentY, complete)) {
                textX = iconX + 18;
            } else if (task instanceof EntityKillTask killTask && minecraft.level != null) {
                EntityType<?> entityType = getDisplayEntityForTask(i, killTask);

                if (entityType != null) {
                    try {
                        Entity entity = entityType.create(minecraft.level);
                        if (entity != null) {
                            renderEntityInGui(graphics, iconX + 8, currentY + 14, 14, entity);
                            if (complete) {
                                renderCompletionBadge(graphics, iconX, currentY);
                            }
                            if (killTask.minPowerLevel().isPresent() && DungeonDifficultyCompat.isLoaded()) {
                                graphics.pose().pushPose();
                                graphics.pose().translate(0, 0, 200);
                                graphics.blit(POWER_LEVEL_ICON, iconX + 5, currentY, 0, 0, 9, 9, 9, 9);
                                graphics.pose().popPose();
                            }
                            textX = iconX + 18;

                            if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                                List<Component> entityTooltip = new ArrayList<>();
                                entityTooltip.add(entityType.getDescription().copy().withStyle(ChatFormatting.WHITE));

                                List<Component> detailedInfo = killTask.getDetailedInfo();
                                for (Component info : detailedInfo) {
                                    entityTooltip.add(info.copy().withStyle(ChatFormatting.GRAY));
                                }

                                if (killTask.entityTag().isPresent()) {
                                    entityTooltip.add(Component.literal("Tag: #" + killTask.entityTag().get().location())
                                            .withStyle(ChatFormatting.DARK_AQUA));
                                }

                                List<EntityType<?>> entityList = taskEntityLists.get(i);
                                if (entityList != null && entityList.size() > 1) {
                                    entityTooltip.add(Component.translatable("gui.qe_api.entity_rotating",
                                            taskEntityIndices.getOrDefault(i, 0) + 1, entityList.size())
                                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                                }

                                hoverAreas.add(new HoverArea(iconX, currentY, 18, 16, entityTooltip));
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // if the kill has to be attributed to a spell, show that spell's icon/tooltip (or a
                // representative rotating/generic one for spell_pool/spell_school filters) right after
                // the entity icon, so players can tell which spell(s) they need to use
                if (SpellEngineClientCompat.isLoaded()
                        && (killTask.inSpellId().isPresent() || killTask.inSpellPool().isPresent() || killTask.inSpellSchool().isPresent())) {
                    int spellIconX = iconX + 18;
                    ResourceLocation displaySpellId = null;

                    if (killTask.inSpellId().isPresent()) {
                        displaySpellId = killTask.inSpellId().get();
                    } else if (killTask.inSpellPool().isPresent()) {
                        displaySpellId = getDisplaySpellForPool(i, killTask.inSpellPool().get());
                        taskSpellIconBounds.put(i, new int[]{spellIconX, currentY});
                    }

                    if (displaySpellId != null) {
                        graphics.blit(SpellEngineClientCompat.iconTexture(displaySpellId), spellIconX, currentY, 0, 0, 16, 16, 16, 16);
                        textX = spellIconX + 18;

                        if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT
                                && minecraft.player != null) {
                            hoverAreas.add(new HoverArea(spellIconX, currentY, 16, 16,
                                    SpellEngineClientCompat.tooltipLines(displaySpellId, minecraft.player)));
                        }
                    } else if (killTask.inSpellSchool().isPresent()) {
                        // Spell Power has no per-school icons, so fall back to its generic mob-effect icon - the school name is in the task text
                        graphics.blit(SpellEngineClientCompat.SCHOOL_GENERIC_ICON, spellIconX, currentY, 0, 0, 16, 16, 16, 16);
                        textX = spellIconX + 18;

                        if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                            hoverAreas.add(new HoverArea(spellIconX, currentY, 16, 16,
                                    List.of(SpellEngineClientCompat.schoolDisplayName(killTask.inSpellSchool().get()))));
                        }
                    }
                }
            } else if (task instanceof BringItemTask bringTask) {
                ItemStack itemStack = bringTask.getDisplayStack();
                if (!itemStack.isEmpty()) {
                    int pickerIconX = iconX;
                    if (!renderTextureOverride(graphics, bringTask.textureOverrideId(), pickerIconX - 2, currentY, complete)) {
                        graphics.renderItem(itemStack, pickerIconX - 2, currentY);
                        if (complete) {
                            renderCompletionBadge(graphics, pickerIconX - 2, currentY);
                        }

                        if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                            hoverAreas.add(new HoverArea(pickerIconX - 2, currentY, 16, 16,
                                    Screen.getTooltipFromItem(minecraft, itemStack)));
                        }
                    }
                    textX = iconX + 16;

                    // always offer the picker, even with only one matching stack - otherwise the server
                    // just consumes whichever it hits first in slot order (see ClaimRewardsHelper's
                    // greedy fallback). Not gated on `complete`: checkAndUpdateBringItemProgress reads
                    // this task's progress straight off live inventory count, so it's "complete" exactly
                    // while the player is carrying enough to turn in - which is when they need the picker.
                    if (minecraft.player != null && isSelectedQuestActive()
                            && bringTask.countMatchingItems(minecraft.player.getInventory().items) > 0
                            && currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                        renderSelectionBorder(graphics, pickerIconX - 2, currentY);
                        pickerOpenAreas.add(new PickerOpenArea(false, i,
                                pickerIconX - 2 - SELECTION_INSET, currentY - SELECTION_INSET, SELECTION_SIZE, SELECTION_SIZE));
                    }
                }
            } else if (task instanceof ItemUsedTask itemUsedTask) {
                ItemStack itemStack = new ItemStack(BuiltInRegistries.ITEM.get(itemUsedTask.itemId()));
                if (!itemStack.isEmpty()) {
                    if (!renderTextureOverride(graphics, itemUsedTask.textureOverrideId(), iconX - 2, currentY, complete)) {
                        graphics.renderItem(itemStack, iconX - 2, currentY);
                        if (complete) {
                            renderCompletionBadge(graphics, iconX - 2, currentY);
                        }

                        if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                            hoverAreas.add(new HoverArea(iconX - 2, currentY, 16, 16,
                                    Screen.getTooltipFromItem(minecraft, itemStack)));
                        }
                    }
                    textX = iconX + 16;
                }
            } else if (task instanceof BrewPotionTask brewTask) {
                ItemStack potionStack = brewTask.getDisplayStack();
                if (!potionStack.isEmpty()) {
                    if (!renderTextureOverride(graphics, brewTask.textureOverrideId(), iconX - 2, currentY, complete)) {
                        graphics.renderItem(potionStack, iconX - 2, currentY);
                        if (complete) {
                            renderCompletionBadge(graphics, iconX - 2, currentY);
                        }

                        if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                            hoverAreas.add(new HoverArea(iconX - 2, currentY, 16, 16,
                                    Screen.getTooltipFromItem(minecraft, potionStack)));
                        }
                    }
                    textX = iconX + 16;
                }
            } else if (task instanceof MineBlockTask mineBlockTask) {
                if (!renderTextureOverride(graphics, mineBlockTask.textureOverrideId(), iconX - 2, currentY, complete)) {
                    // generic icon, not the specific block being mined - same idea as the other action-counting tasks
                    graphics.renderItem(new ItemStack(Items.IRON_PICKAXE), iconX - 2, currentY);
                    if (complete) {
                        renderCompletionBadge(graphics, iconX - 2, currentY);
                    }
                }
                textX = iconX + 16;
            } else if (task instanceof FishingTask fishingTask) {
                graphics.blit(fishingTask.getDisplayTexture().get(), iconX - 2, currentY, 0, 0, 16, 16, 16, 16);
                if (complete) {
                    renderCompletionBadge(graphics, iconX - 2, currentY);
                }
                textX = iconX + 16;
            } else if (task instanceof HarvestCropsTask harvestTask) {
                graphics.blit(harvestTask.getDisplayTexture().get(), iconX - 2, currentY, 0, 0, 16, 16, 16, 16);
                if (complete) {
                    renderCompletionBadge(graphics, iconX - 2, currentY);
                }
                textX = iconX + 16;
            } else if (task instanceof AnvilTask anvilTask) {
                graphics.blit(anvilTask.getDisplayTexture().get(), iconX - 2, currentY, 0, 0, 16, 16, 16, 16);
                if (complete) {
                    renderCompletionBadge(graphics, iconX - 2, currentY);
                }
                textX = iconX + 16;
            } else if (task instanceof SmithingTask smithingTask) {
                graphics.blit(smithingTask.getDisplayTexture().get(), iconX - 2, currentY, 0, 0, 16, 16, 16, 16);
                if (complete) {
                    renderCompletionBadge(graphics, iconX - 2, currentY);
                }
                textX = iconX + 16;
            } else if (task instanceof CraftingTask craftingTask) {
                graphics.blit(craftingTask.getDisplayTexture().get(), iconX - 2, currentY, 0, 0, 16, 16, 16, 16);
                if (complete) {
                    renderCompletionBadge(graphics, iconX - 2, currentY);
                }
                textX = iconX + 16;
            } else if (task instanceof EnchantingTask enchantingTask) {
                if (!renderTextureOverride(graphics, enchantingTask.textureOverrideId(), iconX - 2, currentY, complete)) {
                    // no bundled default texture for this one (unlike anvil/smithing/crafting) - the
                    // vanilla enchanting table item reads clearly enough on its own
                    graphics.renderItem(new ItemStack(Items.ENCHANTING_TABLE), iconX - 2, currentY);
                    if (complete) {
                        renderCompletionBadge(graphics, iconX - 2, currentY);
                    }
                }
                textX = iconX + 16;
            } else if (task instanceof ConditionalDropTask dropTask) {
                ItemStack dropStack = dropTask.getDisplayStack();
                if (!dropStack.isEmpty()) {
                    if (!renderTextureOverride(graphics, dropTask.textureOverrideId(), iconX - 2, currentY, complete)) {
                        graphics.renderItem(dropStack, iconX - 2, currentY);
                        if (complete) {
                            renderCompletionBadge(graphics, iconX - 2, currentY);
                        }

                        if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                            hoverAreas.add(new HoverArea(iconX - 2, currentY, 16, 16,
                                    Screen.getTooltipFromItem(minecraft, dropStack)));
                        }
                    }
                    textX = iconX + 16;
                }
            } else if (task instanceof DeliverItemTask deliverItemTask) {
                ItemStack deliverStack = deliverItemTask.getDisplayStack();
                if (!deliverStack.isEmpty()) {
                    if (!renderTextureOverride(graphics, deliverItemTask.textureOverrideId(), iconX - 2, currentY, complete)) {
                        graphics.renderItem(deliverStack, iconX - 2, currentY);
                        if (complete) {
                            renderCompletionBadge(graphics, iconX - 2, currentY);
                        }

                        if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                            hoverAreas.add(new HoverArea(iconX - 2, currentY, 16, 16,
                                    Screen.getTooltipFromItem(minecraft, deliverStack)));
                        }
                    }
                    textX = iconX + 16;
                }
            } else if (task instanceof SpellBindTask || task instanceof SpellPoolCompleteTask) {
                Optional<ResourceLocation> override = task instanceof SpellBindTask spellBindTask
                        ? spellBindTask.textureOverrideId() : ((SpellPoolCompleteTask) task).textureOverrideId();
                if (!renderTextureOverride(graphics, override, iconX - 2, currentY, complete)) {
                    ItemStack spellBindingStack = SpellEngineClientCompat.isLoaded()
                            ? SpellEngineClientCompat.spellBindingTableItemStack()
                            : new ItemStack(Items.BOOK);
                    graphics.renderItem(spellBindingStack, iconX - 2, currentY);
                    if (complete) {
                        renderCompletionBadge(graphics, iconX - 2, currentY);
                    }
                }
                textX = iconX + 16;
            } else if (task instanceof SpellCastTask spellCastTask && SpellEngineClientCompat.isLoaded()) {
                if (renderTextureOverride(graphics, spellCastTask.textureOverrideId(), iconX, currentY, complete)) {
                    textX = iconX + 18;
                } else {
                    ResourceLocation displaySpellId = null;
                    if (spellCastTask.spellId().isPresent()) {
                        displaySpellId = spellCastTask.spellId().get();
                    } else if (spellCastTask.spellPool().isPresent()) {
                        displaySpellId = getDisplaySpellForPool(i, spellCastTask.spellPool().get());
                        taskSpellIconBounds.put(i, new int[]{iconX, currentY});
                    }

                    if (displaySpellId != null) {
                        graphics.blit(SpellEngineClientCompat.iconTexture(displaySpellId), iconX, currentY, 0, 0, 16, 16, 16, 16);
                        if (complete) {
                            renderCompletionBadge(graphics, iconX, currentY);
                        }
                        textX = iconX + 18;

                        if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                            hoverAreas.add(new HoverArea(iconX, currentY, 16, 16,
                                    SpellEngineClientCompat.tooltipLines(displaySpellId, minecraft.player)));
                        }
                    } else if (spellCastTask.spellSchool().isPresent()) {
                        // same generic-icon fallback as above
                        graphics.blit(SpellEngineClientCompat.SCHOOL_GENERIC_ICON, iconX, currentY, 0, 0, 16, 16, 16, 16);
                        if (complete) {
                            renderCompletionBadge(graphics, iconX, currentY);
                        }
                        textX = iconX + 18;
                    }
                }
            } else if (task instanceof RaidCompleteTask raidTask) {
                if (!renderTextureOverride(graphics, raidTask.textureOverrideId(), iconX - 2, currentY, complete)) {
                    TextureAtlasSprite sprite = minecraft.getMobEffectTextures().get(MobEffects.RAID_OMEN);
                    graphics.blit(iconX - 2, currentY, 0, 16, 16, sprite);
                    if (complete) {
                        renderCompletionBadge(graphics, iconX - 2, currentY);
                    }
                }
                textX = iconX + 16;
            } else if (task instanceof TrialSpawnerCompleteTask trialTask) {
                if (!renderTextureOverride(graphics, trialTask.textureOverrideId(), iconX - 2, currentY, complete)) {
                    TextureAtlasSprite sprite = minecraft.getMobEffectTextures().get(MobEffects.TRIAL_OMEN);
                    graphics.blit(iconX - 2, currentY, 0, 16, 16, sprite);
                    if (complete) {
                        renderCompletionBadge(graphics, iconX - 2, currentY);
                    }
                }
                textX = iconX + 16;
            } else if (task instanceof ApplyStatusEffectTask effectTask) {
                if (!renderTextureOverride(graphics, effectTask.textureOverrideId(), iconX - 2, currentY, complete)) {
                    ResourceLocation displayEffectId = getDisplayEffectForTask(i, effectTask);
                    MobEffect effect = displayEffectId != null ? BuiltInRegistries.MOB_EFFECT.get(displayEffectId) : null;

                    if (effect != null) {
                        TextureAtlasSprite sprite = minecraft.getMobEffectTextures().get(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
                        graphics.blit(iconX - 2, currentY, 0, 16, 16, sprite);
                        if (complete) {
                            renderCompletionBadge(graphics, iconX - 2, currentY);
                        }

                        taskEffectIconBounds.put(i, new int[]{iconX - 2, currentY});

                        if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                            List<Component> effectTooltip = new ArrayList<>();
                            effectTooltip.add(effect.getDisplayName().copy().withStyle(ChatFormatting.WHITE));

                            List<ResourceLocation> effectList = taskEffectLists.get(i);
                            if (effectList != null && effectList.size() > 1) {
                                effectTooltip.add(Component.translatable("gui.qe_api.entity_rotating",
                                        taskEffectIndices.getOrDefault(i, 0) + 1, effectList.size())
                                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                            }

                            hoverAreas.add(new HoverArea(iconX - 2, currentY, 16, 16, effectTooltip));
                        }
                    }
                }
                textX = iconX + 16;
            } else if (task instanceof QuestLineChoiceTask lineChoiceTask) {
                graphics.blit(lineChoiceTask.getDisplayTexture().get(), iconX - 2, currentY, 0, 0, 16, 16, 16, 16);
                if (lineRowState(lineChoiceTask) == LineRowState.RESOLVED) {
                    renderCompletionBadge(graphics, iconX - 2, currentY);
                }
                textX = iconX + 16;
            } else if (task.getDisplayTexture().isPresent()) {
                ResourceLocation texture = task.getDisplayTexture().get();
                graphics.blit(texture, iconX - 2, currentY, 0, 0, 16, 16, 16, 16);
                if (complete) {
                    renderCompletionBadge(graphics, iconX - 2, currentY);
                }
                textX = iconX + 16;
            }
            // no special icon - textX stays at its default (iconX + 2), rendered as a colored bullet below
            String prefix = (textX == iconX + 2) ? "\u2022 " : "";
            String taskStr = prefix + taskText.getString();
            int availableWidth = infoWidth - (textX - infoX);
            List<FormattedCharSequence> taskLines = font.split(Component.literal(taskStr), availableWidth);

            for (int lineIdx = 0; lineIdx < taskLines.size(); lineIdx++) {
                graphics.drawString(font, taskLines.get(lineIdx), textX, currentY + 4, color, false);
                if (lineIdx < taskLines.size() - 1) {
                    currentY += 10;
                }
            }
            currentY += 18;

            if (task instanceof QuestLineChoiceTask lineChoiceTask) {
                LineRowState rowState = lineRowState(lineChoiceTask);
                if (rowState == LineRowState.NOT_ACCEPTED) {
                    currentY = drawWrappedLine(graphics, Component.translatable("gui.qe_api.quest_line_not_accepted"),
                            infoX, currentY, infoWidth, 0x606060);
                } else if (rowState == LineRowState.RESOLVED) {
                    currentY = drawWrappedLine(graphics, Component.translatable("gui.qe_api.quest_line_finished"),
                            infoX, currentY, infoWidth, 0x606060);
                } else {
                    currentY = renderLineOptionsRow(graphics, lineChoiceTask, infoX, currentY, infoWidth, mouseX, mouseY);
                }
            }
        }

        currentY += 6;

        graphics.drawString(font, Component.translatable("gui.qe_api.rewards")
                .withStyle(ChatFormatting.UNDERLINE), infoX, currentY, 0x000000, false);
        currentY += 14;

        List<QuestReward> rewards = selectedQuest.rewards();
        for (int i = 0; i < rewards.size(); i++) {
            currentY = renderReward(graphics, rewards.get(i), i, infoX, currentY, infoWidth, mouseX, mouseY);
        }

        // Reward choice pools - player must pick exactly `pick` options per pool before claiming
        if (!selectedQuest.rewardChoicePools().isEmpty()) {
            currentY = renderRewardChoicePools(graphics, infoX, currentY, infoWidth, mouseX, mouseY);
        }

        graphics.disableScissor();

        if (maxInfoScroll > 0) {
            renderScrollbar(graphics, guiLeft + infoScrollbarX, guiTop + INFO_SCROLLBAR_Y,
                    INFO_SCROLLBAR_WIDTH, INFO_SCROLLBAR_HEIGHT,
                    infoScrollOffset, maxInfoScroll);
        }
    }

    private static final int LINE_OPTION_ICON_SIZE = 16;
    private static final int LINE_OPTION_SPACING = 4;
    private static final int LINE_OPTION_STEP = LINE_OPTION_ICON_SIZE + LINE_OPTION_SPACING;

    // wraps every available LineOption's icon into a row within maxWidth, always - even once one is
    // already active - bordering whichever matches activeLine the same way a chosen reward-pool
    // option is bordered, or badging it instead if the line is already in resolvedLines (and
    // skipping its click area, so a resolved line can't be re-picked). Registers a hover tooltip
    // for each option regardless. Mirrors lineOptionsRowHeight's wrap math exactly, since that one
    // has to predict this method's total height without actually rendering. See mouseClicked's
    // lineOptionAreas handling for what each remaining icon does when clicked (picks a line, opens
    // the cancel dialog, or is swallowed).
    private int renderLineOptionsRow(GuiGraphics graphics, QuestLineChoiceTask task, int x, int startY, int maxWidth, int mouseX, int mouseY) {
        List<QuestLineChoiceTask.LineOption> options = task.availableLineOptions();
        int offset = 0;
        int rowY = startY;

        for (QuestLineChoiceTask.LineOption option : options) {
            if (offset + LINE_OPTION_ICON_SIZE > maxWidth) {
                offset = 0;
                rowY += LINE_OPTION_STEP;
            }
            int iconX = x + offset;

            graphics.blit(option.iconTextureId(), iconX, rowY, 0, 0,
                    LINE_OPTION_ICON_SIZE, LINE_OPTION_ICON_SIZE, LINE_OPTION_ICON_SIZE, LINE_OPTION_ICON_SIZE);

            boolean isResolved = resolvedLines.contains(option.id());
            if (isResolved) {
                renderCompletionBadge(graphics, iconX, rowY);
            } else if (activeLine.equals(Optional.of(option.id()))) {
                renderSelectionBorder(graphics, iconX, rowY);
            }

            if (rowY >= guiTop + INFO_BOX_Y && rowY + LINE_OPTION_ICON_SIZE <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(option.displayName().copy().withStyle(ChatFormatting.WHITE));
                option.description().ifPresent(desc -> tooltip.add(desc.copy().withStyle(ChatFormatting.GRAY)));
                hoverAreas.add(new HoverArea(iconX, rowY, LINE_OPTION_ICON_SIZE, LINE_OPTION_ICON_SIZE, tooltip));
                if (!isResolved) {
                    lineOptionAreas.add(new LineOptionArea(option.id(), iconX, rowY, LINE_OPTION_ICON_SIZE, LINE_OPTION_ICON_SIZE));
                }
            }

            offset += LINE_OPTION_STEP;
        }

        return rowY + LINE_OPTION_STEP;
    }

    // non-rendering counterpart of renderLineOptionsRow, for calculateInfoContentHeight
    private static int lineOptionsRowHeight(List<QuestLineChoiceTask.LineOption> options, int maxWidth) {
        if (options.isEmpty()) return 0;

        int offset = 0;
        int rows = 1;
        for (QuestLineChoiceTask.LineOption ignored : options) {
            if (offset + LINE_OPTION_ICON_SIZE > maxWidth) {
                offset = 0;
                rows++;
            }
            offset += LINE_OPTION_STEP;
        }
        return rows * LINE_OPTION_STEP;
    }

    private Entity getGivingEntity() {
        return minecraft.level != null ? minecraft.level.getEntity(entityId) : null;
    }

    private Component getAnimatedDescription() {
        Component fullDescription = selectedQuest.getDescription(getGivingEntity());

        if (!QuestEntityAPIConfig.get().quest_description_typewriter_enabled) {
            return fullDescription;
        }

        ResourceLocation questId = selectedQuest.id();
        if (shownDescriptions.contains(questId)) {
            return fullDescription;
        }

        if (!questId.equals(typewriterQuestId)) {
            typewriterQuestId = questId;
            typewriterStartMs = System.currentTimeMillis();
        }

        String fullText = fullDescription.getString();
        int speedMs = Math.max(1, QuestEntityAPIConfig.get().quest_description_typewriter_speed_ms);
        long elapsedMs = System.currentTimeMillis() - typewriterStartMs;
        int charsToShow = (int) (elapsedMs / speedMs);

        if (charsToShow >= fullText.length()) {
            shownDescriptions.add(questId);
            return fullDescription;
        }

        return Component.literal(fullText.substring(0, Math.max(0, charsToShow)));
    }

    private int calculateInfoContentHeight() {
        if (selectedQuest == null) return 0;

        int height = 0;

        List<FormattedCharSequence> nameLines = font.split(
                selectedQuest.getDisplayName().copy().withStyle(ChatFormatting.BOLD), infoBoxWidth - 8);
        height += nameLines.size() * 10 + 4;

        List<FormattedCharSequence> descLines = font.split(getAnimatedDescription(), infoBoxWidth - 8);
        height += descLines.size() * 10 + 6;

        if (!selectedQuest.requirements().isEmpty()) {
            height += 11; // requirements header
            for (QuestRequirement requirement : selectedQuest.requirements()) {
                List<FormattedCharSequence> reqLines = font.split(
                        Component.literal("\u2022 ").append(requirement.getDisplayText()), infoBoxWidth - 8);
                height += reqLines.size() * 10;
            }
            height += 6;
        }

        height += 14; // tasks header, with extra spacing

        QuestProgress progress = getProgressForQuest(selectedQuest);
        for (int i = 0; i < selectedQuest.tasks().size(); i++) {
            QuestTask task = selectedQuest.tasks().get(i);
            String prefix = task.isComplete(progress, i) ? "\u2713 " : "\u2022 ";
            String taskStr = prefix + task.getDisplayText(progress, i).getString();

            int textX = 2;
            if (task instanceof BringItemTask) {
                // selection.png-bordered icon, shifted right - always the case for this task once it
                // renders an icon at all, whether that's the live item or a texture_override_id
                textX = 18 + SELECTION_TEXT_X_OFFSET;
            } else if (task.textureOverrideId().isPresent()) {
                // an override always wins and always renders as a single 16x16 icon, even for
                // EntityKillTask's normally-wider entity+spell-icon layout
                textX = 18;
            } else if (task instanceof EntityKillTask ekt && ekt.inSpellId().isPresent() && SpellEngineClientCompat.isLoaded()) {
                textX = 36; // entity icon + spell icon side by side
            } else if (task instanceof EntityKillTask || task instanceof ItemUsedTask
                    || task instanceof BrewPotionTask || task instanceof MineBlockTask
                    || task instanceof FishingTask || task instanceof HarvestCropsTask
                    || task instanceof AnvilTask || task instanceof SmithingTask
                    || task instanceof CraftingTask || task instanceof EnchantingTask
                    || task instanceof SpellBindTask || task instanceof SpellPoolCompleteTask
                    || task instanceof ConditionalDropTask || task instanceof DeliverItemTask
                    || task instanceof RaidCompleteTask || task instanceof TrialSpawnerCompleteTask
                    || (task instanceof SpellCastTask sct && sct.spellId().isPresent() && SpellEngineClientCompat.isLoaded())
                    || task.getDisplayTexture().isPresent()) {
                textX = 18;
            }

            int availableWidth = (infoBoxWidth - 8) - textX;
            List<FormattedCharSequence> taskLines = font.split(Component.literal(taskStr), availableWidth);
            height += 18 + (taskLines.size() - 1) * 10;

            if (task instanceof QuestLineChoiceTask lineChoiceTask) {
                LineRowState rowState = lineRowState(lineChoiceTask);
                if (rowState == LineRowState.PICKABLE) {
                    height += lineOptionsRowHeight(lineChoiceTask.availableLineOptions(), infoBoxWidth - 8);
                } else if (rowState == LineRowState.NOT_ACCEPTED) {
                    height += wrappedLineHeight(Component.translatable("gui.qe_api.quest_line_not_accepted"), infoBoxWidth - 8);
                } else {
                    height += wrappedLineHeight(Component.translatable("gui.qe_api.quest_line_finished"), infoBoxWidth - 8);
                }
            }
        }

        height += 6;
        height += 14; // rewards header, with extra spacing

        // has to match the width/prefix conventions renderReward actually uses per reward type
        for (QuestReward reward : selectedQuest.rewards()) {
            boolean hasIcon = rewardHasIcon(reward);
            int textWidth;
            if (reward instanceof TargetItemReward) {
                textWidth = infoBoxWidth - 8 - 20 - SELECTION_TEXT_X_OFFSET;
            } else {
                textWidth = hasIcon ? (infoBoxWidth - 8 - 20) : (infoBoxWidth - 8);
            }
            String displayText;
            if (reward instanceof SpellBindReward spellBind && SpellEngineClientCompat.isLoaded() && minecraft.player != null) {
                displayText = Component.translatable("reward.qe_api.spell_bind",
                        SpellEngineClientCompat.spellName(spellBind.spellId(), minecraft.player)).getString();
            } else {
                displayText = reward.getDisplayText().getString();
            }
            String rewardStr = hasIcon ? displayText : "• " + displayText;
            List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), textWidth);
            height += 4 + rewardLines.size() * 10 + 4;
        }

        for (RewardChoicePool pool : selectedQuest.rewardChoicePools()) {
            height += 12; // "Choose X/Y of Z:" header
            height += (int) pool.options().stream().filter(RewardChoicePool.Option::isAvailable).count() * 18;
            height += 4;
        }

        return height;
    }

    // rewardIndex is this reward's index into selectedQuest.rewards(), or -1 for a reward-choice-pool
    // option (TargetItemReward isn't supported inside pools, see its javadoc, so those never need one)
    private int renderReward(GuiGraphics graphics, QuestReward reward, int rewardIndex, int x, int y, int maxWidth, int mouseX, int mouseY) {
        if (reward instanceof LootTableReward lootReward) {
            return renderLootTableReward(graphics, lootReward, x, y, maxWidth, mouseX, mouseY);
        }

        // handled first (before the texture-override shortcut below) since its own render method
        // applies the override internally while still preserving the item-picker interaction
        if (reward instanceof TargetItemReward targeted) {
            return renderTargetItemReward(graphics, reward, targeted, rewardIndex, x, y, maxWidth);
        }

        if (reward.textureOverrideId().isPresent()) {
            graphics.blit(reward.textureOverrideId().get(), x - 2, y, 0, 0, 16, 16, 16, 16);

            String rewardStr = reward.getDisplayText().getString();
            List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), maxWidth - 20);
            for (int lineIdx = 0; lineIdx < rewardLines.size(); lineIdx++) {
                graphics.drawString(font, rewardLines.get(lineIdx), x + 18, y + 4 + (lineIdx * 10), 0x404040, false);
            }
            return y + 4 + rewardLines.size() * 10 + 4;
        }

        ItemStack cachedDisplayItem = rewardDisplayItemCache.computeIfAbsent(reward,
                r -> r.getDisplayItem().orElse(ItemStack.EMPTY));
        boolean isExperienceReward = reward instanceof ExperienceReward;

        if (!cachedDisplayItem.isEmpty()) {
            ItemStack stack = cachedDisplayItem;
            graphics.renderItem(stack, x - 2, y);
            if (reward instanceof ItemReward itemReward
                    && itemReward.functions().stream().anyMatch(f -> f instanceof SetPowerLevelFunction)
                    && ModCompatUtil.isModLoaded("dungeon_difficulty")) {
                // GUI item rendering depth-tests, so a plain blit() at the default Z renders behind the
                // item regardless of call order - push Z to 200 like vanilla's own count/durability overlays do
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.blit(POWER_LEVEL_ICON, x + 5, y, 0, 0, 9, 9, 9, 9); // top-right corner of the 16x16 icon
                graphics.pose().popPose();
            }

            // no tooltip for experience rewards (bottle of enchanting)
            if (!isExperienceReward && y >= guiTop + INFO_BOX_Y && y + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                hoverAreas.add(new HoverArea(x - 2, y, 16, 16,
                        Screen.getTooltipFromItem(minecraft, stack)));
            }

            String rewardStr = reward.getDisplayText().getString();
            List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), maxWidth - 20);

            for (int lineIdx = 0; lineIdx < rewardLines.size(); lineIdx++) {
                graphics.drawString(font, rewardLines.get(lineIdx), x + 18, y + 4 + (lineIdx * 10), 0x404040, false);
            }

            return y + 4 + rewardLines.size() * 10 + 4;

        } else if (reward instanceof StatusEffectReward effectReward) {
            MobEffect effect = effectReward.getEffect();
            if (effect != null) {
                MobEffectTextureManager textureManager = minecraft.getMobEffectTextures();
                TextureAtlasSprite sprite = textureManager.get(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
                graphics.blit(x - 2, y, 0, 16, 16, sprite);

                if (y >= guiTop + INFO_BOX_Y && y + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                    List<Component> effectTooltip = new ArrayList<>();

                    String levelText = effectReward.amplifier() > 0 ? " " + com.qeapi.util.TextFormatting.toRomanNumeral(effectReward.amplifier() + 1) : "";
                    effectTooltip.add(effect.getDisplayName().copy().append(levelText).withStyle(ChatFormatting.WHITE));

                    String durationText = formatDuration(effectReward.duration());
                    effectTooltip.add(Component.translatable("gui.qe_api.effect_duration", durationText)
                            .withStyle(ChatFormatting.GRAY));

                    effectTooltip.add(Component.translatable("gui.qe_api.effect_level", effectReward.amplifier() + 1)
                            .withStyle(ChatFormatting.GRAY));

                    effectReward.getEffectDescription().ifPresent(desc ->
                            effectTooltip.add(desc.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));

                    hoverAreas.add(new HoverArea(x - 2, y, 16, 16, effectTooltip));
                }

                String rewardStr = reward.getDisplayText().getString();
                List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), maxWidth - 20);

                for (int lineIdx = 0; lineIdx < rewardLines.size(); lineIdx++) {
                    graphics.drawString(font, rewardLines.get(lineIdx), x + 18, y + 4 + (lineIdx * 10), 0x404040, false);
                }
                return y + 4 + rewardLines.size() * 10 + 4;
            } else {
                String rewardStr = "\u2022 " + reward.getDisplayText().getString();
                List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), maxWidth);

                for (int lineIdx = 0; lineIdx < rewardLines.size(); lineIdx++) {
                    graphics.drawString(font, rewardLines.get(lineIdx), x, y + 4 + (lineIdx * 10), 0x404040, false);
                }
                return y + 4 + rewardLines.size() * 10 + 4;
            }
        } else if (reward instanceof SkillExperienceReward || reward instanceof SkillLevelReward
                || reward instanceof SetQuestGroupReward) {
            // texture_override_id (this type's only icon concept) is already handled by the
            // top-of-method override shortcut - reaching here means it was absent, so this is
            // always the generic experience-bottle fallback
            return renderSkillTreeReward(graphics, Optional.empty(), reward.getDisplayText().getString(), x, y, maxWidth);
        } else if (reward instanceof LevelZSkillLevelReward levelZSkillLevel) {
            Optional<ResourceLocation> icon = LevelZCompat.isLoaded()
                    ? Optional.of(LevelZCompat.skillIcon(levelZSkillLevel.skillId())) : Optional.empty();
            return renderSkillTreeReward(graphics, icon, reward.getDisplayText().getString(), x, y, maxWidth);
        } else if (reward instanceof SpellScrollReward spellScrollReward && SpellEngineClientCompat.isLoaded()) {
            return renderSpellScrollReward(graphics, spellScrollReward, x, y, maxWidth);
        } else {
            String rewardStr = "\u2022 " + reward.getDisplayText().getString();
            List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), maxWidth);

            for (int lineIdx = 0; lineIdx < rewardLines.size(); lineIdx++) {
                graphics.drawString(font, rewardLines.get(lineIdx), x, y + 4 + (lineIdx * 10), 0x404040, false);
            }

            return y + 4 + rewardLines.size() * 10 + 4;
        }
    }

    // generic icon+text row: the quest author's own icon if given, else a generic experience-bottle
    // icon - shared by Pufferfish's Skills rewards (whose category icon isn't queryable through its
    // stable API) and SetQuestGroupReward
    private int renderSkillTreeReward(GuiGraphics graphics, Optional<ResourceLocation> icon, String rewardStr,
                                       int x, int y, int maxWidth) {
        if (icon.isPresent()) {
            graphics.blit(icon.get(), x - 2, y, 0, 0, 16, 16, 16, 16);
        } else {
            graphics.renderItem(new ItemStack(Items.EXPERIENCE_BOTTLE), x - 2, y);
        }

        List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), maxWidth - 20);
        for (int lineIdx = 0; lineIdx < rewardLines.size(); lineIdx++) {
            graphics.drawString(font, rewardLines.get(lineIdx), x + 18, y + 4 + (lineIdx * 10), 0x404040, false);
        }

        return y + 4 + rewardLines.size() * 10 + 4;
    }

    // generic scroll icon - the actual spell is only rolled server-side at claim time, nothing specific to preview
    private int renderSpellScrollReward(GuiGraphics graphics, SpellScrollReward reward, int x, int y, int maxWidth) {
        graphics.renderItem(SpellEngineClientCompat.genericScrollItemStack(reward.pool()), x - 2, y);

        String rewardStr = reward.getDisplayText().getString();
        List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), maxWidth - 20);
        for (int lineIdx = 0; lineIdx < rewardLines.size(); lineIdx++) {
            graphics.drawString(font, rewardLines.get(lineIdx), x + 18, y + 4 + (lineIdx * 10), 0x404040, false);
        }

        return y + 4 + rewardLines.size() * 10 + 4;
    }

    // renders each pool as a header + its options, tracking clickable regions in choiceAreas
    private int renderRewardChoicePools(GuiGraphics graphics, int x, int y, int maxWidth, int mouseX, int mouseY) {
        List<RewardChoicePool> pools = selectedQuest.rewardChoicePools();
        int currentY = y;

        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            RewardChoicePool pool = pools.get(poolIndex);
            List<Integer> selected = selectedPoolChoices.computeIfAbsent(poolIndex, k -> new ArrayList<>());

            boolean isPathChoice = !pool.options().isEmpty()
                    && pool.options().stream().allMatch(option -> option.reward() instanceof SetQuestGroupReward);
            int availableCount = (int) pool.options().stream().filter(RewardChoicePool.Option::isAvailable).count();
            Component header = isPathChoice
                    ? Component.translatable("gui.qe_api.choose_quest_path")
                    : Component.translatable("gui.qe_api.choose_rewards",
                            selected.size(), pool.pick(), availableCount);

            // path-choice's selection border is wider than its 16x16 icon (SELECTION_INSET), so it
            // clips past the info box's left edge at x - shift the whole section right to clear it,
            // same fix as renderTargetItemReward
            int sectionX = isPathChoice ? x + SELECTION_ICON_X_OFFSET : x;
            int sectionMaxWidth = isPathChoice ? maxWidth - SELECTION_ICON_X_OFFSET : maxWidth;

            graphics.drawString(font, header, sectionX, currentY, 0x000000, false);
            currentY += 12;

            for (int optionIndex = 0; optionIndex < pool.options().size(); optionIndex++) {
                if (!pool.options().get(optionIndex).isAvailable()) continue; // required_mod not loaded
                QuestReward option = pool.options().get(optionIndex).reward();
                boolean isSelected = selected.contains(optionIndex);
                int optionStartY = currentY;

                currentY = renderReward(graphics, option, -1, sectionX, currentY, sectionMaxWidth, mouseX, mouseY);

                // border goes around the option's own icon, not the whole row - replaces the old full-row green fill
                if (isSelected && rewardHasIcon(option)) {
                    renderSelectionBorder(graphics, sectionX - 2, optionStartY);
                }

                if (optionStartY >= guiTop + INFO_BOX_Y && currentY <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                    choiceAreas.add(new ChoiceArea(poolIndex, optionIndex, sectionX - 2, optionStartY - 1,
                            sectionMaxWidth + 2, currentY - optionStartY + 1));
                }
            }

            currentY += 4;
        }

        return currentY;
    }

    private int renderLootTableReward(GuiGraphics graphics, LootTableReward lootReward, int x, int y, int maxWidth, int mouseX, int mouseY) {
        if (!renderTextureOverride(graphics, lootReward.textureOverrideId(), x - 2, y, false)) {
            ItemStack displayItem = lootReward.getDisplayItem().orElse(ItemStack.EMPTY);
            if (!displayItem.isEmpty()) {
                graphics.renderItem(displayItem, x - 2, y);
            }

            if (y >= guiTop + INFO_BOX_Y && y + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                List<Component> lootTooltip = new ArrayList<>();
                lootTooltip.add(Component.translatable("gui.qe_api.loot_table_reward")
                        .withStyle(ChatFormatting.GOLD));
                lootTooltip.add(Component.literal(lootReward.lootTableId().toString())
                        .withStyle(ChatFormatting.GRAY));
                hoverAreas.add(new HoverArea(x - 2, y, 16, 16, lootTooltip));
            }
        }

        String formattedName = formatLootTableName(lootReward.lootTableId());
        String rewardStr = Component.translatable("reward.qe_api.loot_table_formatted", formattedName).getString();
        List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), maxWidth - 20);

        for (int lineIdx = 0; lineIdx < rewardLines.size(); lineIdx++) {
            graphics.drawString(font, rewardLines.get(lineIdx), x + 18, y + 4 + (lineIdx * 10), 0x404040, false);
        }

        return y + 18;
    }

    private EnhanceOperation getCurrentEnhanceOperation(int rewardIndex, EnhanceItemReward reward) {
        List<EnhanceOperation> operations = reward.operations();
        if (operations.isEmpty()) return null;
        int index = enhanceOperationIndices.getOrDefault(rewardIndex, 0);
        return operations.get(index % operations.size());
    }

    // "nothing picked yet" placeholder icon for a TargetItemReward, dispatched by concrete type -
    // works the same whether targetItemReward is a standalone reward or one of enhance_item's own
    // operations, since every permitted EnhanceOperation type is also one of these concrete reward
    // records. Returns false (renders nothing) for a type with no special icon of its own, so the
    // caller falls back to PICKER_PLACEHOLDER_STACK (BARRIER).
    private boolean renderTargetItemPlaceholderIcon(GuiGraphics graphics, TargetItemReward targetItemReward, int x, int y) {
        if (targetItemReward instanceof EnchantRandomlyReward || targetItemReward instanceof EnchantSpecificReward) {
            graphics.renderItem(new ItemStack(Items.ENCHANTED_BOOK), x, y);
            return true;
        }
        if (targetItemReward instanceof RepairItemReward) {
            graphics.blit(RepairItemReward.DEFAULT_TEXTURE, x, y, 0, 0, 16, 16, 16, 16);
            return true;
        }
        if (targetItemReward instanceof IncreasePowerLevelReward) {
            if (!ModCompatUtil.isModLoaded("dungeon_difficulty")) return false;
            graphics.blit(POWER_LEVEL_ICON, x, y, 0, 0, 16, 16, 16, 16);
            return true;
        }
        if (targetItemReward instanceof IncreaseEnchantSlotsReward) {
            graphics.blit(IncreaseEnchantSlotsReward.DEFAULT_TEXTURE, x, y, 0, 0, 16, 16, 16, 16);
            return true;
        }
        if (targetItemReward instanceof SpellBindReward spellBind && SpellEngineClientCompat.isLoaded() && minecraft.player != null) {
            graphics.blit(SpellEngineClientCompat.iconTexture(spellBind.spellId()), x, y, 0, 0, 16, 16, 16, 16);
            return true;
        }
        return false;
    }

    // renders a TargetItemReward's row: the chosen item's icon once picked, or a placeholder before
    // that, always bordered, plus a click area that opens the item-picker overlay for this reward
    private int renderTargetItemReward(GuiGraphics graphics, QuestReward reward, TargetItemReward targeted,
                                        int rewardIndex, int x, int y, int maxWidth) {
        Integer slot = rewardIndex >= 0 ? selectedTargetSlots.get(rewardIndex) : null;
        ItemStack chosen = ItemStack.EMPTY;
        if (slot != null && minecraft.player != null
                && slot >= 0 && slot < minecraft.player.getInventory().items.size()) {
            ItemStack candidate = minecraft.player.getInventory().items.get(slot);
            if (targeted.isValidTarget(minecraft.level, candidate)) {
                chosen = candidate;
            }
        }

        int pickerIconX = x + SELECTION_ICON_X_OFFSET; // shifted right so the wider border clears the info box's left edge

        // an override always wins over both the picked-item and placeholder states - the item-picker
        // interaction below is unaffected either way, only the icon shown changes
        if (!renderTextureOverride(graphics, reward.textureOverrideId(), pickerIconX - 2, y, false)) {
            boolean showSpellIcon = chosen.isEmpty() && reward instanceof SpellBindReward spellBind
                    && SpellEngineClientCompat.isLoaded() && minecraft.player != null;

            if (!chosen.isEmpty()) {
                graphics.renderItem(chosen, pickerIconX - 2, y);
                if (y >= guiTop + INFO_BOX_Y && y + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                    hoverAreas.add(new HoverArea(pickerIconX - 2, y, 16, 16, Screen.getTooltipFromItem(minecraft, chosen)));
                }
            } else if (showSpellIcon) {
                ResourceLocation spellId = ((SpellBindReward) reward).spellId();
                graphics.blit(SpellEngineClientCompat.iconTexture(spellId), pickerIconX - 2, y, 0, 0, 16, 16, 16, 16);
                if (y >= guiTop + INFO_BOX_Y && y + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                    hoverAreas.add(new HoverArea(pickerIconX - 2, y, 16, 16,
                            SpellEngineClientCompat.tooltipLines(spellId, minecraft.player)));
                }
            } else {
                TargetItemReward placeholderSource = targeted;
                if (targeted instanceof EnhanceItemReward enhanceReward && rewardIndex >= 0) {
                    enhanceIconBounds.put(rewardIndex, new int[]{pickerIconX - 2, y});
                    placeholderSource = getCurrentEnhanceOperation(rewardIndex, enhanceReward);
                }

                if (placeholderSource == null || !renderTargetItemPlaceholderIcon(graphics, placeholderSource, pickerIconX - 2, y)) {
                    graphics.renderItem(PICKER_PLACEHOLDER_STACK, pickerIconX - 2, y); // signals "click to pick an item"
                }
            }
        }

        renderSelectionBorder(graphics, pickerIconX - 2, y);

        // only interactive once the quest is accepted (see isSelectedQuestActive) and at least one
        // valid target exists - no point opening an overlay just to say "nothing valid here"
        boolean hasValidTarget = minecraft.player != null && minecraft.level != null
                && minecraft.player.getInventory().items.stream()
                        .anyMatch(stack -> targeted.isValidTarget(minecraft.level, stack));
        if (rewardIndex >= 0 && isSelectedQuestActive() && hasValidTarget
                && y >= guiTop + INFO_BOX_Y && y + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
            pickerOpenAreas.add(new PickerOpenArea(true, rewardIndex,
                    pickerIconX - 2 - SELECTION_INSET, y - SELECTION_INSET, SELECTION_SIZE, SELECTION_SIZE));
        }

        String rewardStr;
        if (reward instanceof SpellBindReward spellBind && SpellEngineClientCompat.isLoaded() && minecraft.player != null) {
            rewardStr = Component.translatable("reward.qe_api.spell_bind",
                    SpellEngineClientCompat.spellName(spellBind.spellId(), minecraft.player)).getString();
        } else {
            rewardStr = reward.getDisplayText().getString();
        }
        List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), maxWidth - 20 - SELECTION_TEXT_X_OFFSET);
        for (int lineIdx = 0; lineIdx < rewardLines.size(); lineIdx++) {
            graphics.drawString(font, rewardLines.get(lineIdx), x + 18 + SELECTION_TEXT_X_OFFSET, y + 4 + (lineIdx * 10), 0x404040, false);
        }
        return y + 4 + rewardLines.size() * 10 + 4;
    }

    // whether renderReward draws an icon for this reward vs. bullet+text only - shared source of
    // truth for calculateInfoContentHeight's layout math and the reward-choice-pool border check
    private static boolean rewardHasIcon(QuestReward reward) {
        return reward.textureOverrideId().isPresent()
                || reward.getDisplayItem().isPresent()
                || reward instanceof LootTableReward
                || (reward instanceof StatusEffectReward statusEffectReward && statusEffectReward.getEffect() != null)
                || reward instanceof SkillExperienceReward || reward instanceof SkillLevelReward
                || (reward instanceof LevelZSkillLevelReward && LevelZCompat.isLoaded())
                || reward instanceof SetQuestGroupReward
                || (reward instanceof SpellScrollReward && SpellEngineClientCompat.isLoaded())
                || reward instanceof TargetItemReward;
    }

    // lines up over a 16x16 icon at (iconX, iconY); pushes Z like the power-level badge does since
    // it's drawn after graphics.renderItem's own depth-tested geometry
    private void renderSelectionBorder(GuiGraphics graphics, int iconX, int iconY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.blit(SELECTION_TEXTURE, iconX - SELECTION_INSET, iconY - SELECTION_INSET,
                0, 0, SELECTION_SIZE, SELECTION_SIZE, SELECTION_SIZE, SELECTION_SIZE);
        graphics.pose().popPose();
    }

    // full-panel overlay that replaces the quest info box while the player picks an inventory item
    // for a TargetItemReward or ambiguous BringItemTask (see activePickerRewardIndex/TaskIndex)
    private void renderItemPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        pickerCandidateAreas.clear();
        pickerBackButtonBounds = null;
        if (minecraft.player == null || minecraft.level == null) return;

        List<ItemStack> allItems = minecraft.player.getInventory().items;
        List<Integer> candidateSlots = new ArrayList<>();

        if (activePickerRewardIndex != null) {
            QuestReward reward = selectedQuest.rewards().get(activePickerRewardIndex);
            if (reward instanceof TargetItemReward targeted) {
                for (int slot = 0; slot < allItems.size(); slot++) {
                    if (targeted.isValidTarget(minecraft.level, allItems.get(slot))) {
                        candidateSlots.add(slot);
                    }
                }
            }
        } else if (activePickerTaskIndex != null) {
            QuestTask task = selectedQuest.tasks().get(activePickerTaskIndex);
            if (task instanceof BringItemTask bringTask) {
                for (int slot = 0; slot < allItems.size(); slot++) {
                    if (bringTask.matches(allItems.get(slot))) {
                        candidateSlots.add(slot);
                    }
                }
            }
        }

        int panelX = guiLeft + INFO_BOX_X;
        int panelY = guiTop + INFO_BOX_Y;

        graphics.blit(ITEM_SELECTION_TEXTURE, panelX, panelY, 0, 0,
                ITEM_SELECTION_WIDTH, ITEM_SELECTION_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);

        graphics.drawString(font, Component.translatable("gui.qe_api.pick_item"),
                panelX + PICKER_TITLE_X, panelY + PICKER_TITLE_Y, 0x000000, false);

        Component backLabel = Component.translatable("gui.qe_api.back");
        int backTextX = panelX + PICKER_BACK_TEXT_X;
        int backTextY = panelY + PICKER_BACK_TEXT_Y;
        graphics.drawString(font, backLabel, backTextX, backTextY, 0x000000, false);

        int backAreaX = panelX + PICKER_BACK_ARROW_X;
        int backAreaY = panelY + PICKER_BACK_ARROW_Y;
        int backAreaWidth = backTextX + font.width(backLabel) - backAreaX;
        pickerBackButtonBounds = new int[]{backAreaX, backAreaY, backAreaWidth, PICKER_BACK_ARROW_HEIGHT};

        int gridX = panelX + PICKER_GRID_FRAME_X;
        int gridY = panelY + PICKER_GRID_FRAME_Y;
        pickerGridY = gridY;
        pickerGridVisibleHeight = PICKER_GRID_FRAME_HEIGHT;

        if (candidateSlots.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.qe_api.no_valid_items"),
                    panelX + PICKER_GRID_ICON_X, panelY + PICKER_GRID_ICON_Y, 0xAAAAAA, false);
            maxInfoScroll = 0;
            return;
        }

        int rows = (candidateSlots.size() + PICKER_GRID_COLUMNS - 1) / PICKER_GRID_COLUMNS;
        int contentHeight = rows * PICKER_GRID_CELL_SIZE;
        maxInfoScroll = Math.max(0, contentHeight - PICKER_GRID_FRAME_HEIGHT);
        infoScrollOffset = Math.max(0, Math.min(infoScrollOffset, maxInfoScroll));

        Integer currentRewardSelection = activePickerRewardIndex != null
                ? selectedTargetSlots.get(activePickerRewardIndex) : null;
        List<Integer> currentBringSelection = activePickerTaskIndex != null
                ? selectedBringItemSlots.getOrDefault(activePickerTaskIndex, List.of()) : List.of();

        graphics.enableScissor(gridX, gridY, gridX + PICKER_GRID_FRAME_WIDTH, gridY + PICKER_GRID_FRAME_HEIGHT);

        int iconOriginX = panelX + PICKER_GRID_ICON_X;
        int iconOriginY = panelY + PICKER_GRID_ICON_Y;

        for (int i = 0; i < candidateSlots.size(); i++) {
            int slot = candidateSlots.get(i);
            int col = i % PICKER_GRID_COLUMNS;
            int row = i / PICKER_GRID_COLUMNS;
            int iconX = iconOriginX + col * PICKER_GRID_CELL_SIZE;
            int iconY = iconOriginY + row * PICKER_GRID_CELL_SIZE - infoScrollOffset;

            if (iconY + 16 < gridY || iconY > gridY + PICKER_GRID_FRAME_HEIGHT) {
                continue;
            }

            ItemStack stack = allItems.get(slot);
            graphics.renderItem(stack, iconX, iconY);
            graphics.renderItemDecorations(font, stack, iconX, iconY);

            boolean isSelected = activePickerRewardIndex != null
                    ? slot == (currentRewardSelection != null ? currentRewardSelection : -1)
                    : currentBringSelection.contains(slot);

            if (isSelected) {
                renderSelectionBorder(graphics, iconX, iconY);
            }

            if (mouseX >= iconX - 1 && mouseX < iconX + PICKER_GRID_CELL_SIZE - 1
                    && mouseY >= iconY - 1 && mouseY < iconY + PICKER_GRID_CELL_SIZE - 1) {
                hoverAreas.add(new HoverArea(iconX - 1, iconY - 1, PICKER_GRID_CELL_SIZE, PICKER_GRID_CELL_SIZE,
                        Screen.getTooltipFromItem(minecraft, stack)));
            }

            pickerCandidateAreas.add(new PickerCandidateArea(slot,
                    iconX - 1, iconY - 1, PICKER_GRID_CELL_SIZE));
        }

        graphics.disableScissor();

        if (maxInfoScroll > 0) {
            renderScrollbar(graphics, guiLeft + infoScrollbarX, gridY, INFO_SCROLLBAR_WIDTH, PICKER_GRID_FRAME_HEIGHT,
                    infoScrollOffset, maxInfoScroll);
        }
    }

    // e.g. "minecraft:chests/simple_dungeon" -> "Simple Dungeon Loot Table"
    private String formatLootTableName(ResourceLocation lootTableId) {
        String path = lootTableId.getPath();
        if (path.contains("/")) {
            path = path.substring(path.lastIndexOf('/') + 1);
        }
        return com.qeapi.util.TextFormatting.titleCaseWords(path);
    }

    private String formatDuration(int seconds) {
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            if (secs > 0) {
                return String.format("%d:%02d", minutes, secs);
            } else {
                return minutes + " min";
            }
        } else {
            return seconds + "s";
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int width, int height, int scrollOffset, int maxScroll) {
        if (maxScroll <= 0) return;

        int trackHeight = height - SCROLLBAR_THUMB_HEIGHT;
        float scrollPercent = (float) scrollOffset / maxScroll;
        int thumbY = y + (int) (scrollPercent * trackHeight);

        graphics.blit(TEXTURE, x, thumbY, SCROLLBAR_THUMB_U, SCROLLBAR_THUMB_V,
                SCROLLBAR_THUMB_WIDTH, SCROLLBAR_THUMB_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderClaimButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonX = guiLeft + CLAIM_BUTTON_X;
        int buttonY = guiTop + CLAIM_BUTTON_Y;

        claimButtonHovered = mouseX >= buttonX && mouseX < buttonX + CLAIM_BUTTON_WIDTH
                && mouseY >= buttonY && mouseY < buttonY + CLAIM_BUTTON_HEIGHT;

        boolean canClaim = canClaimReward();

        if (canClaim) {
            graphics.blit(TEXTURE, buttonX, buttonY, 0, 222,
                    CLAIM_BUTTON_WIDTH, CLAIM_BUTTON_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        }

        if (claimButtonHovered && canClaim) {
            graphics.fill(buttonX + 1, buttonY + 1, buttonX + CLAIM_BUTTON_WIDTH - 1,
                    buttonY + CLAIM_BUTTON_HEIGHT - 1, 0x40FFFFFF);
        }

        Component buttonText = Component.translatable("gui.qe_api.claim");
        int textWidth = font.width(buttonText);
        int textX = buttonX + (CLAIM_BUTTON_WIDTH - textWidth) / 2;
        int textY = buttonY + (CLAIM_BUTTON_HEIGHT - 8) / 2;
        int textColor = canClaim ? 0x404040 : 0xFFFFFF;
        graphics.drawString(font, buttonText, textX, textY, textColor, false);
    }

    private void renderTradeButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonX = guiLeft + tradeButtonX;
        int buttonY = guiTop + TRADE_BUTTON_Y;

        tradeButtonHovered = mouseX >= buttonX && mouseX < buttonX + TRADE_BUTTON_SIZE
                && mouseY >= buttonY && mouseY < buttonY + TRADE_BUTTON_SIZE;

        // matches vanilla button styling
        graphics.fill(buttonX, buttonY, buttonX + TRADE_BUTTON_SIZE, buttonY + TRADE_BUTTON_SIZE, 0xFF8B8B8B);
        graphics.fill(buttonX + 1, buttonY + 1, buttonX + TRADE_BUTTON_SIZE - 1, buttonY + TRADE_BUTTON_SIZE - 1, 0xFFC6C6C6);

        if (tradeButtonHovered) {
            graphics.fill(buttonX + 1, buttonY + 1, buttonX + TRADE_BUTTON_SIZE - 1,
                    buttonY + TRADE_BUTTON_SIZE - 1, 0x40FFFFFF);
        }

        Component buttonText = Component.literal("T");
        int textWidth = font.width(buttonText);
        int textX = buttonX + (TRADE_BUTTON_SIZE - textWidth) / 2;
        int textY = buttonY + (TRADE_BUTTON_SIZE - 8) / 2;
        graphics.drawString(font, buttonText, textX, textY, 0x404040, false);
    }

    // shown before a dismiss checkbox click actually fires - see confirmDismissOpen. Button UV
    // coordinates in the atlas double as their draw offset from the dialog's top-left, since the
    // art is laid out 1:1 with where each piece renders.
    private void renderConfirmDismissDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0x80000000);

        int dialogX = guiLeft + (guiWidth - CONFIRM_DIALOG_WIDTH) / 2;
        int dialogY = guiTop + (GUI_HEIGHT - CONFIRM_DIALOG_HEIGHT) / 2;

        graphics.blit(CONFIRM_DIALOG_TEXTURE, dialogX, dialogY, 0, 0,
                CONFIRM_DIALOG_WIDTH, CONFIRM_DIALOG_HEIGHT,
                CONFIRM_DIALOG_ATLAS_SIZE, CONFIRM_DIALOG_ATLAS_SIZE);

        int yesX = dialogX + CONFIRM_DIALOG_YES_X;
        int noX = dialogX + CONFIRM_DIALOG_NO_X;
        int buttonY = dialogY + CONFIRM_DIALOG_BUTTON_Y;

        confirmDismissConfirmHovered = mouseX >= yesX && mouseX < yesX + CONFIRM_DIALOG_BUTTON_WIDTH
                && mouseY >= buttonY && mouseY < buttonY + CONFIRM_DIALOG_BUTTON_HEIGHT;
        confirmDismissCancelHovered = mouseX >= noX && mouseX < noX + CONFIRM_DIALOG_BUTTON_WIDTH
                && mouseY >= buttonY && mouseY < buttonY + CONFIRM_DIALOG_BUTTON_HEIGHT;

        graphics.blit(CONFIRM_DIALOG_TEXTURE, yesX, buttonY, CONFIRM_DIALOG_YES_X, CONFIRM_DIALOG_BUTTON_Y,
                CONFIRM_DIALOG_BUTTON_WIDTH, CONFIRM_DIALOG_BUTTON_HEIGHT,
                CONFIRM_DIALOG_ATLAS_SIZE, CONFIRM_DIALOG_ATLAS_SIZE);
        graphics.blit(CONFIRM_DIALOG_TEXTURE, noX, buttonY, CONFIRM_DIALOG_NO_X, CONFIRM_DIALOG_BUTTON_Y,
                CONFIRM_DIALOG_BUTTON_WIDTH, CONFIRM_DIALOG_BUTTON_HEIGHT,
                CONFIRM_DIALOG_ATLAS_SIZE, CONFIRM_DIALOG_ATLAS_SIZE);

        if (confirmDismissConfirmHovered) {
            graphics.blit(CONFIRM_DIALOG_TEXTURE, yesX, buttonY, CONFIRM_DIALOG_HOVER_U, CONFIRM_DIALOG_HOVER_V,
                    CONFIRM_DIALOG_BUTTON_WIDTH, CONFIRM_DIALOG_BUTTON_HEIGHT,
                    CONFIRM_DIALOG_ATLAS_SIZE, CONFIRM_DIALOG_ATLAS_SIZE);
        }
        if (confirmDismissCancelHovered) {
            graphics.blit(CONFIRM_DIALOG_TEXTURE, noX, buttonY, CONFIRM_DIALOG_HOVER_U, CONFIRM_DIALOG_HOVER_V,
                    CONFIRM_DIALOG_BUTTON_WIDTH, CONFIRM_DIALOG_BUTTON_HEIGHT,
                    CONFIRM_DIALOG_ATLAS_SIZE, CONFIRM_DIALOG_ATLAS_SIZE);
        }

        Component message = Component.translatable("gui.qe_api.confirm_dismiss_message");
        drawWrappedCenteredText(graphics, message, dialogX + CONFIRM_DIALOG_TEXT_BOX_X,
                dialogY + CONFIRM_DIALOG_TEXT_BOX_Y, CONFIRM_DIALOG_TEXT_BOX_WIDTH,
                CONFIRM_DIALOG_TEXT_BOX_HEIGHT, 0x404040);

        Component confirmText = Component.translatable("gui.qe_api.confirm_dismiss");
        Component cancelText = Component.translatable("gui.qe_api.cancel_dismiss");
        graphics.drawString(font, confirmText,
                yesX + (CONFIRM_DIALOG_BUTTON_WIDTH - font.width(confirmText)) / 2,
                buttonY + (CONFIRM_DIALOG_BUTTON_HEIGHT - 8) / 2, 0x404040, false);
        graphics.drawString(font, cancelText,
                noX + (CONFIRM_DIALOG_BUTTON_WIDTH - font.width(cancelText)) / 2,
                buttonY + (CONFIRM_DIALOG_BUTTON_HEIGHT - 8) / 2, 0x404040, false);
    }

    // left-aligned wrap for a single task-list status line - returns the y to continue at, mirrored
    // by wrappedLineHeight for calculateInfoContentHeight
    private int drawWrappedLine(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private int wrappedLineHeight(Component text, int maxWidth) {
        return font.split(text, maxWidth).size() * 10;
    }

    // wraps text to boxWidth and centers the resulting block both ways within the box, so
    // translations longer or shorter than English still land centered instead of overflowing.
    private void drawWrappedCenteredText(GuiGraphics graphics, Component text, int boxX, int boxY,
                                          int boxWidth, int boxHeight, int color) {
        List<FormattedCharSequence> lines = font.split(text, boxWidth);
        int totalHeight = lines.size() * font.lineHeight;
        int startY = boxY + Math.max(0, (boxHeight - totalHeight) / 2);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int lineX = boxX + (boxWidth - font.width(line)) / 2;
            graphics.drawString(font, line, lineX, startY + i * font.lineHeight, color, false);
        }
    }

    // whether the player has actually accepted the selected quest - needed both to claim rewards and
    // to use the item-picker overlay, since picking a target item before accepting doesn't mean anything
    private boolean isSelectedQuestActive() {
        if (selectedQuest == null) return false;
        return questComponent.getActiveQuest(playerId)
                .map(data -> data.questId().equals(selectedQuest.id()))
                .orElse(false);
    }

    // true for a quest whose sole task is quest_line_choice - the root of a quest line, which never
    // goes through the normal accept/entityProgress pipeline (see QuestLineChoiceTask's javadoc)
    private static boolean isLineRootQuest(Quest quest) {
        return quest.tasks().size() == 1 && quest.tasks().get(0) instanceof QuestLineChoiceTask;
    }

    // the three states a quest_line_choice root's task row can be in - drives both
    // renderQuestInfo/calculateInfoContentHeight's branching for that row
    private enum LineRowState { NOT_ACCEPTED, PICKABLE, RESOLVED }

    // always called with a task belonging to selectedQuest, so selectedQuest.id() is the right root
    // to check acceptedRoots against
    private LineRowState lineRowState(QuestLineChoiceTask task) {
        if (!acceptedRoots.contains(selectedQuest.id())) return LineRowState.NOT_ACCEPTED;
        if (task.isResolved(resolvedLines)) return LineRowState.RESOLVED;
        return LineRowState.PICKABLE;
    }

    private boolean canClaimReward() {
        if (selectedQuest == null) return false;

        if (isLineRootQuest(selectedQuest)) {
            QuestLineChoiceTask lineChoiceTask = (QuestLineChoiceTask) selectedQuest.tasks().get(0);
            return lineChoiceTask.isResolved(resolvedLines) && !claimedRoots.contains(selectedQuest.id());
        }

        if (!isSelectedQuestActive() || !selectedQuest.isComplete(getProgressForQuest(selectedQuest))) {
            return false;
        }

        List<RewardChoicePool> pools = selectedQuest.rewardChoicePools();
        for (int i = 0; i < pools.size(); i++) {
            List<Integer> selected = selectedPoolChoices.getOrDefault(i, List.of());
            if (selected.size() != pools.get(i).pick()) {
                return false;
            }
        }

        // every TargetItemReward has to resolve to exactly one valid item, picked manually or the only
        // candidate available (see resolveTargetSlot). BringItemTask doesn't need this - unresolved just
        // falls back to greedy slot-order consumption.
        List<QuestReward> rewards = selectedQuest.rewards();
        for (int i = 0; i < rewards.size(); i++) {
            if (rewards.get(i) instanceof TargetItemReward targeted && resolveTargetSlot(i, targeted) < 0) {
                return false;
            }
        }

        return true;
    }

    // the slot a TargetItemReward gets claimed against: the manual pick if there is one, else the
    // sole valid candidate if exactly one exists, else -1 (ambiguous or none - player has to pick)
    private int resolveTargetSlot(int rewardIndex, TargetItemReward targeted) {
        Integer manual = selectedTargetSlots.get(rewardIndex);
        if (manual != null) return manual;
        if (minecraft.player == null || minecraft.level == null) return -1;

        List<ItemStack> items = minecraft.player.getInventory().items;
        int found = -1;
        for (int slot = 0; slot < items.size(); slot++) {
            if (targeted.isValidTarget(minecraft.level, items.get(slot))) {
                if (found != -1) return -1; // more than one candidate - must pick manually
                found = slot;
            }
        }
        return found;
    }

    // pick == 1 behaves like radio buttons (replaces selection); otherwise clicks beyond the
    // pick count are ignored until a slot is freed
    private void toggleRewardChoice(int poolIndex, int optionIndex) {
        if (selectedQuest == null || poolIndex >= selectedQuest.rewardChoicePools().size()) return;

        RewardChoicePool pool = selectedQuest.rewardChoicePools().get(poolIndex);
        List<Integer> selected = selectedPoolChoices.computeIfAbsent(poolIndex, k -> new ArrayList<>());

        if (selected.contains(optionIndex)) {
            selected.remove(Integer.valueOf(optionIndex));
        } else if (pool.pick() == 1) {
            selected.clear();
            selected.add(optionIndex);
        } else if (selected.size() < pool.pick()) {
            selected.add(optionIndex);
        }
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (HoverArea area : hoverAreas) {
            if (area.contains(mouseX, mouseY)) {
                graphics.renderTooltip(font, area.tooltip(), Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    // rotates through every matching entity type for a tag/multi-id EntityKillTask
    private EntityType<?> getDisplayEntityForTask(int taskIndex, EntityKillTask killTask) {
        if (!taskEntityLists.containsKey(taskIndex)) {
            List<EntityType<?>> entities = new ArrayList<>();

            if (killTask.entityId().isPresent()) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(killTask.entityId().get());
                if (type != null) {
                    entities.add(type);
                }
            }

            if (!killTask.entityIds().isEmpty()) {
                for (ResourceLocation id : killTask.entityIds()) {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
                    if (type != null && !entities.contains(type)) {
                        entities.add(type);
                    }
                }
            }

            if (killTask.entityTag().isPresent()) {
                var tagKey = killTask.entityTag().get();
                var tagOptional = BuiltInRegistries.ENTITY_TYPE.getTag(tagKey);
                if (tagOptional.isPresent()) {
                    for (var holder : tagOptional.get()) {
                        EntityType<?> type = holder.value();
                        if (!entities.contains(type)) {
                            entities.add(type);
                        }
                    }
                }
            }

            taskEntityLists.put(taskIndex, entities);
            taskEntityIndices.put(taskIndex, 0);
        }

        List<EntityType<?>> entities = taskEntityLists.get(taskIndex);
        if (entities.isEmpty()) {
            return null;
        }

        int currentIndex = taskEntityIndices.getOrDefault(taskIndex, 0);
        return entities.get(currentIndex % entities.size());
    }

    // same rotation idea as getDisplayEntityForTask, but for a spell_pool filter's spells
    private ResourceLocation getDisplaySpellForPool(int taskIndex, ResourceLocation poolId) {
        if (!taskSpellLists.containsKey(taskIndex)) {
            List<ResourceLocation> spells = new ArrayList<>();
            if (minecraft.level != null) {
                spells.addAll(SpellEngineClientCompat.spellsInPool(minecraft.level, poolId));
            }
            taskSpellLists.put(taskIndex, spells);
            taskSpellIndices.put(taskIndex, 0);
        }

        List<ResourceLocation> spells = taskSpellLists.get(taskIndex);
        if (spells.isEmpty()) {
            return null;
        }

        int currentIndex = taskSpellIndices.getOrDefault(taskIndex, 0);
        return spells.get(currentIndex % spells.size());
    }

    // same rotation idea as getDisplaySpellForPool, but for apply_status_effect's effect_ids list -
    // effect_id (or a single-entry effect_ids) is just that one effect, no rotation needed
    private ResourceLocation getDisplayEffectForTask(int taskIndex, ApplyStatusEffectTask effectTask) {
        if (!taskEffectLists.containsKey(taskIndex)) {
            List<ResourceLocation> effects = new ArrayList<>();
            effectTask.effectId().ifPresent(effects::add);
            for (ResourceLocation id : effectTask.effectIds()) {
                if (!effects.contains(id)) {
                    effects.add(id);
                }
            }
            taskEffectLists.put(taskIndex, effects);
            taskEffectIndices.put(taskIndex, 0);
        }

        List<ResourceLocation> effects = taskEffectLists.get(taskIndex);
        if (effects.isEmpty()) {
            return null;
        }

        int currentIndex = taskEffectIndices.getOrDefault(taskIndex, 0);
        return effects.get(currentIndex % effects.size());
    }

    // returns false without rendering when no override is set, so callers fall through to that
    // type's own icon logic - see QuestTask.textureOverrideId for the override-priority contract
    private boolean renderTextureOverride(GuiGraphics graphics, Optional<ResourceLocation> override, int x, int y, boolean complete) {
        if (override.isEmpty()) return false;
        graphics.blit(override.get(), x, y, 0, 0, 16, 16, 16, 16);
        if (complete) renderCompletionBadge(graphics, x, y);
        return true;
    }

    // green checkmark in the top-right corner of a 16x16 icon at (iconX, iconY) - Z-translated to
    // 200 like the power-level badge, since GUI icons depth-test and a plain blit() would render behind it
    private void renderCompletionBadge(GuiGraphics graphics, int iconX, int iconY) {
        float scale = (float) COMPLETION_BADGE_SIZE / CHECKMARK_SOURCE_SIZE;
        graphics.pose().pushPose();
        graphics.pose().translate(iconX + 16 - COMPLETION_BADGE_SIZE, iconY, 200);
        graphics.pose().scale(scale, scale, 1f);
        graphics.blit(GREEN_CHECKMARK_TEXTURE, 0, 0, 0, 0,
                CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE, CHECKMARK_SOURCE_SIZE);
        graphics.pose().popPose();
    }

    private void renderEntityInGui(GuiGraphics graphics, int x, int y, int size, Entity entity) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 50);
        float scale = size / Math.max(entity.getBbWidth(), entity.getBbHeight());
        poseStack.scale(scale, -scale, scale);

        // Stabilize entity rotation to prevent head shaking
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = 0;
            living.yBodyRotO = 0;
            living.yHeadRot = 0;
            living.yHeadRotO = 0;
            living.setYRot(0);
            living.setXRot(0);
        }

        var dispatcher = minecraft.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        dispatcher.render(entity, 0, 0, 0, 0, 1.0f, poseStack, graphics.bufferSource(), 0xF000F0);
        dispatcher.setRenderShadow(true);
        graphics.flush();

        poseStack.popPose();
    }

    private QuestProgress getProgressForQuest(Quest quest) {
        if (currentProgress != null && currentProgress.getQuestId().equals(quest.id())) {
            return currentProgress;
        }
        return new QuestProgress(quest.id());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Modal while the confirm-dismiss overlay is open - takes priority over the item-picker
            // modal below too, since the checkbox that opened it is only ever reachable when the
            // picker isn't already open.
            if (confirmDismissOpen) {
                int dialogX = guiLeft + (guiWidth - CONFIRM_DIALOG_WIDTH) / 2;
                int dialogY = guiTop + (GUI_HEIGHT - CONFIRM_DIALOG_HEIGHT) / 2;
                int buttonY = dialogY + CONFIRM_DIALOG_BUTTON_Y;
                int confirmX = dialogX + CONFIRM_DIALOG_YES_X;
                int cancelX = dialogX + CONFIRM_DIALOG_NO_X;

                if (mouseX >= confirmX && mouseX < confirmX + CONFIRM_DIALOG_BUTTON_WIDTH
                        && mouseY >= buttonY && mouseY < buttonY + CONFIRM_DIALOG_BUTTON_HEIGHT) {
                    confirmDismissOpen = false;
                    if (confirmDismissAction != null) {
                        confirmDismissAction.run();
                    }
                    return true;
                }
                if (mouseX >= cancelX && mouseX < cancelX + CONFIRM_DIALOG_BUTTON_WIDTH
                        && mouseY >= buttonY && mouseY < buttonY + CONFIRM_DIALOG_BUTTON_HEIGHT) {
                    confirmDismissOpen = false;
                    return true;
                }
                return true; // swallow clicks elsewhere while the dialog is open
            }

            // Modal while the item-picker overlay is open - only picker clicks are handled, so a
            // stale reward-choice/quest-switch/claim click can't slip through mid-pick.
            if (activePickerRewardIndex != null || activePickerTaskIndex != null) {
                if (pickerBackButtonBounds != null) {
                    int[] b = pickerBackButtonBounds;
                    if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                        activePickerRewardIndex = null;
                        activePickerTaskIndex = null;
                        infoScrollOffset = descriptionScrollOffset;
                        return true;
                    }
                }
                for (PickerCandidateArea area : pickerCandidateAreas) {
                    if (area.contains((int) mouseX, (int) mouseY)) {
                        if (activePickerRewardIndex != null) {
                            // single-select - picking a target item closes the overlay
                            selectedTargetSlots.put(activePickerRewardIndex, area.inventorySlot());
                            activePickerRewardIndex = null;
                            infoScrollOffset = descriptionScrollOffset;
                        } else {
                            // multi-select (a BringItemTask can need more than one stack) - stays
                            // open until the player hits Back
                            List<Integer> slots = selectedBringItemSlots.computeIfAbsent(activePickerTaskIndex, k -> new ArrayList<>());
                            if (slots.contains(area.inventorySlot())) {
                                slots.remove(Integer.valueOf(area.inventorySlot()));
                            } else {
                                slots.add(area.inventorySlot());
                            }
                        }
                        return true;
                    }
                }

                // Picker grid scrollbar - reuses infoScrollOffset/maxInfoScroll/isDraggingInfoScrollbar,
                // since the picker and the normal info content are never shown at the same time.
                if (maxInfoScroll > 0) {
                    int scrollX = guiLeft + infoScrollbarX;
                    if (mouseX >= scrollX && mouseX < scrollX + INFO_SCROLLBAR_WIDTH
                            && mouseY >= guiTop + INFO_BOX_Y && mouseY < guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                        isDraggingInfoScrollbar = true;
                        dragTrackY = pickerGridY;
                        dragTrackHeight = pickerGridVisibleHeight;
                        updateInfoScrollFromMouse(mouseY, dragTrackY, dragTrackHeight);
                        return true;
                    }
                }

                return true; // swallow clicks elsewhere in the overlay
            }

            for (int i = 0; i < VISIBLE_QUESTS; i++) {
                int questIndex = questScrollOffset + i;
                if (questIndex >= availableQuests.size()) continue;

                int slotY = guiTop + QUEST_LIST_Y + (i * CHECKBOX_SIZE);
                int checkboxX = guiLeft + QUEST_LIST_X;

                if (mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_SIZE
                        && mouseY >= slotY && mouseY < slotY + CHECKBOX_SIZE) {

                    Quest quest = availableQuests.get(questIndex);
                    if (isQuestLocked(quest)) {
                        return true;
                    }
                    boolean isCompleted = questComponent.hasCompletedQuest(playerId, quest.id());

                    if (!isCompleted) {
                        if (isLineRootQuest(quest)) {
                            // requires the same accept step as any other quest before its line
                            // picker becomes usable. Once accepted-and-not-yet-claimed, re-clicking
                            // opens the same confirm-dismiss dialog a line's own icon uses, this
                            // time un-accepting the whole root. Once claimed, there's nothing left
                            // to dismiss.
                            if (!acceptedRoots.contains(quest.id())) {
                                sendAcceptPacket(quest.id());
                            } else if (!claimedRoots.contains(quest.id())) {
                                ResourceLocation rootId = quest.id();
                                confirmDismissOpen = true;
                                confirmDismissAction = () -> ClientPacketSender.sendDismissQuestLineRoot(entityId, rootId);
                            }
                        } else {
                            boolean isActive = questComponent.getActiveQuest(playerId)
                                    .map(data -> data.questId().equals(quest.id()))
                                    .orElse(false);

                            if (isActive) {
                                confirmDismissOpen = true;
                                confirmDismissAction = this::sendDismissPacket;
                            } else if (!questComponent.hasActiveQuest(playerId)) {
                                sendAcceptPacket(quest.id());
                            }
                        }
                    }
                    return true;
                }

                int nameBoxX = guiLeft + 28;
                if (mouseX >= nameBoxX && mouseX < nameBoxX + questBoxWidth
                        && mouseY >= slotY && mouseY < slotY + QUEST_BOX_HEIGHT) {
                    selectedQuestIndex = questIndex;
                    selectedQuest = availableQuests.get(questIndex);
                    infoScrollOffset = 0;
                    return true;
                }
            }

            for (ChoiceArea area : choiceAreas) {
                if (area.contains((int) mouseX, (int) mouseY)) {
                    toggleRewardChoice(area.poolIndex(), area.optionIndex());
                    return true;
                }
            }

            for (LineOptionArea area : lineOptionAreas) {
                if (area.contains((int) mouseX, (int) mouseY) && selectedQuest != null) {
                    if (activeLine.equals(Optional.of(area.lineId()))) {
                        ResourceLocation rootId = selectedQuest.id();
                        String lineId = area.lineId();
                        confirmDismissOpen = true;
                        confirmDismissAction = () -> ClientPacketSender.sendCancelQuestLine(entityId, rootId, lineId);
                    } else if (activeLine.isEmpty()) {
                        ClientPacketSender.sendChooseQuestLine(entityId, selectedQuest.id(), area.lineId());
                    }
                    return true;
                }
            }

            for (PickerOpenArea area : pickerOpenAreas) {
                if (area.contains((int) mouseX, (int) mouseY)) {
                    if (area.isReward()) {
                        activePickerRewardIndex = area.index();
                    } else {
                        activePickerTaskIndex = area.index();
                    }
                    descriptionScrollOffset = infoScrollOffset;
                    infoScrollOffset = 0; // fresh picker context - don't inherit a stale scroll position
                    return true;
                }
            }

            int buttonX = guiLeft + CLAIM_BUTTON_X;
            int buttonY = guiTop + CLAIM_BUTTON_Y;
            if (mouseX >= buttonX && mouseX < buttonX + CLAIM_BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY < buttonY + CLAIM_BUTTON_HEIGHT) {
                if (canClaimReward()) {
                    sendClaimPacket();
                    return true;
                }
            }

            if (openedFromMerchant) {
                int tradeX = guiLeft + tradeButtonX;
                int tradeY = guiTop + TRADE_BUTTON_Y;
                if (mouseX >= tradeX && mouseX < tradeX + TRADE_BUTTON_SIZE
                        && mouseY >= tradeY && mouseY < tradeY + TRADE_BUTTON_SIZE) {
                    ClientPacketSender.sendRequestMerchantMenu(entityId);
                    return true;
                }
            }

            if (availableQuests.size() > VISIBLE_QUESTS) {
                int scrollX = guiLeft + questScrollbarX;
                int scrollY = guiTop + QUEST_SCROLLBAR_Y;
                if (mouseX >= scrollX && mouseX < scrollX + QUEST_SCROLLBAR_WIDTH
                        && mouseY >= scrollY && mouseY < scrollY + QUEST_SCROLLBAR_HEIGHT) {
                    isDraggingQuestScrollbar = true;
                    updateQuestScrollFromMouse(mouseY);
                    return true;
                }
            }

            if (maxInfoScroll > 0) {
                int scrollX = guiLeft + infoScrollbarX;
                int scrollY = guiTop + INFO_SCROLLBAR_Y;
                if (mouseX >= scrollX && mouseX < scrollX + INFO_SCROLLBAR_WIDTH
                        && mouseY >= scrollY && mouseY < scrollY + INFO_SCROLLBAR_HEIGHT) {
                    isDraggingInfoScrollbar = true;
                    dragTrackY = scrollY;
                    dragTrackHeight = INFO_SCROLLBAR_HEIGHT;
                    updateInfoScrollFromMouse(mouseY, dragTrackY, dragTrackHeight);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingQuestScrollbar = false;
            isDraggingInfoScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            if (isDraggingQuestScrollbar) {
                updateQuestScrollFromMouse(mouseY);
                return true;
            }
            if (isDraggingInfoScrollbar) {
                updateInfoScrollFromMouse(mouseY, dragTrackY, dragTrackHeight);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateQuestScrollFromMouse(double mouseY) {
        int maxScroll = Math.max(0, availableQuests.size() - VISIBLE_QUESTS);
        if (maxScroll <= 0) return;

        int scrollY = guiTop + QUEST_SCROLLBAR_Y;
        int trackHeight = QUEST_SCROLLBAR_HEIGHT - SCROLLBAR_THUMB_HEIGHT;

        float percent = (float) (mouseY - scrollY - SCROLLBAR_THUMB_HEIGHT / 2) / trackHeight;
        percent = Math.max(0, Math.min(1, percent));

        questScrollOffset = Math.round(percent * maxScroll);
    }

    // trackY/trackHeight: either INFO_SCROLLBAR_Y/HEIGHT for the normal info content, or the picker
    // grid's own bounds when reused for the item-picker overlay - safe to share since the two never show at once
    private void updateInfoScrollFromMouse(double mouseY, int trackY, int trackHeight) {
        if (maxInfoScroll <= 0) return;

        int scrollY = trackY;
        int trackHeightPx = trackHeight - SCROLLBAR_THUMB_HEIGHT;

        float percent = (float) (mouseY - scrollY - SCROLLBAR_THUMB_HEIGHT / 2) / trackHeightPx;
        percent = Math.max(0, Math.min(1, percent));

        infoScrollOffset = Math.round(percent * maxInfoScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= guiLeft + QUEST_LIST_X && mouseX < guiLeft + QUEST_LIST_X + questBoxWidth + CHECKBOX_SIZE + 20
                && mouseY >= guiTop + QUEST_LIST_Y && mouseY < guiTop + QUEST_LIST_Y + (VISIBLE_QUESTS * CHECKBOX_SIZE)) {
            int maxScroll = Math.max(0, availableQuests.size() - VISIBLE_QUESTS);
            questScrollOffset -= (int) scrollY;
            questScrollOffset = Math.max(0, Math.min(questScrollOffset, maxScroll));
            return true;
        }

        if (mouseX >= guiLeft + INFO_BOX_X && mouseX < guiLeft + INFO_BOX_X + infoBoxWidth
                && mouseY >= guiTop + INFO_BOX_Y && mouseY < guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
            infoScrollOffset -= (int) (scrollY * 16);
            infoScrollOffset = Math.max(0, Math.min(infoScrollOffset, maxInfoScroll));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void sendAcceptPacket(ResourceLocation questId) {
        QuestEntityAPI.LOGGER.debug("Sending accept quest packet: {}", questId);
        ClientPacketSender.sendAcceptQuest(entityId, questId);
    }

    private void sendDismissPacket() {
        QuestEntityAPI.LOGGER.debug("Sending dismiss quest packet");
        ClientPacketSender.sendDismissQuest(entityId);
    }

    private void sendClaimPacket() {
        if (selectedQuest != null && isLineRootQuest(selectedQuest)) {
            QuestEntityAPI.LOGGER.debug("Sending claim quest line root packet");
            ClientPacketSender.sendClaimQuestLineRoot(entityId, selectedQuest.id());
            return;
        }

        QuestEntityAPI.LOGGER.debug("Sending claim rewards packet");
        List<List<Integer>> poolChoices = new ArrayList<>();
        List<Integer> rewardTargetSlots = new ArrayList<>();
        List<List<Integer>> bringItemSlots = new ArrayList<>();
        if (selectedQuest != null) {
            for (int i = 0; i < selectedQuest.rewardChoicePools().size(); i++) {
                poolChoices.add(List.copyOf(selectedPoolChoices.getOrDefault(i, List.of())));
            }
            List<QuestReward> rewards = selectedQuest.rewards();
            for (int i = 0; i < rewards.size(); i++) {
                if (rewards.get(i) instanceof TargetItemReward targeted) {
                    rewardTargetSlots.add(resolveTargetSlot(i, targeted));
                } else {
                    rewardTargetSlots.add(-1);
                }
            }
            for (int i = 0; i < selectedQuest.tasks().size(); i++) {
                bringItemSlots.add(List.copyOf(selectedBringItemSlots.getOrDefault(i, List.of())));
            }
        }
        ClientPacketSender.sendClaimRewards(entityId, poolChoices, rewardTargetSlots, bringItemSlots);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
