package com.qeapi.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qeapi.QuestAPI;
import com.qeapi.network.packet.ActiveQuestEntry;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestProgress;
import com.qeapi.quest.reward.QuestReward;
import com.qeapi.quest.reward.RewardChoicePool;
import com.qeapi.quest.task.BringItemTask;
import com.qeapi.quest.task.QuestTask;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Read-only overview opened via keybind - see QuestKeybinds. Reuses QuestScreen's texture and
// list+detail layout conventions rather than inventing a new look, but drops the
// picker/reward-choice/claim affordances (nothing to accept or claim here) and renders reward/task
// icons with one generic renderer instead of QuestScreen's per-type special-casing.
public class ActiveQuestScreen extends Screen {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            QuestAPI.MOD_ID, "textures/gui/quests.png");

    private static final int GUI_WIDTH = 240;
    private static final int GUI_HEIGHT = 222;
    private static final int TEXTURE_SIZE = 256;

    private static final int LIST_X = 8;
    private static final int LIST_Y = 15;
    private static final int ROW_SIZE = 19;
    private static final int LIST_BOX_WIDTH = 190;
    private static final int VISIBLE_ROWS = 3;

    private static final int SCROLLBAR_RIGHT_OFFSET = 21;
    private static final int LIST_SCROLLBAR_Y = 14;
    private static final int LIST_SCROLLBAR_WIDTH = 14;
    private static final int LIST_SCROLLBAR_HEIGHT = 59;

    private static final int SCROLLBAR_THUMB_U = 244;
    private static final int SCROLLBAR_THUMB_V = 0;
    private static final int SCROLLBAR_THUMB_WIDTH = 12;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;

    private static final int INFO_BOX_X = 8;
    private static final int INFO_BOX_Y = 82;
    private static final int INFO_BOX_WIDTH = 210;
    private static final int INFO_BOX_HEIGHT = 109;

    private static final int INFO_SCROLLBAR_Y = 81;
    private static final int INFO_SCROLLBAR_WIDTH = 14;
    private static final int INFO_SCROLLBAR_HEIGHT = 110;

    // reuses the claim button's slot for the tier-filter cycle button - nothing to claim here
    private static final int FILTER_BUTTON_X = 43;
    private static final int FILTER_BUTTON_Y = 196;
    private static final int FILTER_BUTTON_WIDTH = 154;
    private static final int FILTER_BUTTON_HEIGHT = 19;

    private final List<ActiveQuestEntry> allEntries;
    private List<ActiveQuestEntry> visibleEntries;
    // 0 = no filter, otherwise the exact tier being filtered to
    private int tierFilter = 0;

    private int guiLeft;
    private int guiTop;

    private int listScrollOffset = 0;
    private int infoScrollOffset = 0;
    private int maxInfoScroll = 0;
    private boolean draggingListScrollbar = false;
    private boolean draggingInfoScrollbar = false;

    private int selectedIndex = -1;

    private record HoverArea(int x, int y, int width, int height, List<Component> tooltip) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final List<HoverArea> hoverAreas = new ArrayList<>();

    public ActiveQuestScreen(List<ActiveQuestEntry> entries) {
        super(Component.translatable("gui.quest_api.active_quests_screen.title"));
        this.allEntries = entries;
        this.visibleEntries = entries;
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (width - GUI_WIDTH) / 2;
        int minTop = 5;
        int maxTop = height - GUI_HEIGHT - 45;
        int centeredTop = (height - GUI_HEIGHT) / 2 - 10;
        guiTop = Math.max(minTop, Math.min(centeredTop, maxTop));

        if (!visibleEntries.isEmpty()) {
            selectedIndex = 0;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        hoverAreas.clear();

        graphics.blit(TEXTURE, guiLeft, guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.drawString(font, title, guiLeft + 8, guiTop + 4, 0x404040, false);

        renderList(graphics, mouseX, mouseY);
        renderFilterButton(graphics, mouseX, mouseY);

        if (selectedIndex >= 0 && selectedIndex < visibleEntries.size()) {
            renderDetail(graphics, visibleEntries.get(selectedIndex), mouseX, mouseY);
        } else {
            graphics.drawString(font, Component.translatable("gui.quest_api.no_active_quests"),
                    guiLeft + INFO_BOX_X + 4, guiTop + INFO_BOX_Y + 4, 0x606060, false);
        }

        renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int maxScroll = Math.max(0, visibleEntries.size() - VISIBLE_ROWS);
        listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = listScrollOffset + i;
            int slotY = guiTop + LIST_Y + (i * ROW_SIZE);
            int iconX = guiLeft + LIST_X;
            int nameBoxX = guiLeft + 28;

            if (index >= visibleEntries.size()) continue;
            ActiveQuestEntry entry = visibleEntries.get(index);

            if (selectedIndex == index) {
                graphics.fill(nameBoxX + 1, slotY + 1, nameBoxX + LIST_BOX_WIDTH - 1,
                        slotY + ROW_SIZE - 1, 0x80FFFFFF);
            }

            Entity previewEntity = createPreviewEntity(entry.location().entityType());
            if (previewEntity != null) {
                renderEntityInGui(graphics, iconX + 9, slotY + 17, 14, previewEntity);
            }

            String questName = entry.quest().getDisplayName(displayName(entry)).getString();
            int maxTextWidth = LIST_BOX_WIDTH - 6;
            String displayText = questName;
            if (font.width(questName) > maxTextWidth) {
                displayText = font.plainSubstrByWidth(questName, maxTextWidth - 6) + "...";
            }

            graphics.enableScissor(nameBoxX + 2, slotY + 1, nameBoxX + LIST_BOX_WIDTH - 2, slotY + ROW_SIZE - 1);
            graphics.drawString(font, displayText, nameBoxX + 3, slotY + 6, 0x000000, false);
            graphics.disableScissor();
        }

        if (visibleEntries.size() > VISIBLE_ROWS) {
            renderScrollbar(graphics, guiLeft + GUI_WIDTH - SCROLLBAR_RIGHT_OFFSET, guiTop + LIST_SCROLLBAR_Y,
                    LIST_SCROLLBAR_WIDTH, LIST_SCROLLBAR_HEIGHT, listScrollOffset, maxScroll);
        }
    }

    private void renderFilterButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonX = guiLeft + FILTER_BUTTON_X;
        int buttonY = guiTop + FILTER_BUTTON_Y;

        boolean hovered = mouseX >= buttonX && mouseX < buttonX + FILTER_BUTTON_WIDTH
                && mouseY >= buttonY && mouseY < buttonY + FILTER_BUTTON_HEIGHT;
        if (hovered) {
            graphics.fill(buttonX + 1, buttonY + 1, buttonX + FILTER_BUTTON_WIDTH - 1,
                    buttonY + FILTER_BUTTON_HEIGHT - 1, 0x40FFFFFF);
        }

        Component label = tierFilter == 0
                ? Component.translatable("gui.quest_api.filter_all_tiers")
                : Component.translatable("gui.quest_api.tier", tierFilter);
        int textWidth = font.width(label);
        graphics.drawString(font, label, buttonX + (FILTER_BUTTON_WIDTH - textWidth) / 2,
                buttonY + (FILTER_BUTTON_HEIGHT - 8) / 2, 0x404040, false);
    }

    private void cycleFilter() {
        List<Integer> tiers = allEntries.stream().map(e -> e.quest().tier()).distinct().sorted().toList();
        if (tiers.isEmpty()) return;

        if (tierFilter == 0) {
            tierFilter = tiers.get(0);
        } else {
            int idx = tiers.indexOf(tierFilter);
            tierFilter = (idx < 0 || idx == tiers.size() - 1) ? 0 : tiers.get(idx + 1);
        }

        visibleEntries = tierFilter == 0 ? allEntries
                : allEntries.stream().filter(e -> e.quest().tier() == tierFilter).toList();
        listScrollOffset = 0;
        selectedIndex = visibleEntries.isEmpty() ? -1 : 0;
    }

    private void renderDetail(GuiGraphics graphics, ActiveQuestEntry entry, int mouseX, int mouseY) {
        int infoX = guiLeft + INFO_BOX_X + 4;
        int infoY = guiTop + INFO_BOX_Y + 4;
        int infoWidth = INFO_BOX_WIDTH - 8;
        int infoHeight = INFO_BOX_HEIGHT - 8;

        int contentHeight = calculateDetailHeight(entry, infoWidth);
        maxInfoScroll = Math.max(0, contentHeight - infoHeight);
        infoScrollOffset = Math.max(0, Math.min(infoScrollOffset, maxInfoScroll));

        graphics.enableScissor(guiLeft + INFO_BOX_X + 1, guiTop + INFO_BOX_Y + 1,
                guiLeft + INFO_BOX_X + INFO_BOX_WIDTH - 1, guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT - 1);

        int currentY = infoY - infoScrollOffset;
        Quest quest = entry.quest();
        String entityName = displayName(entry);

        Entity previewEntity = createPreviewEntity(entry.location().entityType());
        int textX = infoX;
        if (previewEntity != null) {
            renderEntityInGui(graphics, infoX + 12, currentY + 20, 24, previewEntity);
            textX = infoX + 30;
        }

        List<FormattedCharSequence> nameLines = font.split(
                quest.getDisplayName(entityName).copy().withStyle(ChatFormatting.BOLD), infoWidth - (textX - infoX));
        int nameY = currentY;
        for (var line : nameLines) {
            graphics.drawString(font, line, textX, nameY, 0x000000, false);
            nameY += 10;
        }

        Component coordsText = entry.location().coordinatesKnown()
                ? Component.translatable("gui.quest_api.giver_coordinates",
                        entry.location().pos().getX(), entry.location().pos().getY(), entry.location().pos().getZ(),
                        formatDimension(entry.location().dimension()))
                : Component.translatable("gui.quest_api.giver_coordinates_unknown");
        graphics.drawString(font, font.split(coordsText, infoWidth - (textX - infoX)).get(0), textX, nameY, 0x606060, false);

        currentY += Math.max(nameLines.size() * 10 + 10, previewEntity != null ? 34 : 0);
        currentY += 6;

        List<FormattedCharSequence> descLines = font.split(quest.getDescription(entityName), infoWidth);
        for (var line : descLines) {
            graphics.drawString(font, line, infoX, currentY, 0x606060, false);
            currentY += 10;
        }
        currentY += 6;

        graphics.drawString(font, Component.translatable("gui.quest_api.tasks").withStyle(ChatFormatting.UNDERLINE),
                infoX, currentY, 0x000000, false);
        currentY += 12;

        QuestProgress progress = entry.progress();
        for (int i = 0; i < quest.tasks().size(); i++) {
            QuestTask task = quest.tasks().get(i);
            boolean complete = task.isComplete(progress, i);
            // BringItemTask completion is reversible (dropping the item un-completes it), so only
            // the color is neutralized here - the checkmark still reflects current carry status.
            int color = (complete && !(task instanceof BringItemTask)) ? 0x00AA00 : 0x404040;
            String prefix = complete ? "✓ " : "• ";

            List<FormattedCharSequence> taskLines = font.split(
                    Component.literal(prefix).append(task.getDisplayText(progress, i)), infoWidth);
            for (var line : taskLines) {
                graphics.drawString(font, line, infoX, currentY, color, false);
                currentY += 10;
            }
        }
        currentY += 6;

        graphics.drawString(font, Component.translatable("gui.quest_api.rewards").withStyle(ChatFormatting.UNDERLINE),
                infoX, currentY, 0x000000, false);
        currentY += 12;

        for (QuestReward reward : quest.rewards()) {
            currentY = renderRewardLine(graphics, reward, infoX, currentY, infoWidth, mouseX, mouseY);
        }

        for (RewardChoicePool pool : quest.rewardChoicePools()) {
            int availableCount = (int) pool.options().stream().filter(RewardChoicePool.Option::isAvailable).count();
            Component header = Component.translatable("gui.quest_api.choose_rewards",
                    pool.pick(), pool.pick(), availableCount);
            graphics.drawString(font, header, infoX, currentY, 0x000000, false);
            currentY += 12;
            for (RewardChoicePool.Option option : pool.options()) {
                if (!option.isAvailable()) continue;
                currentY = renderRewardLine(graphics, option.reward(), infoX, currentY, infoWidth, mouseX, mouseY);
            }
        }

        graphics.disableScissor();

        if (maxInfoScroll > 0) {
            renderScrollbar(graphics, guiLeft + GUI_WIDTH - SCROLLBAR_RIGHT_OFFSET, guiTop + INFO_SCROLLBAR_Y,
                    INFO_SCROLLBAR_WIDTH, INFO_SCROLLBAR_HEIGHT, infoScrollOffset, maxInfoScroll);
        }
    }

    private int renderRewardLine(GuiGraphics graphics, QuestReward reward, int x, int y, int maxWidth, int mouseX, int mouseY) {
        Optional<ItemStack> icon = reward.getDisplayItem();
        String rewardStr = reward.getDisplayText().getString();

        if (icon.isPresent() && !icon.get().isEmpty()) {
            graphics.renderItem(icon.get(), x - 2, y);
            if (y >= guiTop + INFO_BOX_Y && y + 16 <= guiTop + INFO_BOX_Y + INFO_BOX_HEIGHT) {
                hoverAreas.add(new HoverArea(x - 2, y, 16, 16, Screen.getTooltipFromItem(minecraft, icon.get())));
            }
            List<FormattedCharSequence> lines = font.split(Component.literal(rewardStr), maxWidth - 20);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), x + 18, y + 4 + i * 10, 0x404040, false);
            }
            return y + 4 + lines.size() * 10 + 4;
        }

        List<FormattedCharSequence> lines = font.split(Component.literal("• " + rewardStr), maxWidth);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x, y + 4 + i * 10, 0x404040, false);
        }
        return y + 4 + lines.size() * 10 + 4;
    }

    private int calculateDetailHeight(ActiveQuestEntry entry, int infoWidth) {
        Quest quest = entry.quest();
        int height = 44; // name + coordinates + entity icon row, fixed allowance
        height += font.split(quest.getDescription(displayName(entry)), infoWidth).size() * 10 + 6;
        height += 12;

        QuestProgress progress = entry.progress();
        for (int i = 0; i < quest.tasks().size(); i++) {
            QuestTask task = quest.tasks().get(i);
            height += font.split(Component.literal("• ").append(task.getDisplayText(progress, i)), infoWidth).size() * 10;
        }
        height += 6 + 12;

        for (QuestReward reward : quest.rewards()) {
            height += rewardLineHeight(reward, infoWidth);
        }
        for (RewardChoicePool pool : quest.rewardChoicePools()) {
            height += 12;
            for (RewardChoicePool.Option option : pool.options()) {
                if (!option.isAvailable()) continue;
                height += rewardLineHeight(option.reward(), infoWidth);
            }
        }
        return height;
    }

    private int rewardLineHeight(QuestReward reward, int infoWidth) {
        Optional<ItemStack> icon = reward.getDisplayItem();
        int width = (icon.isPresent() && !icon.get().isEmpty()) ? infoWidth - 20 : infoWidth;
        String prefix = (icon.isPresent() && !icon.get().isEmpty()) ? "" : "• ";
        return font.split(Component.literal(prefix + reward.getDisplayText().getString()), width).size() * 10 + 8;
    }

    private String displayName(ActiveQuestEntry entry) {
        String stored = entry.location().displayName();
        if (stored != null && !stored.isEmpty()) {
            return stored;
        }
        return Component.translatable(entry.location().entityType().toLanguageKey("entity")).getString();
    }

    private String formatDimension(ResourceLocation dimension) {
        return com.qeapi.util.TextFormatting.titleCaseWords(dimension.getPath());
    }

    private Entity createPreviewEntity(ResourceLocation entityTypeId) {
        if (minecraft == null || minecraft.level == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityTypeId);
        try {
            return type.create(minecraft.level);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderEntityInGui(GuiGraphics graphics, int x, int y, int size, Entity entity) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 50);
        float scale = size / Math.max(entity.getBbWidth(), entity.getBbHeight());
        poseStack.scale(scale, -scale, scale);

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

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int width, int height, int scrollOffset, int maxScroll) {
        if (maxScroll <= 0) return;
        int trackHeight = height - SCROLLBAR_THUMB_HEIGHT;
        float scrollPercent = (float) scrollOffset / maxScroll;
        int thumbY = y + (int) (scrollPercent * trackHeight);
        graphics.blit(TEXTURE, x, thumbY, SCROLLBAR_THUMB_U, SCROLLBAR_THUMB_V,
                SCROLLBAR_THUMB_WIDTH, SCROLLBAR_THUMB_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (HoverArea area : hoverAreas) {
            if (area.contains(mouseX, mouseY)) {
                graphics.renderComponentTooltip(font, area.tooltip(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int filterX = guiLeft + FILTER_BUTTON_X;
            int filterY = guiTop + FILTER_BUTTON_Y;
            if (mouseX >= filterX && mouseX < filterX + FILTER_BUTTON_WIDTH
                    && mouseY >= filterY && mouseY < filterY + FILTER_BUTTON_HEIGHT) {
                cycleFilter();
                return true;
            }

            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int index = listScrollOffset + i;
                if (index >= visibleEntries.size()) continue;
                int slotY = guiTop + LIST_Y + (i * ROW_SIZE);
                int nameBoxX = guiLeft + 28;
                if (mouseX >= nameBoxX && mouseX < nameBoxX + LIST_BOX_WIDTH
                        && mouseY >= slotY && mouseY < slotY + ROW_SIZE) {
                    selectedIndex = index;
                    infoScrollOffset = 0;
                    return true;
                }
            }

            int scrollbarX = guiLeft + GUI_WIDTH - SCROLLBAR_RIGHT_OFFSET;
            if (visibleEntries.size() > VISIBLE_ROWS
                    && mouseX >= scrollbarX && mouseX < scrollbarX + LIST_SCROLLBAR_WIDTH
                    && mouseY >= guiTop + LIST_SCROLLBAR_Y && mouseY < guiTop + LIST_SCROLLBAR_Y + LIST_SCROLLBAR_HEIGHT) {
                draggingListScrollbar = true;
                return true;
            }
            if (maxInfoScroll > 0
                    && mouseX >= scrollbarX && mouseX < scrollbarX + INFO_SCROLLBAR_WIDTH
                    && mouseY >= guiTop + INFO_SCROLLBAR_Y && mouseY < guiTop + INFO_SCROLLBAR_Y + INFO_SCROLLBAR_HEIGHT) {
                draggingInfoScrollbar = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingListScrollbar) {
            int maxScroll = Math.max(0, visibleEntries.size() - VISIBLE_ROWS);
            float percent = (float) (mouseY - (guiTop + LIST_SCROLLBAR_Y)) / (LIST_SCROLLBAR_HEIGHT - SCROLLBAR_THUMB_HEIGHT);
            listScrollOffset = Math.round(percent * maxScroll);
            listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));
            return true;
        }
        if (draggingInfoScrollbar) {
            float percent = (float) (mouseY - (guiTop + INFO_SCROLLBAR_Y)) / (INFO_SCROLLBAR_HEIGHT - SCROLLBAR_THUMB_HEIGHT);
            infoScrollOffset = Math.round(percent * maxInfoScroll);
            infoScrollOffset = Math.max(0, Math.min(infoScrollOffset, maxInfoScroll));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingListScrollbar = false;
        draggingInfoScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listX = guiLeft + LIST_X;
        if (mouseX >= listX && mouseX < listX + LIST_BOX_WIDTH + 20
                && mouseY >= guiTop + LIST_Y && mouseY < guiTop + LIST_Y + VISIBLE_ROWS * ROW_SIZE) {
            int maxScroll = Math.max(0, visibleEntries.size() - VISIBLE_ROWS);
            listScrollOffset = Math.max(0, Math.min(listScrollOffset - (int) Math.signum(scrollY), maxScroll));
            return true;
        }
        if (maxInfoScroll > 0) {
            infoScrollOffset = Math.max(0, Math.min(infoScrollOffset - (int) (Math.signum(scrollY) * 10), maxInfoScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
