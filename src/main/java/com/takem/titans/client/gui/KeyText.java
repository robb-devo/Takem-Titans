package com.takem.titans.client.gui;

import com.takem.titans.client.OdmKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Fills key placeholders like [DASH] in manual and popup text with whatever the player actually has them bound to. */
public final class KeyText {
    private KeyText() {
    }

    public static String fill(String text) {
        if (text.indexOf('[') < 0) {
            return text;
        } else {
            Options o = Minecraft.getInstance().options;
            return text.replace("[L]", key(OdmKeys.HOOK_LEFT.getTranslatedKeyMessage().getString()))
                .replace("[R]", key(OdmKeys.HOOK_RIGHT.getTranslatedKeyMessage().getString()))
                .replace("[USE]", key(o.keyUse.getTranslatedKeyMessage().getString()))
                .replace("[ATTACK]", key(o.keyAttack.getTranslatedKeyMessage().getString()))
                .replace("[BOOST]", key(OdmKeys.label(OdmKeys.BOOST, o.keyJump)))
                .replace("[DASH]", key(OdmKeys.label(OdmKeys.DASH, o.keySprint)))
                .replace("[WINCH]", key(OdmKeys.label(OdmKeys.WINCH_LOCK, o.keyShift)))
                .replace("[RELOAD]", key(OdmKeys.RELOAD.getTranslatedKeyMessage().getString()))
                .replace("[NOVA]", key(OdmKeys.GAS_NOVA.getTranslatedKeyMessage().getString()))
                .replace("[SKILLS]", key(OdmKeys.SKILLS.getTranslatedKeyMessage().getString()))
                .replace("[MANUAL]", key(OdmKeys.MANUAL.getTranslatedKeyMessage().getString()));
        }
    }

    private static String key(String name) {
        return "[" + name + "]";
    }
}
