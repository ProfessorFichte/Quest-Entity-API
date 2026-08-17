package com.qeapi.client.render;

import com.qeapi.client.ClientHudMessageState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;

// Same position/backdrop style vanilla uses for its own action-bar overlay message (Gui.renderOverlayMessage).
public final class QuestHudMessageRenderer {

    private QuestHudMessageRenderer() {}

    private static final int Y_ABOVE_HOTBAR = 68;

    public static void render(GuiGraphics graphics) {
        Component message = ClientHudMessageState.getMessage();
        if (message == null) return;

        Font font = Minecraft.getInstance().font;
        int width = font.width(message);
        int x = graphics.guiWidth() / 2 - width / 2;
        int y = graphics.guiHeight() - Y_ABOVE_HOTBAR;

        int alpha = (int) (ClientHudMessageState.getAlpha() * 255.0F);
        int color = FastColor.ARGB32.color(alpha, -1);

        graphics.drawStringWithBackdrop(font, message, x, y, width, color);
    }
}
