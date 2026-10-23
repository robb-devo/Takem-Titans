package com.takem.titans.item;

import net.minecraft.ChatFormatting;

public enum FlareColor {
    RED("red", 15022389, ChatFormatting.RED),
    GREEN("green", 4431943, ChatFormatting.GREEN),
    BLACK("black", 2500134, ChatFormatting.DARK_GRAY),
    YELLOW("yellow", 16635957, ChatFormatting.YELLOW),
    PURPLE("purple", 9315498, ChatFormatting.LIGHT_PURPLE);

    private final String id;
    public final int rgb;
    public final ChatFormatting chat;

    private FlareColor(String id, int rgb, ChatFormatting chat) {
        this.id = id;
        this.rgb = rgb;
        this.chat = chat;
    }

    public String id() {
        return this.id;
    }

    public static FlareColor byIndex(int i) {
        FlareColor[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
