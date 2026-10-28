package com.takem.titans.client.gui;

import com.takem.titans.network.OdmActionPayload;
import com.takem.titans.world.ShiganshinaLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The map of the walls, drawn in light: Wall Maria, Rose and Sina and every district ring trace themselves in, the
 * waystones rise onto it as glowing marks, and a line runs from where you stand to the one under the cursor. Grey
 * marks are stones not yet woken.
 */
public class WaystoneScreen extends Screen {
    private static final double MIN_X = -1080.0;
    private static final double MAX_X = 1080.0;
    private static final double MIN_Z = -2140.0;
    private static final double MAX_Z = 920.0;
    private final List<WaystoneScreen.Mark> marks = new ArrayList<>();
    private final String here;
    private int ticks;
    private int px;
    private int py;
    private int pw;
    private int ph;

    public WaystoneScreen(CompoundTag tag) {
        super(Component.translatable("waystone.takemtitans.title"));
        this.here = tag.getString("here");
        ListTag list = tag.getList("list", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            this.marks.add(new WaystoneScreen.Mark(i, e.getString("n"), e.getInt("x"), e.getInt("z"), e.getBoolean("on")));
        }
    }

    @Override
    protected void init() {
        this.ph = Math.min(this.height - 60, 300);
        this.pw = (int)((double)this.ph * (MAX_X - MIN_X) / (MAX_Z - MIN_Z));
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2 + 6;
    }

    @Override
    public void tick() {
        this.ticks++;
    }

    private float sx(double x) {
        return (float)this.px + (float)((x - MIN_X) / (MAX_X - MIN_X)) * (float)this.pw;
    }

    private float sy(double z) {
        return (float)this.py + (float)((z - MIN_Z) / (MAX_Z - MIN_Z)) * (float)this.ph;
    }

    /** A circle on the map, traced in up to {@code reveal} of its length, made of short dots. */
    private void circle(GuiGraphics g, double cx, double cz, double r, float reveal, int argb) {
        int n = Math.max(24, (int)(r * 0.35));
        int shown = Math.round((float)n * Mth.clamp(reveal, 0.0F, 1.0F));

        for (int i = 0; i < shown; i++) {
            double a = (double)i / (double)n * Math.PI * 2.0 - Math.PI / 2.0;
            int x = Math.round(this.sx(cx + Math.cos(a) * r));
            int y = Math.round(this.sy(cz + Math.sin(a) * r));
            g.fill(x, y, x + 1, y + 1, argb);
        }
    }

    private WaystoneScreen.Mark hovered(int mx, int my) {
        for (WaystoneScreen.Mark m : this.marks) {
            float x = this.sx((double)m.x);
            float y = this.sy((double)m.z);
            if (Math.abs((float)mx - x) <= 7.0F && Math.abs((float)my - y) <= 7.0F) {
                return m;
            }
        }

        return null;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, this.width, this.height, 0xC0050610, 0xE0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        float t = (float)this.ticks + pt;
        // the parchment of the map, framed
        g.fill(this.px - 8, this.py - 8, this.px + this.pw + 8, this.py + this.ph + 8, 0xFF1A1208);
        g.fillGradient(this.px - 6, this.py - 6, this.px + this.pw + 6, this.py + this.ph + 6, 0xFF2C2414, 0xFF1C170C);
        int line = 0xC0E8D9A8;
        float reveal = t / 22.0F;
        this.circle(g, 0.0, ShiganshinaLayout.MARIA_Z, ShiganshinaLayout.MARIA_R, reveal, line);
        this.circle(g, 0.0, ShiganshinaLayout.ROSE_Z, ShiganshinaLayout.ROSE_R, reveal - 0.15F, line);
        this.circle(g, 0.0, ShiganshinaLayout.SINA_Z, ShiganshinaLayout.SINA_R, reveal - 0.3F, line);

        for (ShiganshinaLayout.Bulge b : ShiganshinaLayout.BULGES) {
            this.circle(g, b.cx(), b.cz(), b.r(), reveal - 0.45F, b.ring() == ShiganshinaLayout.ARENA_RING ? 0xD0C58CFF : 0xA0D8C890);
        }

        String title = this.title.getString();
        g.drawString(this.font, Component.literal(title).withStyle(ChatFormatting.BOLD), (this.width - this.font.width(title)) / 2, this.py - 22, 0xFFE8D9A8, true);
        WaystoneScreen.Mark hover = this.hovered(mx, my);
        WaystoneScreen.Mark self = null;

        for (WaystoneScreen.Mark m : this.marks) {
            if (m.zone.equals(this.here)) {
                self = m;
            }
        }

        // a line of light runs from here to the chosen stone
        if (self != null && hover != null && hover != self && hover.on) {
            float x0 = this.sx((double)self.x);
            float y0 = this.sy((double)self.z);
            float x1 = this.sx((double)hover.x);
            float y1 = this.sy((double)hover.z);
            int steps = (int)(Math.hypot((double)(x1 - x0), (double)(y1 - y0)) / 3.0);

            for (int i = 0; i <= steps; i++) {
                float f = (float)i / (float)Math.max(1, steps);
                float wave = (f * 6.0F - t * 0.25F) % 1.0F;
                int a = (int)(90.0F + 160.0F * (wave < 0.0F ? wave + 1.0F : wave));
                int x = Math.round(Mth.lerp(f, x0, x1));
                int y = Math.round(Mth.lerp(f, y0, y1));
                g.fill(x, y, x + 1, y + 1, a << 24 | 0xC58CFF);
            }
        }

        for (WaystoneScreen.Mark m : this.marks) {
            // each stone rises onto the map a little after the walls are drawn
            float pop = Mth.clamp((t - 18.0F - (float)m.index * 2.0F) / 6.0F, 0.0F, 1.0F);
            if (pop > 0.0F) {
                int x = Math.round(this.sx((double)m.x));
                int y = Math.round(this.sy((double)m.z));
                boolean isHere = m == self;
                boolean over = m == hover;
                float pulse = 0.5F + 0.5F * Mth.sin(t * 0.25F + (float)m.index);
                int col = !m.on ? 0xFF6E6A62 : (isHere ? 0xFFFFD27A : 0xFFC58CFF);
                int r = Math.round((over ? 5.0F : 3.0F) * pop + (isHere ? pulse * 1.5F : 0.0F));
                g.fill(x - r - 1, y - r - 1, x + r + 2, y + r + 2, 0x80000000);
                g.fill(x - r, y - r, x + r + 1, y + r + 1, col);
                if (m.on) {
                    g.fill(x - 1, y - r - 3 - Math.round(pulse * 2.0F), x + 2, y - r, col & 0x80FFFFFF);
                }

                String name = Component.translatable("zone.takemtitans." + m.zone).getString();
                int nx = x + 9;
                if (nx + this.font.width(name) > this.px + this.pw + 6) {
                    nx = x - 9 - this.font.width(name);
                }

                g.drawString(this.font, name, nx, y - 4, over ? 0xFFFFFFFF : (m.on ? 0xFFE8D9A8 : 0xFF8A8478), true);
                if (over) {
                    String sub = isHere
                        ? Component.translatable("waystone.takemtitans.here").getString()
                        : (!m.on ? Component.translatable("waystone.takemtitans.asleep").getString() : "");
                    if (!sub.isEmpty()) {
                        g.drawString(this.font, sub, nx, y + 6, 0xFFB0A890, false);
                    }
                }
            }
        }

        String hint = Component.translatable("waystone.takemtitans.hint").getString();
        g.drawString(this.font, hint, (this.width - this.font.width(hint)) / 2, this.py + this.ph + 14, 0xA0D8CFBC, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        WaystoneScreen.Mark m = this.hovered((int)mx, (int)my);
        if (m != null && m.on && !m.zone.equals(this.here)) {
            PacketDistributor.sendToServer(new OdmActionPayload(24, m.index));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F));
            this.onClose();
            return true;
        } else {
            return super.mouseClicked(mx, my, button);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static record Mark(int index, String zone, int x, int z, boolean on) {
    }
}
