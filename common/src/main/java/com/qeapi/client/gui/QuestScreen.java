package com.qeapi.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qeapi.QuestEntityAPI;
import com.qeapi.client.ClientQuestCache;
import com.qeapi.component.EntityQuestComponent;
import com.qeapi.network.ClientPacketSender;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestProgress;
import com.qeapi.quest.requirement.QuestRequirement;
import com.qeapi.quest.reward.ExperienceReward;
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
import com.qeapi.compat.ModCompatUtil;
import com.qeapi.client.compat.SpellEngineClientCompat;
import com.qeapi.quest.task.BlocksTraveledTask;
import com.qeapi.quest.task.BrewPotionTask;
import com.qeapi.quest.task.BringItemTask;
import com.qeapi.quest.task.MineBlockTask;
import com.qeapi.quest.task.SpellCastTask;
import com.qeapi.quest.task.EntityKillTask;
import com.qeapi.quest.task.FindStructureTask;
import com.qeapi.quest.task.ItemUsedTask;
import com.qeapi.quest.task.QuestTask;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final int SCROLL_DELAY_TICKS = 30;
    private static final int SCROLL_SPEED_TICKS = 3;
    private static final int SCROLL_PAUSE_TICKS = 40;

    private final int entityId;
    private final List<Quest> availableQuests;
    private final EntityQuestComponent questComponent;
    private final UUID playerId;

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

    private int textScrollTicks = 0;
    private int textScrollOffset = 0;
    private boolean textScrollPaused = false;
    private int lastSelectedIndex = -1;

    private final List<HoverArea> hoverAreas = new ArrayList<>();

    private final Map<Integer, List<Integer>> selectedPoolChoices = new HashMap<>();
    private final List<ChoiceArea> choiceAreas = new ArrayList<>();

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

    // some reward functions (Dungeon Difficulty's SetPowerLevelFunction) roll randomized attribute
    // values on every call, so this caches the display stack per quest selection instead of re-rolling
    // (and flickering) every frame
    private final Map<QuestReward, ItemStack> rewardDisplayItemCache = new HashMap<>();

    private int lootTableRotationTicks = 0;
    private final Map<Integer, Integer> rewardItemIndices = new HashMap<>();
    private final Map<Integer, List<ItemStack>> rewardItemLists = new HashMap<>();

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

    public QuestScreen(int entityId, List<Quest> quests, EntityQuestComponent component, UUID playerId) {
        super(Component.translatable("gui.qe_api.quest_screen.title"));
        this.entityId = entityId;
        this.availableQuests = quests;
        this.questComponent = component;
        this.playerId = playerId;

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
            rewardItemIndices.clear();
            rewardItemLists.clear();
            rewardDisplayItemCache.clear();
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
                boolean isActive = questComponent.getActiveQuest(playerId)
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

        List<FormattedCharSequence> descLines = font.split(selectedQuest.getDescription(), infoWidth);
        for (var line : descLines) {
            graphics.drawString(font, line, infoX, currentY, 0x606060, false);
            currentY += 10;
        }
        currentY += 6;

        if (!selectedQuest.requirements().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.qe_api.requirements")
                    .withStyle(ChatFormatting.UNDERLINE), infoX, currentY, 0xAA0000, false);
            currentY += 11;

            for (QuestRequirement requirement : selectedQuest.requirements()) {
                Component reqText = requirement.getDisplayText();
                boolean isMet = requirement.canCheckClientSide() && requirement.isMetClientSide(minecraft.player);

                int iconX = infoX;
                int textX = infoX;
                int textColor = isMet ? 0x00AA00 : 0x804040;

                if (requirement.getDisplayTexture().isPresent()) {
                    // always show the actual icon, with a green checkmark badge overlaid when met, rather than swapping it out
                    ResourceLocation texture = requirement.getDisplayTexture().get();
                    graphics.blit(texture, iconX, currentY - 2, 0, 0, 16, 16, 16, 16);
                    if (isMet) {
                        renderCompletionBadge(graphics, iconX, currentY - 2);
                    }
                    textX = iconX + 18;

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
            int color = complete ? 0x00AA00 : 0x404040;

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

            if (task instanceof EntityKillTask killTask && minecraft.level != null) {
                EntityType<?> entityType = getDisplayEntityForTask(i, killTask);

                if (entityType != null) {
                    try {
                        Entity entity = entityType.create(minecraft.level);
                        if (entity != null) {
                            renderEntityInGui(graphics, iconX + 8, currentY + 14, 14, entity);
                            if (complete) {
                                renderCompletionBadge(graphics, iconX, currentY);
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
                ItemStack itemStack = new ItemStack(BuiltInRegistries.ITEM.get(bringTask.itemId()));
                if (!itemStack.isEmpty()) {
                    int pickerIconX = iconX + SELECTION_ICON_X_OFFSET;
                    graphics.renderItem(itemStack, pickerIconX - 2, currentY);
                    if (complete) {
                        renderCompletionBadge(graphics, pickerIconX - 2, currentY);
                    }
                    textX = iconX + 16 + SELECTION_TEXT_X_OFFSET;

                    if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                        hoverAreas.add(new HoverArea(pickerIconX - 2, currentY, 16, 16,
                                Screen.getTooltipFromItem(minecraft, itemStack)));
                    }

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
                    graphics.renderItem(itemStack, iconX - 2, currentY);
                    if (complete) {
                        renderCompletionBadge(graphics, iconX - 2, currentY);
                    }
                    textX = iconX + 16;

                    if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                        hoverAreas.add(new HoverArea(iconX - 2, currentY, 16, 16,
                                Screen.getTooltipFromItem(minecraft, itemStack)));
                    }
                }
            } else if (task instanceof BrewPotionTask brewTask) {
                ItemStack potionStack = brewTask.getDisplayStack();
                if (!potionStack.isEmpty()) {
                    graphics.renderItem(potionStack, iconX - 2, currentY);
                    if (complete) {
                        renderCompletionBadge(graphics, iconX - 2, currentY);
                    }
                    textX = iconX + 16;

                    if (currentY >= guiTop + INFO_BOX_Y && currentY + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                        hoverAreas.add(new HoverArea(iconX - 2, currentY, 16, 16,
                                Screen.getTooltipFromItem(minecraft, potionStack)));
                    }
                }
            } else if (task instanceof MineBlockTask) {
                // generic icon, not the specific block being mined - same idea as the other action-counting tasks
                graphics.renderItem(new ItemStack(Items.IRON_PICKAXE), iconX - 2, currentY);
                if (complete) {
                    renderCompletionBadge(graphics, iconX - 2, currentY);
                }
                textX = iconX + 16;
            } else if (task instanceof SpellCastTask spellCastTask && SpellEngineClientCompat.isLoaded()) {
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

    private int calculateInfoContentHeight() {
        if (selectedQuest == null) return 0;

        int height = 0;

        List<FormattedCharSequence> nameLines = font.split(
                selectedQuest.getDisplayName().copy().withStyle(ChatFormatting.BOLD), infoBoxWidth - 8);
        height += nameLines.size() * 10 + 4;

        List<FormattedCharSequence> descLines = font.split(selectedQuest.getDescription(), infoBoxWidth - 8);
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
            if (task instanceof EntityKillTask ekt && ekt.inSpellId().isPresent() && SpellEngineClientCompat.isLoaded()) {
                textX = 36; // entity icon + spell icon side by side
            } else if (task instanceof BringItemTask) {
                textX = 18 + SELECTION_TEXT_X_OFFSET; // selection.png-bordered icon, shifted right
            } else if (task instanceof EntityKillTask || task instanceof ItemUsedTask
                    || task instanceof BrewPotionTask || task instanceof MineBlockTask
                    || (task instanceof SpellCastTask sct && sct.spellId().isPresent() && SpellEngineClientCompat.isLoaded())
                    || task.getDisplayTexture().isPresent()) {
                textX = 18;
            }

            int availableWidth = (infoBoxWidth - 8) - textX;
            List<FormattedCharSequence> taskLines = font.split(Component.literal(taskStr), availableWidth);
            height += 18 + (taskLines.size() - 1) * 10;
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
            height += pool.options().size() * 18;
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

        } else if (reward instanceof TargetItemReward targeted) {
            return renderTargetItemReward(graphics, reward, targeted, rewardIndex, x, y, maxWidth);
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
        } else if (reward instanceof SkillExperienceReward || reward instanceof SkillLevelReward) {
            Optional<ResourceLocation> icon = reward instanceof SkillExperienceReward skillExp
                    ? skillExp.icon() : ((SkillLevelReward) reward).icon();
            return renderSkillTreeReward(graphics, icon, reward.getDisplayText().getString(), x, y, maxWidth);
        } else if (reward instanceof LevelZSkillLevelReward levelZSkillLevel) {
            Optional<ResourceLocation> icon = LevelZCompat.isLoaded()
                    ? Optional.of(LevelZCompat.skillIcon(levelZSkillLevel.skillId())) : Optional.empty();
            return renderSkillTreeReward(graphics, icon, reward.getDisplayText().getString(), x, y, maxWidth);
        } else if (reward instanceof SetQuestGroupReward questGroupReward) {
            return renderSkillTreeReward(graphics, Optional.of(questGroupReward.icon()), reward.getDisplayText().getString(), x, y, maxWidth);
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
                    && pool.options().stream().allMatch(option -> option instanceof SetQuestGroupReward);
            Component header = isPathChoice
                    ? Component.translatable("gui.qe_api.choose_quest_path")
                    : Component.translatable("gui.qe_api.choose_rewards",
                            selected.size(), pool.pick(), pool.options().size());

            // path-choice's selection border is wider than its 16x16 icon (SELECTION_INSET), so it
            // clips past the info box's left edge at x - shift the whole section right to clear it,
            // same fix as renderTargetItemReward
            int sectionX = isPathChoice ? x + SELECTION_ICON_X_OFFSET : x;
            int sectionMaxWidth = isPathChoice ? maxWidth - SELECTION_ICON_X_OFFSET : maxWidth;

            graphics.drawString(font, header, sectionX, currentY, 0x000000, false);
            currentY += 12;

            for (int optionIndex = 0; optionIndex < pool.options().size(); optionIndex++) {
                QuestReward option = pool.options().get(optionIndex);
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

        String formattedName = formatLootTableName(lootReward.lootTableId());
        String rewardStr = Component.translatable("reward.qe_api.loot_table_formatted", formattedName).getString();
        List<FormattedCharSequence> rewardLines = font.split(Component.literal(rewardStr), maxWidth - 20);

        for (int lineIdx = 0; lineIdx < rewardLines.size(); lineIdx++) {
            graphics.drawString(font, rewardLines.get(lineIdx), x + 18, y + 4 + (lineIdx * 10), 0x404040, false);
        }

        return y + 18;
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
            graphics.renderItem(PICKER_PLACEHOLDER_STACK, pickerIconX - 2, y); // signals "click to pick an item"
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
        return reward.getDisplayItem().isPresent()
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

    // whether the player has actually accepted the selected quest - needed both to claim rewards and
    // to use the item-picker overlay, since picking a target item before accepting doesn't mean anything
    private boolean isSelectedQuestActive() {
        if (selectedQuest == null) return false;
        return questComponent.getActiveQuest(playerId)
                .map(data -> data.questId().equals(selectedQuest.id()))
                .orElse(false);
    }

    private boolean canClaimReward() {
        if (selectedQuest == null) return false;

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
                    boolean isCompleted = questComponent.hasCompletedQuest(playerId, quest.id());

                    if (!isCompleted) {
                        boolean isActive = questComponent.getActiveQuest(playerId)
                                .map(data -> data.questId().equals(quest.id()))
                                .orElse(false);

                        if (isActive) {
                            sendDismissPacket();
                        } else if (!questComponent.hasActiveQuest(playerId)) {
                            sendAcceptPacket(quest.id());
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
