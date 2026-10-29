package com.takem.titans.entity.titan;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;

/**
 * Marked titans: once the story is past the walls, now and then a titan turns up with something wrong with it. Each
 * mark changes how it fights, shows on it (a coloured wisp at the nape, its name in that colour), and pays off when it
 * goes down: more experience, a fistful of Merit Badges and a piece of rare kit.
 */
public enum Elite {
    NONE(0xFFFFFF, 1.0F),
    /** Its nape has hardened: it takes far more to cut through. */
    HARDENED(0xD9D2C4, 2.2F),
    /** Runs and turns like an abnormal. */
    SWIFT(0x7FD8FF, 1.3F),
    /** Severed limbs grow back in seconds and the nape knits as you watch. */
    REGENERATOR(0x7CFF8A, 1.3F),
    /** Half again as tall as its kind. */
    GIANT(0xFFB347, 1.6F),
    /** Boiling steam pours off it and scalds whoever comes close. */
    SCALDING(0xFF5A3A, 1.3F);

    public final int colour;
    public final float napeMul;

    Elite(int colour, float napeMul) {
        this.colour = colour;
        this.napeMul = napeMul;
    }

    public static Elite of(int id) {
        Elite[] all = values();
        return id > 0 && id < all.length ? all[id] : NONE;
    }

    public static Elite roll(RandomSource r) {
        return values()[1 + r.nextInt(values().length - 1)];
    }

    public float red() {
        return (float)(this.colour >> 16 & 0xFF) / 255.0F;
    }

    public float green() {
        return (float)(this.colour >> 8 & 0xFF) / 255.0F;
    }

    public float blue() {
        return (float)(this.colour & 0xFF) / 255.0F;
    }

    /** "Regenerating Pure Titan", in the mark's colour. */
    public Component title(Component kind) {
        return Component.translatable("elite.takemtitans." + this.name().toLowerCase(java.util.Locale.ROOT), kind)
            .withStyle(Style.EMPTY.withColor(this.colour).withBold(true));
    }
}
