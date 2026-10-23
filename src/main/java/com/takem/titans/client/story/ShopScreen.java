package com.takem.titans.client.story;

import com.takem.titans.client.gui.TTGui;
import com.takem.titans.network.ShopBuyPayload;
import com.takem.titans.registry.ModItems;
import com.takem.titans.story.Shop;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.neoforged.neoforge.network.PacketDistributor;

public class ShopScreen extends Screen {
    private static final int CARD_W = 62;
    private static final int CARD_H = 58;
    private static final int COLS = 4;
    private static final int ROWS = 3;
    private static final ItemStack[] TAB_ICONS = new ItemStack[5];
    private final int npc;
    private final Component keeper;
    private final long opened = Util.getMillis();
    private int category;
    private int selected = -1;
    private int scroll;
    private float shownWallet;
    private final List<float[]> sparks = new ArrayList<>();
    private int px;
    private int py;
    private int pw;
    private int ph;
    private boolean bgDone;

    public ShopScreen(int npc, Component keeper) {
        super(Component.translatable("shop.takemtitans.title"));
        this.npc = npc;
        this.keeper = keeper;
        this.shownWallet = (float)StoryClient.wallet;
    }

    @Override
    protected void init() {
        TAB_ICONS[0] = new ItemStack(ModItems.GAS_CANISTER.get());
        TAB_ICONS[1] = new ItemStack(ModItems.THUNDER_SPEAR.get());
        TAB_ICONS[2] = new ItemStack(ModItems.ACKERMAN_BLADES.get());
        TAB_ICONS[3] = new ItemStack(ModItems.SIEGE_CANNON.get());
        TAB_ICONS[4] = new ItemStack(Items.NETHER_STAR);
        this.pw = Math.min(this.width - 20, 510);
        this.ph = Math.min(this.height - 20, 256);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2 + 6;
        if (this.selected < 0) {
            List<Integer> list = this.offers();
            this.selected = list.isEmpty() ? -1 : list.get(0);
        }
    }

    private List<Integer> offers() {
        List<Integer> out = new ArrayList<>();

        for (int i = 0; i < Shop.OFFERS.size(); i++) {
            if (Shop.OFFERS.get(i).category() == this.category) {
                out.add(i);
            }
        }

        return out;
    }

    private float time() {
        return (float)(Util.getMillis() - this.opened) / 1000.0F;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (!this.bgDone) {
            this.bgDone = true;
            TTGui.uniform(TTGui.skillAura, "Time", this.time());
            TTGui.uniform(TTGui.skillAura, "Aspect", (float)this.width / (float)Math.max(1, this.height));
            TTGui.uniform(TTGui.skillAura, "Accent", 0.95F, 0.7F, 0.25F, 1.0F);
            if (!TTGui.shaderQuad(g, TTGui.skillAura, 0.0F, 0.0F, (float)this.width, (float)this.height)) {
                g.fillGradient(0, 0, this.width, this.height, -535818732, -534767600);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.bgDone = false;
        this.renderBackground(g, mx, my, pt);
        float t = this.time();
        this.shownWallet = this.shownWallet + ((float)StoryClient.wallet - this.shownWallet) * 0.18F;
        TTGui.panel(g, this.px, this.py, this.pw, this.ph);
        g.pose().pushPose();
        g.pose().translate((float)(this.px + 16), (float)(this.py + 12), 0.0F);
        g.pose().scale(1.3F, 1.3F, 1.0F);
        g.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), 0, 0, -995200, true);
        g.pose().popPose();
        g.drawString(this.font, this.keeper, this.px + 16, this.py + 26, -4675448, false);
        int wx = this.px + this.pw - 120;
        TTGui.sprite(g, wx, this.py + 10, 104, 20, 128, 32, 100, 18, -1);
        g.renderItem(new ItemStack(ModItems.MERIT_BADGE.get()), wx + 4, this.py + 12);
        String w = String.valueOf(Math.round(this.shownWallet));
        g.drawString(this.font, w, wx + 24, this.py + 16, -8080, true);
        g.drawString(this.font, Component.translatable("shop.takemtitans.badges"), wx + 28 + this.font.width(w), this.py + 16, -5205920, false);
        int tx = this.px + 12;
        int ty = this.py + 44;

        for (int c = 0; c < Shop.CATEGORIES.length; c++) {
            int y = ty + c * 26;
            boolean hover = mx >= tx && mx < tx + 76 && my >= y && my < y + 22;
            TTGui.button(g, tx, y, 76, 22, this.category == c ? 1 : (hover ? 0 : 2));
            g.renderItem(TAB_ICONS[c], tx + 4, y + 3);
            Component name = Component.translatable("shop.takemtitans.cat." + Shop.CATEGORIES[c]);
            float sc = Math.min(1.0F, 48.0F / (float)Math.max(1, this.font.width(name)));
            g.pose().pushPose();
            g.pose().translate((float)(tx + 23), (float)(y + 8), 0.0F);
            g.pose().scale(sc, sc, 1.0F);
            g.drawString(this.font, name, 0, 0, this.category == c ? -3920 : -2570080, true);
            g.pose().popPose();
        }

        List<Integer> list = this.offers();
        int gx = this.px + 98;
        int gy = this.py + 44;
        int maxScroll = Math.max(0, (list.size() + 4 - 1) / 4 - 3);
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll);
        int hoveredOffer = -1;

        for (int k = 0; k < list.size(); k++) {
            int row = k / 4 - this.scroll;
            if (row >= 0 && row < 3) {
                int idx = list.get(k);
                Shop.Offer o = Shop.OFFERS.get(idx);
                int cx = gx + k % 4 * 66;
                int cy = gy + row * 62;
                boolean hover = mx >= cx && mx < cx + 62 && my >= cy && my < cy + 58;
                if (hover) {
                    hoveredOffer = idx;
                }

                boolean open = StoryClient.stage >= o.unlock();
                boolean afford = open && StoryClient.wallet >= o.price();
                if (idx == this.selected || hover) {
                    float pulse = 0.5F + 0.5F * Mth.sin(t * 4.0F);
                    TTGui.sprite(g, cx - 10, cy - 10, 82, 78, 0, 32, 64, 64, (int)(90.0F + 80.0F * pulse) << 24 | (idx == this.selected ? 16762976 : 16777215));
                }

                TTGui.hudPanel(g, cx, cy, 62, 58);
                ItemStack st = o.item().get();
                g.pose().pushPose();
                g.pose().translate((float)(cx + 31), (float)(cy + 20), 0.0F);
                float s = hover ? 1.9F : 1.7F;
                g.pose().scale(s, s, 1.0F);
                g.renderItem(st, -8, -8);
                g.renderItemDecorations(this.font, st, -8, -8);
                g.pose().popPose();
                Component name = st.getHoverName();
                float sc = Math.min(0.75F, 56.0F / (float)Math.max(1, this.font.width(name)));
                g.pose().pushPose();
                g.pose().translate((float)(cx + 31), (float)(cy + 36), 0.0F);
                g.pose().scale(sc, sc, 1.0F);
                g.drawString(this.font, name, -this.font.width(name) / 2, 0, -1516352, false);
                g.pose().popPose();
                String price = String.valueOf(o.price());
                int pwid = this.font.width(price) + 11;
                int ppx = cx + (62 - pwid) / 2;
                g.pose().pushPose();
                g.pose().translate((float)ppx, (float)(cy + 45), 0.0F);
                g.pose().scale(0.5F, 0.5F, 1.0F);
                g.renderItem(new ItemStack(ModItems.MERIT_BADGE.get()), 0, 0);
                g.pose().popPose();
                g.drawString(this.font, price, ppx + 11, cy + 46, afford ? -6492048 : (open ? -1023920 : -8357776), true);
                if (!open) {
                    g.fill(cx + 2, cy + 2, cx + 60, cy + 56, -1610612736);
                    Component lock = Component.translatable("shop.takemtitans.locked_short");
                    g.drawCenteredString(this.font, lock, cx + 31, cy + 16, -4673376);
                }
            }
        }

        if (maxScroll > 0) {
            int bh = 186;
            int sy = gy + (int)((float)this.scroll / (float)maxScroll * (float)(bh - 20));
            g.fill(gx + 264, gy, gx + 264 + 2, gy + bh, 1090519039);
            g.fill(gx + 264, sy, gx + 264 + 2, sy + 20, -2052000);
        }

        this.renderDetail(g, mx, my, t);
        this.renderSparks(g);
        super.render(g, mx, my, pt);
        if (hoveredOffer >= 0 && hoveredOffer != this.selected) {
            g.renderTooltip(this.font, Shop.OFFERS.get(hoveredOffer).item().get(), mx, my);
        }
    }

    private int dx() {
        return this.px + 98 + 264 + 8;
    }

    private void renderDetail(GuiGraphics g, int mx, int my, float t) {
        if (this.selected >= 0) {
            Shop.Offer o = Shop.OFFERS.get(this.selected);
            ItemStack st = o.item().get();
            int x = this.dx();
            int w = this.px + this.pw - 12 - x;
            int y = this.py + 44;
            int h = this.ph - 56;
            TTGui.hudPanel(g, x, y, w, h);
            TTGui.sprite(g, x + w / 2 - 34, y + 4, 68, 68, 0, 32, 64, 64, 1895811168);
            g.pose().pushPose();
            g.pose().translate((float)(x + w / 2), (float)(y + 36) + Mth.sin(t * 2.0F) * 2.0F, 100.0F);
            g.pose().scale(3.2F, 3.2F, 1.0F);
            g.renderItem(st, -8, -8);
            g.pose().popPose();
            g.drawCenteredString(this.font, st.getHoverName(), x + w / 2, y + 66, -5968);
            List<Component> tip = st.getTooltipLines(TooltipContext.of(this.minecraft.level), this.minecraft.player, TooltipFlag.NORMAL);
            int ly = y + 80;

            for (int i = 1; i < tip.size() && ly < y + h - 50; i++) {
                for (FormattedCharSequence line : this.font.split(tip.get(i), w - 12)) {
                    if (ly < y + h - 50) {
                        g.drawString(this.font, line, x + 6, ly, -4673376, false);
                        ly += 10;
                    }
                }
            }

            boolean open = StoryClient.stage >= o.unlock();
            boolean afford = open && StoryClient.wallet >= o.price();
            boolean five = open && (st.getMaxStackSize() > 1 || o.price() * 5 <= StoryClient.wallet);
            int by = y + h - 44;
            if (!open) {
                Component when = Component.translatable("shop.takemtitans.unlocks", Component.translatable(com.takem.titans.story.Story.chapterKey(o.unlock())));
                int ty = by - 12;
                for (FormattedCharSequence line : this.font.split(when, w - 12)) {
                    g.drawCenteredString(this.font, line, x + w / 2, ty, -2052000);
                    ty -= 10;
                }
            }

            this.buyButton(g, x + 6, by, w - 12, Component.translatable("shop.takemtitans.buy", o.price()), afford, mx, my);
            this.buyButton(
                g, x + 6, by + 22, w - 12, Component.translatable("shop.takemtitans.buy5", o.price() * 5), StoryClient.wallet >= o.price() * 5 && five, mx, my
            );
        }
    }

    private void buyButton(GuiGraphics g, int x, int y, int w, Component label, boolean ok, int mx, int my) {
        boolean hover = ok && mx >= x && mx < x + w && my >= y && my < y + 18;
        TTGui.button(g, x, y, w, 18, ok ? (hover ? 1 : 0) : 2);
        g.drawCenteredString(this.font, label, x + w / 2, y + 5, ok ? (hover ? -2880 : -1517392) : -8357776);
    }

    private void renderSparks(GuiGraphics g) {
        float now = this.time();
        this.sparks.removeIf(sx -> now - sx[4] > 0.8F);

        for (float[] s : this.sparks) {
            float age = now - s[4];
            float x = s[0] + s[2] * age * 60.0F;
            float y = s[1] + s[3] * age * 60.0F + age * age * 50.0F;
            int a = (int)(255.0F * (1.0F - age / 0.8F));
            g.fill((int)x - 1, (int)y - 1, (int)x + 1, (int)y + 1, a << 24 | 16765024);
        }
    }

    private void buy(int count, int sx, int sy) {
        Shop.Offer o = Shop.OFFERS.get(this.selected);
        if (StoryClient.stage >= o.unlock() && StoryClient.wallet >= o.price() * count) {
            PacketDistributor.sendToServer(new ShopBuyPayload(this.npc, this.selected, count));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 1.0F));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.0F, 0.8F));
            float now = this.time();

            for (int i = 0; i < 20; i++) {
                double a = 6.283185307179586 * (double)i / 20.0;
                this.sparks.add(new float[]{(float)sx, (float)sy, (float)Math.cos(a) * 1.4F, (float)Math.sin(a) * 1.4F - 0.6F, now});
            }
        } else {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F, 1.0F));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int tx = this.px + 12;
            int ty = this.py + 44;

            for (int c = 0; c < Shop.CATEGORIES.length; c++) {
                int y = ty + c * 26;
                if (mx >= (double)tx && mx < (double)(tx + 76) && my >= (double)y && my < (double)(y + 22)) {
                    if (this.category != c) {
                        this.category = c;
                        this.scroll = 0;
                        List<Integer> list = this.offers();
                        this.selected = list.isEmpty() ? -1 : list.get(0);
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F));
                    }

                    return true;
                }
            }

            List<Integer> list = this.offers();
            int gx = this.px + 98;
            int gy = this.py + 44;

            for (int k = 0; k < list.size(); k++) {
                int row = k / 4 - this.scroll;
                if (row >= 0 && row < 3) {
                    int cx = gx + k % 4 * 66;
                    int cy = gy + row * 62;
                    if (mx >= (double)cx && mx < (double)(cx + 62) && my >= (double)cy && my < (double)(cy + 58)) {
                        if (this.selected == list.get(k) && hasShiftDown()) {
                            this.buy(1, (int)mx, (int)my);
                        }

                        this.selected = list.get(k);
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.4F));
                        return true;
                    }
                }
            }

            if (this.selected >= 0) {
                int x = this.dx();
                int w = this.px + this.pw - 12 - x;
                int by = this.py + 44 + (this.ph - 56) - 44;
                if (mx >= (double)(x + 6) && mx < (double)(x + w - 6)) {
                    if (my >= (double)by && my < (double)(by + 18)) {
                        this.buy(1, (int)mx, (int)my);
                        return true;
                    }

                    if (my >= (double)(by + 22) && my < (double)(by + 40)) {
                        this.buy(5, (int)mx, (int)my);
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        this.scroll = this.scroll - (int)Math.signum(dy);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
