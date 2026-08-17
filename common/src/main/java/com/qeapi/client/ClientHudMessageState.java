package com.qeapi.client;

import net.minecraft.network.chat.Component;

// Single-slot HUD toast (see ShowHudMessagePacket/QuestHudMessageRenderer) - a new message
// always replaces whatever's showing, so at most one is ever visible at once.
public final class ClientHudMessageState {

    private ClientHudMessageState() {}

    private static final int VISIBLE_TICKS = 60;
    private static final int FADE_TICKS = 20;

    private static Component message;
    private static int ticksLeft;

    public static void show(Component newMessage) {
        message = newMessage;
        ticksLeft = VISIBLE_TICKS + FADE_TICKS;
    }

    public static void tick() {
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    public static Component getMessage() {
        return ticksLeft > 0 ? message : null;
    }

    public static float getAlpha() {
        if (ticksLeft <= 0) return 0.0F;
        if (ticksLeft > FADE_TICKS) return 1.0F;
        return ticksLeft / (float) FADE_TICKS;
    }

    public static void clear() {
        message = null;
        ticksLeft = 0;
    }
}
