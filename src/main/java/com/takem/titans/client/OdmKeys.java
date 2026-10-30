package com.takem.titans.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public final class OdmKeys {
    public static final String CATEGORY = "key.categories.takemtitans";
    public static final KeyMapping HOOK_LEFT = key("hook_left", 81);
    public static final KeyMapping HOOK_RIGHT = key("hook_right", 69);
    public static final KeyMapping HOOK_BOTH = key("hook_both", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping RELOAD = key("reload", 82);
    public static final KeyMapping BOOST = fallbackKey("boost", () -> Minecraft.getInstance().options.keyJump);
    public static final KeyMapping DASH = fallbackKey("dash", () -> Minecraft.getInstance().options.keySprint);
    public static final KeyMapping WINCH_LOCK = key("winch_lock", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping GAS_NOVA = key("gas_nova", 71);
    public static final KeyMapping SKILLS = key("skills", 75);
    public static final KeyMapping MANUAL = key("manual", 74);
    public static final KeyMapping WORKSHOP = key("workshop", 85);
    public static final KeyMapping[] ALL = new KeyMapping[]{HOOK_LEFT, HOOK_RIGHT, HOOK_BOTH, RELOAD, BOOST, DASH, WINCH_LOCK, GAS_NOVA, SKILLS, MANUAL, WORKSHOP};

    private OdmKeys() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.takemtitans." + name, KeyConflictContext.IN_GAME, Type.KEYSYM, code, "key.categories.takemtitans");
    }

    /**
     * Boost and dash are unbound by default and ride on jump and sprint. Their names follow suit, so a keybind shown
     * in a tooltip or a tutorial line reads "Space" rather than "Not bound".
     */
    private static KeyMapping fallbackKey(String name, java.util.function.Supplier<KeyMapping> fallback) {
        return new KeyMapping("key.takemtitans." + name, KeyConflictContext.IN_GAME, Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY) {
            @Override
            public net.minecraft.network.chat.Component getTranslatedKeyMessage() {
                if (this.isUnbound() && Minecraft.getInstance().options != null) {
                    return fallback.get().getTranslatedKeyMessage();
                }

                return super.getTranslatedKeyMessage();
            }
        };
    }

    private static boolean down(KeyMapping own, KeyMapping fallback) {
        return own.isUnbound() ? fallback.isDown() : own.isDown();
    }

    public static boolean boostDown() {
        return down(BOOST, Minecraft.getInstance().options.keyJump);
    }

    public static boolean dashDown() {
        return down(DASH, Minecraft.getInstance().options.keySprint);
    }

    public static boolean winchLockDown() {
        return down(WINCH_LOCK, Minecraft.getInstance().options.keyShift);
    }

    public static String label(KeyMapping own, KeyMapping fallback) {
        KeyMapping k = own.isUnbound() && fallback != null ? fallback : own;
        return k.getTranslatedKeyMessage().getString();
    }
}
