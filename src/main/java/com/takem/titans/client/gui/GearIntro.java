package com.takem.titans.client.gui;

import com.takem.titans.TakemTitans;
import com.takem.titans.item.GearTier;
import com.takem.titans.odm.OdmGear;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

/**
 * The first time a rig is strapped on, a card slides in from the left and says what it does differently - once per
 * rig, remembered across sessions. The manual has the rest.
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class GearIntro {
    public static final ResourceLocation LAYER = TakemTitans.id("gear_intro");
    private static final int LIFE = 220;
    private static final int W = 230;
    private static final Set<String> SEEN = new HashSet<>();
    private static boolean loaded;
    @Nullable
    private static GearTier last;
    @Nullable
    private static GearTier showing;
    private static ItemStack icon = ItemStack.EMPTY;
    private static int age = -1;

    private GearIntro() {
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) {
            last = null;
            age = -1;
        } else {
            GearTier tier = OdmGear.hasGear(p) ? OdmGear.tier(p) : null;
            if (tier != last) {
                last = tier;
                load();
                if (tier != null && tier != GearTier.STANDARD && SEEN.add(tier.name())) {
                    showing = tier;
                    icon = OdmGear.getGear(p).copy();
                    age = 0;
                    save();
                }
            }

            if (age >= 0 && !mc.isPaused() && ++age > LIFE) {
                age = -1;
            }
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/takemtitans-client.properties");
    }

    private static void load() {
        if (!loaded) {
            loaded = true;
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(file())) {
                props.load(r);
            } catch (IOException ignored) {
            }

            for (String s : props.getProperty("introduced", "").split(",")) {
                if (!s.isBlank()) {
                    SEEN.add(s.trim());
                }
            }
        }
    }

    private static void save() {
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(file())) {
            props.load(r);
        } catch (IOException ignored) {
        }

        props.setProperty("introduced", String.join(",", SEEN));
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                props.store(w, "Takem Titans client state");
            }
        } catch (IOException ignored) {
        }
    }

    public static int colour(GearTier tier) {
        return switch (tier) {
            case ADVANCED -> 0xFFFFC23A;
            case PHANTOM -> 0xFF8DF0FF;
            case GODSPEED -> 0xFF7FD8FF;
            case EVENT_HORIZON -> 0xFFC58CFF;
            default -> 0xFFE8E2D4;
        };
    }

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (age >= 0 && showing != null && !mc.options.hideGui) {
            Font font = mc.font;
            float t = (float)age + dt.getGameTimeDeltaPartialTick(false);
            // slides in, holds, slides out
            float in = Mth.clamp(t / 12.0F, 0.0F, 1.0F);
            float out = Mth.clamp(((float)LIFE - t) / 14.0F, 0.0F, 1.0F);
            float k = Math.min(1.0F - (1.0F - in) * (1.0F - in) * (1.0F - in), out * out);
            String key = "intro.takemtitans." + showing.name().toLowerCase();
            List<FormattedCharSequence> lines = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String raw = Component.translatable(key + "." + i).getString();
                if (!raw.equals(key + "." + i) && !raw.isEmpty()) {
                    lines.addAll(font.split(Component.literal(KeyText.fill(raw)), W - 40));
                }
            }

            int h = 54 + lines.size() * 10;
            int x = Math.round(-W + (float)(W + 12) * k);
            int y = g.guiHeight() / 2 - h / 2 - 20;
            int col = colour(showing);
            g.fill(x, y, x + W, y + h, 0xD8101318);
            g.fill(x, y, x + 3, y + h, col);
            // a thin shine runs along the top edge while the card is up
            float sweep = (t * 0.02F) % 1.0F;
            int sx = x + (int)(sweep * (float)W);
            g.fill(x, y, x + W, y + 1, col & 0x60FFFFFF);
            g.fill(Math.max(x, sx - 20), y, Math.min(x + W, sx + 20), y + 1, col);
            g.pose().pushPose();
            g.pose().translate((float)(x + 10), (float)(y + 9) + Mth.sin(t * 0.12F) * 1.5F, 0.0F);
            g.pose().scale(1.5F, 1.5F, 1.0F);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
            g.drawString(font, Component.translatable("intro.takemtitans.new").getString(), x + 40, y + 8, 0xFFB8A27A, false);
            g.drawString(font, icon.getHoverName().getString(), x + 40, y + 19, col, true);
            int ly = y + 38;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 12, ly, 0xFFE6E0D2, false);
                ly += 10;
            }

            String more = KeyText.fill(Component.translatable("intro.takemtitans.more").getString());
            g.drawString(font, more, x + W - 8 - font.width(more), y + h - 11, 0xFF8C8578, false);
        }
    }
}
