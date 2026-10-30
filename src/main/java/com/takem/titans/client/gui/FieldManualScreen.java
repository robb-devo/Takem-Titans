package com.takem.titans.client.gui;

import com.takem.titans.item.GearTier;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * The Survey Corps field manual. An open book: chapters down the left page, the text on the right. It opens with a
 * lift, pages turn on their spine, the bookmark ribbon sways, and dust drifts over the paper. Key names in the text are
 * the player's own bindings. It reopens where it was closed.
 */
public class FieldManualScreen extends Screen {
    private static final int W = 372;
    private static final int H = 228;
    private static final int INK = 0xFF2E2216;
    private static final int HEAD = 0xFF6A3A16;
    private static final int FADED = 0xFF8C7A5E;
    private static final List<FieldManualScreen.Chapter> CHAPTERS = List.of(
        new FieldManualScreen.Chapter("start", () -> new ItemStack(ModItems.SCOUT_CLOAK.get()), 2),
        new FieldManualScreen.Chapter("odm", () -> new ItemStack(ModItems.ODM_GEAR.get()), 2),
        new FieldManualScreen.Chapter("combat", () -> new ItemStack(ModItems.ODM_BLADES.get()), 2),
        new FieldManualScreen.Chapter("gear", () -> new ItemStack(ModItems.GODSPEED_ODM_GEAR.get()), 3),
        new FieldManualScreen.Chapter("titans", () -> new ItemStack(ModItems.PURE_TITAN_SPAWN_EGG.get()), 3),
        new FieldManualScreen.Chapter("squad", () -> new ItemStack(ModItems.SQUAD_HORN.get()), 2),
        new FieldManualScreen.Chapter("city", () -> new ItemStack(Items.BRICKS), 3),
        new FieldManualScreen.Chapter("valgard", () -> new ItemStack(Items.BELL), 2),
        new FieldManualScreen.Chapter("progress", () -> new ItemStack(ModItems.MERIT_BADGE.get()), 2)
    );
    private static int lastChapter;
    private static int lastPage;
    private final ItemStack[] icons = new ItemStack[CHAPTERS.size()];
    private final float[] hover = new float[CHAPTERS.size()];
    private final float[] hoverO = new float[CHAPTERS.size()];
    private final float[] motes = new float[48];
    private int chapter;
    private int page;
    private int prevChapter;
    private int prevPage;
    private float flip = 1.0F;
    private float flipO = 1.0F;
    private float open;
    private float openO;
    private float bar;
    private float barO;
    private int ticks;
    private int bx;
    private int by;
    private float scale = 1.0F;

    public FieldManualScreen() {
        super(Component.translatable("item.takemtitans.field_manual"));
        this.chapter = Mth.clamp(lastChapter, 0, CHAPTERS.size() - 1);
        this.page = Mth.clamp(lastPage, 0, CHAPTERS.get(this.chapter).pages - 1);
        this.prevChapter = this.chapter;
        this.prevPage = this.page;
        this.bar = this.barO = (float)this.chapter;
        RandomSource r = RandomSource.create();

        for (int i = 0; i < this.motes.length; i += 3) {
            this.motes[i] = r.nextFloat();
            this.motes[i + 1] = r.nextFloat();
            this.motes[i + 2] = r.nextFloat();
        }
    }

    @Override
    protected void init() {
        this.scale = Math.min(1.0F, Math.min((float)this.width / (float)(W + 30), (float)this.height / (float)(H + 40)));
        this.bx = Math.round(((float)this.width / this.scale - (float)W) / 2.0F);
        this.by = Math.round(((float)this.height / this.scale - (float)H) / 2.0F);

        for (int i = 0; i < CHAPTERS.size(); i++) {
            this.icons[i] = CHAPTERS.get(i).icon.get();
        }
    }

    @Override
    public void tick() {
        this.ticks++;
        this.openO = this.open;
        this.open = Math.min(1.0F, this.open + 0.14F);
        this.flipO = this.flip;
        this.flip = Math.min(1.0F, this.flip + 0.17F);
        this.barO = this.bar;
        this.bar = this.bar + ((float)this.chapter - this.bar) * 0.45F;

        for (int i = 0; i < this.hover.length; i++) {
            this.hoverO[i] = this.hover[i];
        }
    }

    @Override
    public void onClose() {
        lastChapter = this.chapter;
        lastPage = this.page;
        super.onClose();
    }

    private void turn(int newChapter, int newPage) {
        int c = Mth.clamp(newChapter, 0, CHAPTERS.size() - 1);
        int p = Mth.clamp(newPage, 0, CHAPTERS.get(c).pages - 1);
        if (c != this.chapter || p != this.page) {
            this.prevChapter = this.chapter;
            this.prevPage = this.page;
            this.chapter = c;
            this.page = p;
            this.flip = 0.0F;
            this.flipO = 0.0F;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9F + this.ticks % 5 * 0.05F));
        }
    }

    private void step(int dir) {
        int pages = CHAPTERS.get(this.chapter).pages;
        if (this.page + dir >= 0 && this.page + dir < pages) {
            this.turn(this.chapter, this.page + dir);
        } else if (dir > 0 && this.chapter + 1 < CHAPTERS.size()) {
            this.turn(this.chapter + 1, 0);
        } else if (dir < 0 && this.chapter > 0) {
            this.turn(this.chapter - 1, CHAPTERS.get(this.chapter - 1).pages - 1);
        }
    }

    private double lx(double mx) {
        return mx / (double)this.scale - (double)this.bx;
    }

    private double ly(double my) {
        return my / (double)this.scale - (double)this.by;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = this.lx(mx);
        double y = this.ly(my);
        int row = this.rowAt(x, y);
        if (row >= 0) {
            this.turn(row, 0);
            return true;
        } else if (y > (double)(H - 26) && y < (double)(H - 8)) {
            if (x > (double)(W / 2 + 14) && x < (double)(W / 2 + 40)) {
                this.step(-1);
                return true;
            } else if (x > (double)(W - 40) && x < (double)(W - 14)) {
                this.step(1);
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0.0) {
            this.step(sy < 0.0 ? 1 : -1);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_PAGE_DOWN -> {
                this.step(1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_PAGE_UP -> {
                this.step(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                this.turn(this.chapter + 1, 0);
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                this.turn(this.chapter - 1, 0);
                return true;
            }
            default -> {
                if (com.takem.titans.client.OdmKeys.MANUAL.matches(key, scan)) {
                    this.onClose();
                    return true;
                }

                return super.keyPressed(key, scan, mods);
            }
        }
    }

    /** Chapter row under the cursor on the left page, or -1. */
    private int rowAt(double x, double y) {
        if (x > 16.0 && x < (double)(W / 2 - 12)) {
            int row = (int)Math.floor((y - 58.0) / 19.0);
            if (row >= 0 && row < CHAPTERS.size() && y >= 58.0) {
                return row;
            }
        }

        return -1;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, this.width, this.height, 0xB0080706, 0xD0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        float o = Mth.lerp(pt, this.openO, this.open);
        float lift = 1.0F - o;
        float ease = 1.0F - lift * lift * lift;
        float t = (float)this.ticks + pt;
        double x = this.lx((double)mx);
        double y = this.ly((double)my);
        int row = this.rowAt(x, y);

        for (int i = 0; i < this.hover.length; i++) {
            this.hover[i] = this.hover[i] + ((i == row ? 1.0F : 0.0F) - this.hover[i]) * 0.25F;
        }

        g.pose().pushPose();
        g.pose().scale(this.scale, this.scale, 1.0F);
        // the book lifts into view and settles
        float s = 0.92F + 0.08F * ease;
        g.pose().translate((float)(this.bx + W / 2), (float)(this.by + H / 2) + lift * 40.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.pose().translate((float)(-W / 2), (float)(-H / 2), 0.0F);
        this.drawBook(g, t);
        this.drawContents(g, t, pt, x, y);
        this.drawMotes(g, t);
        g.pose().popPose();
        String hint = Component.translatable("manual.takemtitans.hint").getString();
        g.drawString(this.font, hint, (this.width - this.font.width(hint)) / 2, this.height - 14, 0x80D8CFBC, false);
    }

    private void drawBook(GuiGraphics g, float t) {
        // leather cover, a darker rim, two parchment pages and the shadow of the spine between them
        g.fill(-10, -8, W + 10, H + 10, 0xFF1C110A);
        g.fill(-8, -6, W + 8, H + 8, 0xFF4A2D19);
        g.fill(-6, -4, W + 6, H + 6, 0xFF5A3720);
        g.fill(-2, H, W + 2, H + 4, 0xFFCFC1A0);
        g.fillGradient(0, 0, W / 2, H, 0xFFEFE6CF, 0xFFE0D3B3);
        g.fillGradient(W / 2, 0, W, H, 0xFFF1E8D2, 0xFFE2D5B6);

        for (int i = 0; i < 14; i++) {
            int a = (int)(70.0F * (1.0F - (float)i / 14.0F));
            g.fill(W / 2 - 1 - i, 0, W / 2 - i, H, a << 24 | 0x3A2414);
            g.fill(W / 2 + i, 0, W / 2 + i + 1, H, a << 24 | 0x3A2414);
        }

        g.fill(0, 0, W, 1, 0x40000000);
        // the bookmark ribbon sways a little
        int rx = W - 46 + Math.round(Mth.sin(t * 0.05F) * 1.5F);
        g.fill(rx, -6, rx + 7, 24, 0xFF8E1E22);
        g.fill(rx, 24, rx + 3, 28, 0xFF8E1E22);
        g.fill(rx + 4, 24, rx + 7, 28, 0xFF8E1E22);
        g.fill(rx + 1, -6, rx + 2, 22, 0x40FFFFFF);
    }

    private void drawContents(GuiGraphics g, float t, float pt, double mx, double my) {
        Font f = this.font;
        // left page: the title and the chapters
        g.pose().pushPose();
        g.pose().translate(18.0F, 16.0F, 0.0F);
        g.pose().scale(1.2F, 1.2F, 1.0F);
        g.drawString(f, Component.translatable("manual.takemtitans.title").withStyle(ChatFormatting.BOLD), 0, 0, HEAD, false);
        g.pose().popPose();
        Component sub = Component.translatable("manual.takemtitans.subtitle");
        float subScale = Math.min(1.0F, (float)(W / 2 - 34) / (float)Math.max(1, f.width(sub)));
        g.pose().pushPose();
        g.pose().translate(18.0F, 33.0F, 0.0F);
        g.pose().scale(subScale, subScale, 1.0F);
        g.drawString(f, sub, 0, 0, FADED, false);
        g.pose().popPose();
        g.fill(18, 46, W / 2 - 16, 47, 0x506A3A16);
        float b = Mth.lerp(pt, this.barO, this.bar);
        int barY = 58 + Math.round(b * 19.0F);
        g.fill(14, barY - 1, W / 2 - 12, barY + 17, 0x306A3A16);
        g.fill(14, barY - 1, 16, barY + 17, 0xFF8E1E22);

        for (int i = 0; i < CHAPTERS.size(); i++) {
            float h = Mth.lerp(pt, this.hoverO[i], this.hover[i]);
            int rx = 20 + Math.round(h * 4.0F);
            int ry = 58 + i * 19;
            g.pose().pushPose();
            g.pose().translate((float)rx, (float)ry, 0.0F);
            g.pose().scale(0.9F, 0.9F, 1.0F);
            g.renderItem(this.icons[i], 0, 0);
            g.pose().popPose();
            int col = i == this.chapter ? HEAD : (h > 0.5F ? 0xFF4A3420 : INK);
            g.drawString(f, Component.translatable("manual.takemtitans." + CHAPTERS.get(i).id + ".title"), rx + 20, ry + 5, col, false);
        }

        // right page: the text, turning on the spine when it changes
        float fl = Mth.lerp(pt, this.flipO, this.flip);
        boolean old = fl < 0.5F;
        float k = old ? 1.0F - fl * 2.0F : (fl - 0.5F) * 2.0F;
        float squash = Mth.sin(k * 1.5707964F);
        int left = W / 2;
        g.pose().pushPose();
        g.pose().translate((float)left, 0.0F, 0.0F);
        g.pose().scale(Math.max(0.02F, squash), 1.0F, 1.0F);
        this.drawPage(g, old ? this.prevChapter : this.chapter, old ? this.prevPage : this.page, t);
        if (squash < 0.999F) {
            // the turning leaf darkens as it stands on edge
            int a = (int)((1.0F - squash) * 120.0F);
            g.fill(0, 0, W / 2, H, a << 24 | 0x2A1A0E);
        }

        g.pose().popPose();
        // page arrows and the page count
        FieldManualScreen.Chapter ch = CHAPTERS.get(this.chapter);
        boolean first = this.chapter == 0 && this.page == 0;
        boolean last = this.chapter == CHAPTERS.size() - 1 && this.page == ch.pages - 1;
        boolean overL = my > (double)(H - 26) && my < (double)(H - 8) && mx > (double)(left + 14) && mx < (double)(left + 40);
        boolean overR = my > (double)(H - 26) && my < (double)(H - 8) && mx > (double)(W - 40) && mx < (double)(W - 14);
        if (!first) {
            g.drawString(f, "◀", left + 20 - (overL ? 2 : 0), H - 21, overL ? HEAD : FADED, false);
        }

        if (!last) {
            g.drawString(f, "▶", W - 30 + (overR ? 2 : 0), H - 21, overR ? HEAD : FADED, false);
        }

        String count = (this.page + 1) + " / " + ch.pages;
        g.drawString(f, count, left + (W / 2 - f.width(count)) / 2, H - 21, FADED, false);
    }

    private void drawPage(GuiGraphics g, int ch, int pg, float t) {
        Font f = this.font;
        String base = "manual.takemtitans." + CHAPTERS.get(ch).id + "." + pg;
        int w = W / 2 - 34;
        g.pose().pushPose();
        g.pose().translate(18.0F, 16.0F, 0.0F);
        g.pose().scale(1.1F, 1.1F, 1.0F);
        g.drawString(f, Component.translatable(base + ".title").withStyle(ChatFormatting.BOLD), 0, 0, HEAD, false);
        g.pose().popPose();
        g.fill(18, 30, 18 + w, 31, 0x506A3A16);
        // in the gear chapter, the rig you're wearing gets a gold mark
        if ("gear".equals(CHAPTERS.get(ch).id) && pg == 0 && this.minecraft != null && this.minecraft.player != null && OdmGear.hasGear(this.minecraft.player)) {
            GearTier tier = OdmGear.tier(this.minecraft.player);
            String worn = Component.translatable("manual.takemtitans.wearing", OdmGear.getGear(this.minecraft.player).getHoverName()).getString();
            g.drawString(f, worn, 18, H - 40, GearIntro.colour(tier) & 0xFFFFFF | 0xFF000000, false);
        }

        String body = KeyText.fill(Component.translatable(base + ".body").getString());
        List<FormattedCharSequence> lines = new ArrayList<>();
        float sc = 0.85F;
        int wrap = Math.round((float)w / sc);

        for (String para : body.split("\n")) {
            if (para.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            } else {
                lines.addAll(f.split(Component.literal(para), wrap));
            }
        }

        g.pose().pushPose();
        g.pose().translate(18.0F, 38.0F, 0.0F);
        g.pose().scale(sc, sc, 1.0F);
        int ly = 0;

        for (FormattedCharSequence line : lines) {
            if ((float)ly * sc > (float)(H - 82)) {
                break;
            }

            g.drawString(f, line, 0, ly, INK, false);
            ly += 10;
        }

        g.pose().popPose();
    }

    private void drawMotes(GuiGraphics g, float t) {
        // dust in the lamplight, drifting over the paper
        for (int i = 0; i < this.motes.length; i += 3) {
            float px = (this.motes[i] * (float)W + t * (0.08F + this.motes[i + 2] * 0.12F)) % (float)W;
            float py = (this.motes[i + 1] * (float)H + Mth.sin(t * 0.03F + (float)i) * 6.0F - t * 0.05F * this.motes[i + 2]) % (float)H;
            if (py < 0.0F) {
                py += (float)H;
            }

            int a = (int)(60.0F + 50.0F * Mth.sin(t * 0.1F + (float)i * 1.7F));
            g.fill(Math.round(px), Math.round(py), Math.round(px) + 1, Math.round(py) + 1, a << 24 | 0xFFF4DC);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static record Chapter(String id, Supplier<ItemStack> icon, int pages) {
    }
}
