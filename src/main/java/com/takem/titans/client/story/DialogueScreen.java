package com.takem.titans.client.story;

import com.takem.titans.client.gui.TTGui;
import com.takem.titans.network.StoryActionPayload;
import com.takem.titans.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class DialogueScreen extends Screen {
    private static final float CHARS_PER_TICK = 2.2F;
    private final int npc;
    private final Component speaker;
    private final int role;
    private final List<Component> lines;
    private final List<Integer> ids;
    private final List<Component> labels;
    private final List<Button> buttons = new ArrayList<>();
    private int line;
    private float shown;
    private int ticks;

    public DialogueScreen(int npc, Component speaker, int role, List<Component> lines, List<Integer> ids, List<Component> labels) {
        super(speaker);
        this.npc = npc;
        this.speaker = speaker;
        this.role = role;
        this.lines = lines.isEmpty() ? List.of(Component.literal("...")) : lines;
        this.ids = ids;
        this.labels = labels;
    }

    private int panelTop() {
        return this.height - Math.max(96, this.height / 3);
    }

    @Override
    protected void init() {
        this.buttons.clear();
        int top = this.panelTop();
        int bw = Math.min(220, this.width / 3);
        int n = this.ids.size();

        for (int i = 0; i < n; i++) {
            int id = this.ids.get(i);
            Button b = Button.builder(this.labels.get(i), btn -> this.choose(id)).bounds(this.width - bw - 14, top - 8 - (n - i) * 22, bw, 20).build();
            b.visible = false;
            this.buttons.add(this.addRenderableWidget(b));
        }

        this.updateButtons();
    }

    private boolean lineDone() {
        return this.shown >= (float)this.lines.get(this.line).getString().length();
    }

    private boolean lastLine() {
        return this.line >= this.lines.size() - 1;
    }

    private void updateButtons() {
        boolean show = this.lastLine() && this.lineDone();

        for (Button b : this.buttons) {
            b.visible = show;
        }
    }

    private void choose(int id) {
        if (id != 0) {
            PacketDistributor.sendToServer(new StoryActionPayload(this.npc, id));
        }

        this.onClose();
    }

    @Override
    public void tick() {
        this.ticks++;
        if (!this.lineDone()) {
            int before = (int)this.shown;
            this.shown += 2.2F;
            if ((int)this.shown / 7 != before / 7 && this.minecraft != null) {
                float pitch = switch (this.role) {
                    case 2 -> 0.85F;
                    case 3 -> 0.95F;
                    case 4 -> 1.35F;
                    case 5 -> 0.7F;
                    default -> 1.1F;
                };
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DIALOGUE_BLIP.get(), pitch + (float)Math.random() * 0.08F, 0.22F));
            }
        }

        this.updateButtons();
    }

    private void advance() {
        if (!this.lineDone()) {
            this.shown = (float)this.lines.get(this.line).getString().length();
        } else if (!this.lastLine()) {
            this.line++;
            this.shown = 0.0F;
        }

        this.updateButtons();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        } else {
            this.advance();
            return true;
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key != 32 && key != 257 && key != 69) {
            return super.keyPressed(key, scan, mods);
        } else if (this.lastLine() && this.lineDone() && key != 69 && !this.buttons.isEmpty()) {
            this.choose(this.ids.get(0));
            return true;
        } else {
            this.advance();
            return true;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, this.height / 2, this.width, this.height, 0, -1073741824);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int top = this.panelTop();
        int left = 14;
        int right = this.width - 14;
        TTGui.panel(g, left - 4, top - 4, right - left + 8, this.height - 8 - top + 4);
        int portrait = 0;
        if (this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getEntity(this.npc) instanceof LivingEntity le) {
            portrait = 70;
            TTGui.hudPanel(g, left + 4, top + 6, portrait - 4, this.height - 24 - top);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, left + 6, top + 8, left + portrait - 2, this.height - 20, Math.max(24, (this.height - 28 - top) / 2), 0.0625F, (float)mx, (float)my, le
            );
        }

        int tx = left + 10 + portrait;
        TTGui.sprite(g, tx - 6, top + 4, Math.max(100, this.font.width(this.speaker) + 16), 16, 128, 32, 100, 18, -1);
        g.drawString(this.font, this.speaker, tx + 2, top + 8, -1523614, true);
        String full = this.lines.get(this.line).getString();
        String vis = full.substring(0, Math.min(full.length(), (int)this.shown));
        List<FormattedCharSequence> wrapped = this.font.split(Component.literal(vis), right - tx - 12);
        int y = top + 22;

        for (FormattedCharSequence s : wrapped) {
            g.drawString(this.font, s, tx, y, -855310, false);
            y += 11;
        }

        String page = this.line + 1 + "/" + this.lines.size();
        g.drawString(this.font, page, right - 8 - this.font.width(page), top + 8, -8747118, false);
        if (this.lineDone() && !this.lastLine() && this.ticks / 8 % 2 == 0) {
            g.drawString(this.font, "▼", right - 16, this.height - 26, -1523614, false);
        }

        if (!this.lineDone() || !this.lastLine()) {
            Component hint = Component.translatable("dialogue.takemtitans.continue");
            g.drawString(this.font, hint, right - 24 - this.font.width(hint), this.height - 26, -9800064, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
