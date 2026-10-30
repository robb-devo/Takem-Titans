package com.takem.titans.client.gui;

import com.takem.titans.client.OdmKeys;
import com.takem.titans.item.GearMod;
import com.takem.titans.item.GearModItem;
import com.takem.titans.network.OdmActionPayload;
import com.takem.titans.odm.OdmGear;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Gear Workshop: the worn rig and its slots on the left, every gear extra on the right with what it does and how
 * many are in the pack, one click to fit one or take it out again (nothing is pushed out or lost), and a short guide
 * that opens by itself the first time. Opened with its key or by using any extra.
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public class GearWorkshopScreen extends Screen {
    private static final int W = 384;
    private static final int H = 252;
    private static boolean guideSeen;
    /** Clickable areas of the last frame: x0, y0, x1, y1, action (1 guide, 40 fit, 41 take out), extra. */
    private final List<int[]> hits = new ArrayList<>();
    private boolean guide;
    private int x0;
    private int y0;

    public GearWorkshopScreen() {
        super(Component.translatable("gui.takemtitans.workshop"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new GearWorkshopScreen());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OdmKeys.WORKSHOP.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                open();
            }
        }
    }

    @Override
    protected void init() {
        this.guide = !guideSeen;
        guideSeen = true;
        this.x0 = (this.width - W) / 2;
        this.y0 = (this.height - H) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.hits.clear();
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        int x = this.x0;
        int y = this.y0;
        panel(g, x, y, W, H);
        g.drawString(this.font, this.title, x + 12, y + 10, 0xFFF2D7A0, true);
        g.drawString(this.font, Component.translatable("gui.takemtitans.workshop.sub"), x + 12, y + 22, 0xFF9A9286, false);
        Component q = Component.translatable("gui.takemtitans.workshop.guide");
        int qw = this.font.width(q) + 14;
        this.button(g, x + W - qw - 10, y + 9, qw, 15, q, mx, my, 1, 0);
        ItemStack gear = OdmGear.getGear(this.minecraft.player);
        List<Component> tip = null;
        // the rig and its slots
        int rx = x + 12;
        int ry = y + 44;
        g.drawString(this.font, Component.translatable("gui.takemtitans.workshop.rig"), rx, ry, 0xFFC9A24A, false);
        int free = 0;
        if (gear.isEmpty()) {
            this.wrap(g, Component.translatable("gui.takemtitans.workshop.no_gear"), rx, ry + 16, 144, 0xFFE07A5A);
        } else {
            int slots = GearMod.slots(OdmGear.tier(gear));
            int used = Integer.bitCount(OdmGear.mods(gear));
            free = slots - used;
            g.renderItem(gear, rx, ry + 13);
            g.drawString(this.font, gear.getHoverName(), rx + 20, ry + 13, 0xFFF4EAD8, false);
            g.drawString(this.font, Component.translatable("gui.takemtitans.workshop.slots", used, slots), rx + 20, ry + 23, 0xFF9A9286, false);
            List<GearMod> fitted = new ArrayList<>();
            for (GearMod m : GearMod.values()) {
                if (OdmGear.hasMod(gear, m)) {
                    fitted.add(m);
                }
            }
            for (int i = 0; i < 3; i++) {
                int sx = rx + i * 48;
                int sy = ry + 42;
                boolean open = i < slots;
                boolean hover = mx >= sx && mx < sx + 42 && my >= sy && my < sy + 42;
                g.fill(sx, sy, sx + 42, sy + 42, open ? (hover ? 0xC0382C1E : 0xA0201810) : 0x50101010);
                frame(g, sx, sy, 42, 42, open ? 0xFFC9A24A : 0xFF3A342C);
                if (i < fitted.size()) {
                    GearMod m = fitted.get(i);
                    g.pose().pushPose();
                    g.pose().translate((float)(sx + 5), (float)(sy + 5), 0.0F);
                    g.pose().scale(2.0F, 2.0F, 1.0F);
                    g.renderItem(new ItemStack(GearModItem.itemFor(m)), 0, 0);
                    g.pose().popPose();
                    this.hits.add(new int[]{sx, sy, sx + 42, sy + 42, 41, m.ordinal()});
                    if (hover) {
                        tip = List.of(
                            name(m).copy().withStyle(ChatFormatting.GOLD),
                            Component.translatable("tooltip.takemtitans.mod." + m.id()).withStyle(ChatFormatting.AQUA),
                            Component.translatable("gui.takemtitans.workshop.remove").withStyle(ChatFormatting.GRAY)
                        );
                    }
                } else {
                    if (!open) {
                        g.drawCenteredString(this.font, "-", sx + 21, sy + 17, 0xFF3A342C);
                    }
                    if (hover) {
                        tip = List.of(Component.translatable(open ? "gui.takemtitans.workshop.empty" : "gui.takemtitans.workshop.slot_locked"));
                    }
                }
            }
            this.wrap(g, Component.translatable("gui.takemtitans.workshop.hint"), rx, ry + 92, 144, 0xFF7A7268);
        }
        // every extra: what it does, how many are in the pack, fit it
        int lx = x + 172;
        int ly = y + 44;
        g.drawString(this.font, Component.translatable("gui.takemtitans.workshop.pack"), lx, ly, 0xFFC9A24A, false);
        GearMod[] all = GearMod.values();
        for (int i = 0; i < all.length; i++) {
            GearMod m = all[i];
            int ex = lx;
            int ey = ly + 12 + i * 29;
            int count = count(this.minecraft.player.getInventory(), m);
            boolean fitted = !gear.isEmpty() && OdmGear.hasMod(gear, m);
            boolean hover = mx >= ex && mx < ex + 200 && my >= ey && my < ey + 27;
            g.fill(ex, ey, ex + 200, ey + 27, hover ? 0x70403020 : 0x40201810);
            g.renderItem(new ItemStack(GearModItem.itemFor(m)), ex + 4, ey + 5);
            g.drawString(this.font, name(m), ex + 24, ey + 4, count > 0 || fitted ? 0xFFF4EAD8 : 0xFF7A7268, false);
            g.drawString(this.font, "x" + count, ex + 24, ey + 15, 0xFF9A9286, false);
            Component label;
            int color;
            if (fitted) {
                label = Component.translatable("gui.takemtitans.workshop.fitted");
                color = 0xFF7FD08A;
            } else if (gear.isEmpty() || count == 0) {
                label = Component.literal("-");
                color = 0xFF5A5248;
            } else if (free <= 0) {
                label = Component.translatable("gui.takemtitans.workshop.full");
                color = 0xFFE07A5A;
            } else {
                label = Component.translatable("gui.takemtitans.workshop.fit");
                color = 0;
            }
            int bw = this.font.width(label) + 12;
            int bx = ex + 200 - bw - 5;
            if (color == 0) {
                this.button(g, bx, ey + 6, bw, 15, label, mx, my, 40, m.ordinal());
            } else {
                g.drawString(this.font, label, bx + 6, ey + 10, color, false);
            }
            if (hover && !(mx >= bx && color == 0)) {
                List<Component> lines = new ArrayList<>();
                lines.add(name(m).copy().withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("tooltip.takemtitans.mod." + m.id()).withStyle(ChatFormatting.AQUA));
                if (count == 0 && !fitted) {
                    lines.add(Component.translatable("gui.takemtitans.workshop.none").withStyle(ChatFormatting.GRAY));
                }
                tip = lines;
            }
        }
        g.drawString(this.font, Component.translatable("gui.takemtitans.workshop.open", OdmKeys.WORKSHOP.getTranslatedKeyMessage()), x + 12, y + H - 14, 0xFF7A7268, false);
        if (this.guide) {
            this.drawGuide(g);
        } else if (tip != null) {
            g.renderComponentTooltip(this.font, tip, mx, my);
        }
    }

    /** The guide: what extras are, how to fit them, slots, pairings, and how to get more room. */
    private void drawGuide(GuiGraphics g) {
        int gw = 344;
        int gh = 236;
        int gx = (this.width - gw) / 2;
        int gy = (this.height - gh) / 2;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        g.fill(0, 0, this.width, this.height, 0xA0000000);
        panel(g, gx, gy, gw, gh);
        g.drawString(this.font, Component.translatable("gui.takemtitans.workshop.guide.title"), gx + 12, gy + 10, 0xFFF2D7A0, true);
        int ty = gy + 28;
        for (int i = 1; i <= 4; i++) {
            g.drawString(this.font, i + ".", gx + 12, ty, 0xFFC9A24A, false);
            ty = this.wrap(g, Component.translatable("gui.takemtitans.workshop.guide." + i), gx + 26, ty, gw - 40, 0xFFE8DCC8) + 7;
        }
        g.drawCenteredString(this.font, Component.translatable("gui.takemtitans.workshop.guide.close"), this.width / 2, gy + gh - 15, 0xFF9A9286);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.guide) {
            this.guide = false;
            return true;
        }
        for (int[] h : this.hits) {
            if (mx >= (double)h[0] && mx < (double)h[2] && my >= (double)h[1] && my < (double)h[3]) {
                if (h[4] == 1) {
                    this.guide = true;
                } else {
                    PacketDistributor.sendToServer(new OdmActionPayload(h[4], h[5]));
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, int mx, int my, int action, int value) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hover ? 0xFFC9A24A : 0xFF3A2E1E);
        frame(g, x, y, w, h, 0xFFC9A24A);
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, hover ? 0xFF1A140C : 0xFFF2D7A0);
        this.hits.add(new int[]{x, y, x + w, y + h, action, value});
    }

    private int wrap(GuiGraphics g, Component text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : this.font.split(text, w)) {
            g.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xE8100C0A);
        frame(g, x, y, w, h, 0xFF6A5A3A);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFE0A0);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static Component name(GearMod m) {
        return new ItemStack(GearModItem.itemFor(m)).getHoverName();
    }

    private static int count(Inventory inv, GearMod m) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof GearModItem gm && gm.mod == m) {
                n += s.getCount();
            }
        }
        return n;
    }
}
