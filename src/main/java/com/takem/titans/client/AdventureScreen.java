package com.takem.titans.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.takem.titans.mixin.CreateWorldScreenAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The front door to the mod from the title menu: carry on in the last Shiganshina world, or begin a new one in two
 * clicks without hunting for the world type in the vanilla create screen. Every other world still works the usual way.
 */
public class AdventureScreen extends Screen {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath("takemtitans", "textures/gui/title_logo.png");
    private static final ResourceKey<WorldPreset> SHIGANSHINA = ResourceKey.create(
        Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath("takemtitans", "shiganshina")
    );
    /** Written by WorldEvents when a Shiganshina world opens in singleplayer. */
    static final String LAST_FILE = "takemtitans-adventure.properties";
    private final Screen parent;
    private boolean creating;
    private String worldName;
    private SelectedGameMode mode = SelectedGameMode.SURVIVAL;
    private boolean cheats;
    @Nullable
    private EditBox nameBox;
    @Nullable
    private String lastId;
    @Nullable
    private String lastName;
    private boolean focusName;

    public AdventureScreen(Screen parent) {
        super(Component.translatable("adventure.takemtitans.title"));
        this.parent = parent;
        this.worldName = Component.translatable("adventure.takemtitans.default_name").getString();
    }

    @Override
    protected void init() {
        this.readLast();
        int cx = this.width / 2;
        int y = this.top() + this.logoHeight() + 22;
        if (!this.creating) {
            if (this.lastId != null) {
                Button cont = Button.builder(Component.translatable("adventure.takemtitans.continue", this.lastName), b -> this.continueLast())
                    .bounds(cx - 110, y, 220, 20)
                    .build();
                this.addRenderableWidget(cont);
                y += 24;
            }

            this.addRenderableWidget(
                Button.builder(Component.translatable("adventure.takemtitans.new"), b -> this.setCreating(true)).bounds(cx - 110, y, 220, 20).build()
            );
            y += 24;
            this.addRenderableWidget(
                Button.builder(Component.translatable("adventure.takemtitans.worlds"), b -> this.minecraft.setScreen(new SelectWorldScreen(this)))
                    .bounds(cx - 110, y, 220, 20)
                    .build()
            );
            y += this.lastId != null ? 32 : 56;
            this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> this.onClose()).bounds(cx - 110, y, 220, 20).build());
        } else {
            this.nameBox = new EditBox(this.font, cx - 110, y + 10, 220, 20, Component.translatable("selectWorld.enterName"));
            this.nameBox.setMaxLength(48);
            this.nameBox.setValue(this.worldName);
            this.nameBox.setResponder(s -> this.worldName = s);
            this.addRenderableWidget(this.nameBox);
            y += 34;
            this.addRenderableWidget(
                CycleButton.<SelectedGameMode>builder(m -> m.displayName)
                    .withValues(SelectedGameMode.SURVIVAL, SelectedGameMode.HARDCORE, SelectedGameMode.CREATIVE)
                    .withInitialValue(this.mode)
                    .create(cx - 110, y, 108, 20, Component.translatable("selectWorld.gameMode"), (b, m) -> this.mode = m)
            );
            this.addRenderableWidget(
                CycleButton.onOffBuilder(this.cheats).create(cx + 2, y, 108, 20, Component.translatable("selectWorld.allowCommands"), (b, v) -> this.cheats = v)
            );
            y += 28;
            Button begin = Button.builder(Component.translatable("adventure.takemtitans.begin").withStyle(ChatFormatting.BOLD), b -> this.create(true))
                .bounds(cx - 110, y, 220, 20)
                .build();
            this.addRenderableWidget(begin);
            y += 24;
            this.addRenderableWidget(
                Button.builder(Component.translatable("adventure.takemtitans.options"), b -> this.create(false)).bounds(cx - 110, y, 108, 20).build()
            );
            this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> this.setCreating(false)).bounds(cx + 2, y, 108, 20).build());
            this.setInitialFocus(this.nameBox);
        }
    }

    private void setCreating(boolean creating) {
        this.creating = creating;
        this.rebuildWidgets();
        // the click that got us here focuses its own (now removed) button once it returns; take focus back next tick
        this.focusName = creating;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.focusName && this.nameBox != null) {
            this.focusName = false;
            this.setFocused(this.nameBox);
            this.nameBox.moveCursorToEnd(false);
        }
    }

    /** Everything below the logo: tagline, then the buttons (and, when creating, the note under them). */
    private int contentHeight() {
        return this.creating ? 142 : 122;
    }

    private int logoHeight() {
        return Mth.clamp(this.height - this.contentHeight() - 40, 30, 100);
    }

    private int top() {
        return Math.max(6, (this.height - this.logoHeight() - this.contentHeight() - 14) / 2);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int h = this.logoHeight();
        int w = h * 640 / 200;
        int top = this.top();
        RenderSystem.enableBlend();
        g.blit(LOGO, (this.width - w) / 2, top, w, h, 0.0F, 0.0F, 640, 200, 640, 200);
        RenderSystem.disableBlend();
        Component line = this.creating ? Component.translatable("adventure.takemtitans.new.sub") : Component.translatable("adventure.takemtitans.tagline");
        g.drawCenteredString(this.font, line, this.width / 2, top + h + 6, 0xD8C7A0);
        if (this.creating) {
            g.drawString(this.font, Component.translatable("selectWorld.enterName"), this.width / 2 - 110, top + h + 22, 0xA0A0A0);
            Component info = this.mode == SelectedGameMode.HARDCORE
                ? Component.translatable("adventure.takemtitans.hardcore_info")
                : Component.translatable("adventure.takemtitans.keep_info");
            int y = top + h + 22 + 110;
            for (FormattedCharSequence part : this.font.split(info, Math.min(this.width - 20, 300))) {
                g.drawCenteredString(this.font, part, this.width / 2, y, 0x9A8F7A);
                y += 10;
            }
        } else {
            g.drawString(this.font, "v2.0", this.width - this.font.width("v2.0") - 4, this.height - 12, 0x6A6152);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private void continueLast() {
        if (this.lastId != null) {
            String id = this.lastId;
            this.minecraft.createWorldOpenFlows().openWorld(id, () -> this.minecraft.setScreen(this));
        }
    }

    private void create(boolean now) {
        String name = this.worldName.isBlank() ? Component.translatable("adventure.takemtitans.default_name").getString() : this.worldName.trim();
        Minecraft mc = this.minecraft;
        CreateWorldScreen.openFresh(mc, this);
        if (mc.screen instanceof CreateWorldScreen cws) {
            WorldCreationUiState ui = cws.getUiState();
            ui.setName(name);
            ui.setGameMode(this.mode);
            ui.setAllowCommands(this.cheats);
            Optional<WorldTypeEntry> type = ui.getNormalPresetList().stream().filter(e -> e.preset() != null && e.preset().is(SHIGANSHINA)).findFirst();
            if (type.isEmpty()) {
                type = ui.getAltPresetList().stream().filter(e -> e.preset() != null && e.preset().is(SHIGANSHINA)).findFirst();
            }

            type.ifPresent(ui::setWorldType);
            if (now && type.isPresent()) {
                ((CreateWorldScreenAccessor)cws).takemtitans$create();
            } else {
                // rebuild the tabs so they show what was just filled in
                cws.resize(mc, this.width, this.height);
            }
        }
    }

    private void readLast() {
        this.lastId = null;
        Path file = FMLPaths.CONFIGDIR.get().resolve(LAST_FILE);
        if (Files.isRegularFile(file)) {
            Properties p = new Properties();

            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                return;
            }

            String id = p.getProperty("folder");
            if (id != null && !id.isBlank() && this.minecraft.getLevelSource().levelExists(id)) {
                this.lastId = id;
                this.lastName = p.getProperty("name", id);
            }
        }
    }

    @EventBusSubscriber(modid = "takemtitans", value = Dist.CLIENT)
    public static final class TitleHook {
        private TitleHook() {
        }

        /** The regiment's logo where the Minecraft one used to be (the vanilla logo texture is blank in this pack). */
        @SubscribeEvent
        public static void onTitleRender(ScreenEvent.Render.Post event) {
            if (event.getScreen() instanceof TitleScreen title) {
                GuiGraphics g = event.getGuiGraphics();
                int buttons = title.height / 4 + 48;
                int h = Mth.clamp(buttons - 34, 36, 84);
                int w = h * 640 / 200;
                RenderSystem.enableBlend();
                g.blit(LOGO, (title.width - w) / 2, Math.max(4, buttons - h - 22), w, h, 0.0F, 0.0F, 640, 200, 640, 200);
                RenderSystem.disableBlend();
            }
        }

        @SubscribeEvent
        public static void onTitleInit(ScreenEvent.Init.Post event) {
            if (event.getScreen() instanceof TitleScreen title) {
                for (GuiEventListener l : event.getListenersList()) {
                    if (l instanceof Button b
                        && b.getMessage().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("menu.singleplayer")
                        && b.getWidth() >= 200) {
                        // Singleplayer shares its row with the way into Shiganshina, so neither can be missed
                        b.setWidth(98);
                        event.addListener(
                            Button.builder(Component.translatable("adventure.takemtitans.button"), x -> Minecraft.getInstance().setScreen(new AdventureScreen(title)))
                                .bounds(b.getX() + 102, b.getY(), 98, 20)
                                .build()
                        );
                        return;
                    }
                }
            }
        }
    }
}
