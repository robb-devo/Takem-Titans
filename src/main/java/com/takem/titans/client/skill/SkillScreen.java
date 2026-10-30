package com.takem.titans.client.skill;

import com.mojang.math.Axis;
import com.takem.titans.client.gui.TTGui;
import com.takem.titans.item.BladeItem;
import com.takem.titans.item.ClusterSpearItem;
import com.takem.titans.item.DemolitionChargeItem;
import com.takem.titans.item.GasCanisterItem;
import com.takem.titans.item.OdmGearItem;
import com.takem.titans.item.ScoutCloakItem;
import com.takem.titans.item.ThermobaricItem;
import com.takem.titans.item.ThunderSpearItem;
import com.takem.titans.item.VanguardArmorItem;
import com.takem.titans.network.SkillActionPayload;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.ClientSkills;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.network.PacketDistributor;

public class SkillScreen extends Screen {
    private static final int COL_W = 80;
    private static final int ROW_H = 46;
    private final List<SkillScreen.Node> nodes = new ArrayList<>();
    private final List<float[]> sparks = new ArrayList<>();
    private final long opened = Util.getMillis();
    @Nullable
    private SkillScreen.Node hovered;
    private int ox;
    private int oy;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private boolean backgroundDrawn;

    public SkillScreen() {
        super(Component.translatable("skill.takemtitans.title"));
    }

    @Override
    protected void init() {
        this.nodes.clear();
        int totalW = Skill.BRANCHES.length * 80;
        this.ox = (this.width - totalW) / 2;
        this.oy = Math.max(70, (this.height - 244) / 2 + 20);
        this.panelX = this.ox - 14;
        this.panelY = this.oy - 34;
        this.panelW = totalW + 28;
        this.panelH = 256;

        for (Skill s : Skill.ALL) {
            int cx = this.ox + s.branch * 80 + 40 + s.lane * 22;
            int cy = this.oy + 8 + s.row * 46;
            this.nodes.add(new SkillScreen.Node(s, cx, cy));
        }

        this.addRenderableWidget(
            new SkillScreen.BrassButton(
                this.width / 2 - 70,
                Math.min(this.height - 22, this.panelY + this.panelH + 18),
                140,
                18,
                Component.translatable("skill.takemtitans.reset"),
                b -> PacketDistributor.sendToServer(new SkillActionPayload(1, 0))
            )
        );
    }

    private float time() {
        return (float)(Util.getMillis() - this.opened) / 1000.0F;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (!this.backgroundDrawn) {
            this.backgroundDrawn = true;
            float t = this.time();
            int accent = this.hovered != null ? Skill.BRANCH_COLORS[this.hovered.skill.branch] : 14721088;
            TTGui.uniform(TTGui.skillAura, "Time", t);
            TTGui.uniform(TTGui.skillAura, "Aspect", (float)this.width / (float)Math.max(1, this.height));
            TTGui.uniform(
                TTGui.skillAura, "Accent", (float)(accent >> 16 & 0xFF) / 255.0F, (float)(accent >> 8 & 0xFF) / 255.0F, (float)(accent & 0xFF) / 255.0F, 1.0F
            );
            if (!TTGui.shaderQuad(g, TTGui.skillAura, 0.0F, 0.0F, (float)this.width, (float)this.height)) {
                g.fillGradient(0, 0, this.width, this.height, -267383276, -266332144);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.backgroundDrawn = false;
        this.renderBackground(g, mx, my, pt);
        float t = this.time();
        float open = Mth.clamp(t * 3.0F, 0.0F, 1.0F);
        this.hovered = null;

        for (SkillScreen.Node n : this.nodes) {
            if (n.hit((double)mx, (double)my)) {
                this.hovered = n;
            }
        }

        int cx = this.width / 2;
        // the emblem sits above the title, clear of it
        TTGui.sprite(g, cx - 22, Math.max(2, this.panelY - 50), 44, 27, 0, 160, 62, 38, -1);
        g.pose().pushPose();
        g.pose().translate((float)cx, (float)(this.panelY - 12), 0.0F);
        g.pose().scale(1.4F, 1.4F, 1.0F);
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, title, -this.font.width(title) / 2, -4, -995200, true);
        g.pose().popPose();
        TTGui.panel(g, this.panelX, this.panelY, this.panelW, this.panelH);
        TTGui.sprite(g, cx - 64, this.panelY + this.panelH - 6, 128, 20, 128, 64, 128, 24, -1);
        Component pts = Component.translatable("skill.takemtitans.points", ClientSkills.points, ClientSkills.earned);
        g.drawCenteredString(this.font, pts, cx, this.panelY + this.panelH, ClientSkills.points > 0 ? -8080 : -5201792);

        for (int b = 0; b < Skill.BRANCHES.length; b++) {
            int col = Skill.BRANCH_COLORS[b];
            int bx = this.ox + b * 80 + 4;
            g.fill(bx + 40 - 5, this.oy - 4, bx + 40 - 3, this.oy + 184 + 20, 402653184 | col & 16777215);
            TTGui.sprite(g, bx, this.panelY + 10, 72, 15, 0, 96, 72, 18, 0xFF000000 | col);
            Component name = Component.translatable("skill.takemtitans.branch." + Skill.BRANCHES[b]);
            float sc = Math.min(1.0F, 58.0F / (float)Math.max(1, this.font.width(name)));
            g.pose().pushPose();
            g.pose().translate((float)(bx + 36), (float)(this.panelY + 14), 0.0F);
            g.pose().scale(sc, sc, 1.0F);
            g.drawString(this.font, name, -this.font.width(name) / 2, 0, -15724528, false);
            g.pose().popPose();
        }

        for (SkillScreen.Node nx : this.nodes) {
            for (Skill req : nx.skill.requires) {
                SkillScreen.Node m = this.node(req);
                if (m != null) {
                    boolean lit = ClientSkills.rank(req) > 0;
                    int c = lit ? 0xFF000000 | Skill.BRANCH_COLORS[req.branch] : 1619034240;
                    int mid = (m.cy + nx.cy) / 2;
                    g.fill(m.cx - 1, m.cy + 13, m.cx + 1, mid + 1, c);
                    g.fill(Math.min(m.cx, nx.cx) - 1, mid - 1, Math.max(m.cx, nx.cx) + 1, mid + 1, c);
                    g.fill(nx.cx - 1, mid, nx.cx + 1, nx.cy - 13, c);
                    if (lit) {
                        float k = (t * 0.8F + (float)(nx.cx + nx.cy) * 0.01F) % 1.0F;
                        int py = (int)Mth.lerp(k, (float)(m.cy + 13), (float)(nx.cy - 13));
                        int px = py < mid ? m.cx : nx.cx;
                        g.fill(px - 2, py - 2, px + 2, py + 2, -3904);
                    }
                }
            }
        }

        for (SkillScreen.Node nx : this.nodes) {
            nx.render(g, t, open, nx == this.hovered);
        }

        this.renderSparks(g);
        super.render(g, mx, my, pt);
        if (this.hovered != null) {
            this.tooltip(g, this.hovered, mx, my);
        }
    }

    @Nullable
    private SkillScreen.Node node(Skill s) {
        for (SkillScreen.Node n : this.nodes) {
            if (n.skill == s) {
                return n;
            }
        }

        return null;
    }

    private void renderSparks(GuiGraphics g) {
        float now = this.time();
        this.sparks.removeIf(sx -> now - sx[4] > 0.9F);

        for (float[] s : this.sparks) {
            float age = now - s[4];
            float x = s[0] + s[2] * age * 60.0F;
            float y = s[1] + s[3] * age * 60.0F + age * age * 40.0F;
            int a = (int)(255.0F * (1.0F - age / 0.9F));
            int c = a << 24 | (int)s[5] & 16777215;
            g.fill((int)x - 1, (int)y - 1, (int)x + 1, (int)y + 1, c);
        }
    }

    private void burst(SkillScreen.Node n) {
        float now = this.time();
        int col = Skill.BRANCH_COLORS[n.skill.branch];

        for (int i = 0; i < 28; i++) {
            double a = 6.283185307179586 * (double)i / 28.0 + Math.random() * 0.3;
            float sp = 0.6F + (float)Math.random() * 1.4F;
            this.sparks
                .add(
                    new float[]{(float)n.cx, (float)n.cy, (float)Math.cos(a) * sp, (float)Math.sin(a) * sp - 0.5F, now, i % 3 == 0 ? 1.6773296E7F : (float)col}
                );
        }
    }

    private void tooltip(GuiGraphics g, SkillScreen.Node n, int mx, int my) {
        Skill s = n.skill;
        int r = ClientSkills.rank(s);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(this.font.split(Component.translatable(s.nameKey()).withStyle(ChatFormatting.BOLD), 200));
        lines.addAll(this.font.split(Component.translatable(s.descKey()), 200));
        lines.add(Component.translatable("skill.takemtitans.rank", r, s.max).withStyle(ChatFormatting.GOLD).getVisualOrderText());
        if (s.minLevel > 1) {
            lines.add(
                Component.translatable("skill.takemtitans.req_level", s.minLevel)
                    .withStyle(ClientSkills.level >= s.minLevel ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED)
                    .getVisualOrderText()
            );
        }

        String why = blocker(s);
        if (why != null) {
            lines.add(Component.translatable(why).withStyle(ChatFormatting.RED).getVisualOrderText());
        } else {
            lines.add(Component.translatable("skill.takemtitans.click_learn").withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        }

        int w = 0;

        for (FormattedCharSequence l : lines) {
            w = Math.max(w, this.font.width(l));
        }

        int h = lines.size() * 10 + 10;
        int x = Math.min(mx + 14, this.width - w - 18);
        int y = Math.min(my + 6, this.height - h - 6);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        TTGui.hudPanel(g, x - 6, y - 5, w + 12, h);
        int ty = y;

        for (int i = 0; i < lines.size(); i++) {
            g.drawString(this.font, lines.get(i), x, ty, i == 0 ? 0xFF000000 | Skill.BRANCH_COLORS[s.branch] : -2041656, true);
            ty += 10;
        }

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (SkillScreen.Node n : this.nodes) {
                if (n.hit(mx, my)) {
                    String why = blocker(n.skill);
                    if (why != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F, 1.2F));
                        this.minecraft.player.displayClientMessage(Component.translatable(why), true);
                    } else {
                        PacketDistributor.sendToServer(new SkillActionPayload(0, n.skill.ordinal()));
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.LEVEL_UP.get(), 0.8F, 1.3F));
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 1.0F));
                        this.burst(n);
                    }

                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Nullable
    private static String blocker(Skill s) {
        int r = ClientSkills.rank(s);
        if (r >= s.max) {
            return "skill.takemtitans.why.maxed";
        } else if (ClientSkills.level < s.minLevel) {
            return "skill.takemtitans.why.level";
        } else if (!Skills.requirementsMet(ClientSkills.RANKS, s)) {
            return "skill.takemtitans.why.requires";
        } else {
            return ClientSkills.points <= 0 ? "skill.takemtitans.why.points" : null;
        }
    }

    private static ItemStack icon(Skill s) {
        return new ItemStack((ItemLike)(switch (s) {
            case SHARPENED_STEEL -> (BladeItem)ModItems.ODM_BLADES.get();
            case NAPE_SPECIALIST -> (BladeItem)ModItems.VETERAN_BLADES.get();
            case FLOWING_COMBO -> Items.FEATHER;
            case THUNDER_EXECUTION -> Items.LIGHTNING_ROD;
            case CRESCENT_CYCLONE -> Items.BREEZE_ROD;
            case ACKERMAN_AWAKENING -> (BladeItem)ModItems.ACKERMAN_BLADES.get();
            case GAS_EFFICIENCY -> (GasCanisterItem)ModItems.GAS_CANISTER.get();
            case REINFORCED_WINCH -> Items.CHAIN;
            case AFTERBURN_DASH -> Items.BLAZE_POWDER;
            case WRECKING_BALL -> Items.ANVIL;
            case PHANTOM_STEP -> Items.PHANTOM_MEMBRANE;
            case TITAN_WINCH -> (OdmGearItem)ModItems.GODSPEED_ODM_GEAR.get();
            case HEAVY_LANDING -> (VanguardArmorItem)ModItems.VANGUARD_BOOTS.get();
            case SHOCKWAVE -> Items.ECHO_SHARD;
            case DEMOLISHER -> (DemolitionChargeItem)ModItems.DEMOLITION_CHARGE.get();
            case METEOR_REBOUND -> Items.SLIME_BALL;
            case COMET_TRAIL -> Items.FIRE_CHARGE;
            case EXTINCTION_EVENT -> Items.MAGMA_BLOCK;
            case GAS_NOVA -> (ThermobaricItem)ModItems.THERMOBARIC_CANISTER.get();
            case VOLATILE_MIX -> Items.GUNPOWDER;
            case SPEAR_SPECIALIST -> (ThunderSpearItem)ModItems.THUNDER_SPEAR.get();
            case CHAIN_REACTION -> (ClusterSpearItem)ModItems.CLUSTER_SPEAR.get();
            case INCENDIARY_EDGE -> Items.FLINT_AND_STEEL;
            case SUPERNOVA -> Items.NETHER_STAR;
            case IRON_WILL -> Items.SHIELD;
            case SQUAD_LEADER -> (ScoutCloakItem)ModItems.SCOUT_CLOAK.get();
            case FIELD_MEDIC -> Items.GOLDEN_APPLE;
            case RALLY_CRY -> Items.GOAT_HORN;
            case VETERAN_INSTINCTS -> Items.SPYGLASS;
            case WINGS_OF_FREEDOM -> Items.ELYTRA;
            case SKYFALL_STRIKE -> Items.TRIDENT;
            case KILL_CHAIN -> Items.REDSTONE;
            case AFTERSHOCK -> Items.TNT;
            case EXECUTIONER -> Items.NETHERITE_SWORD;
            case STORM_DASH -> Items.WIND_CHARGE;
            case SKYBREAKER -> (BladeItem)ModItems.TITANBREAKER_BLADES.get();
            case BLADE_DANCER -> Items.DIAMOND_SWORD;
            case SKY_LORD -> Items.FEATHER;
            case CATACLYSM -> Items.DRAGON_HEAD;
            case INFERNO -> Items.BLAZE_ROD;
            case LEGION -> Items.TOTEM_OF_UNDYING;
            case GODSLAYER -> Items.NETHERITE_AXE;
        }));
    }

    static final class BrassButton extends Button {
        BrassButton(int x, int y, int w, int h, Component msg, OnPress press) {
            super(x, y, w, h, msg, press, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            TTGui.button(g, this.getX(), this.getY(), this.width, this.height, !this.active ? 2 : (this.isHoveredOrFocused() ? 1 : 0));
            Minecraft mc = Minecraft.getInstance();
            g.drawCenteredString(
                mc.font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, this.isHoveredOrFocused() ? -3920 : -1517392
            );
        }
    }

    private final class Node {
        final Skill skill;
        final int cx;
        final int cy;
        final ItemStack icon;

        Node(Skill skill, int cx, int cy) {
            this.skill = skill;
            this.cx = cx;
            this.cy = cy;
            this.icon = SkillScreen.icon(skill);
        }

        boolean hit(double mx, double my) {
            return Math.abs(mx - (double)this.cx) <= 13.0 && Math.abs(my - (double)this.cy) <= 13.0;
        }

        void render(GuiGraphics g, float t, float open, boolean hover) {
            int r = ClientSkills.rank(this.skill);
            boolean learned = r > 0;
            boolean can = SkillScreen.blocker(this.skill) == null;
            int col = Skill.BRANCH_COLORS[this.skill.branch];
            float pulse = 0.5F + 0.5F * Mth.sin(t * 3.0F + (float)this.cx * 0.05F);
            float s = open * (hover ? 1.14F : 1.0F);
            g.pose().pushPose();
            g.pose().translate((float)this.cx, (float)this.cy, 0.0F);
            g.pose().scale(s, s, 1.0F);
            if (this.skill.mastery()) {
                float spin = t * 40.0F;
                g.pose().pushPose();
                g.pose().mulPose(Axis.ZP.rotationDegrees(spin));
                TTGui.sprite(g, -22, -22, 44, 44, 96, 0, 32, 32, learned ? -1 : -1862270977);
                g.pose().popPose();
            }

            if (learned || can) {
                int a = (int)(255.0F * (learned ? 0.45F + 0.25F * pulse : 0.2F + 0.3F * pulse));
                int gc = learned ? col : 16762976;
                TTGui.sprite(g, -24, -24, 48, 48, 0, 32, 64, 64, a << 24 | gc);
            }

            int fu = this.skill.capstone() ? 96 : (learned ? 64 : (can ? 32 : 0));
            TTGui.sprite(g, -14, -14, 28, 28, fu, 0, 32, 32, learned ? -1 : (can ? -1 : -5197640));
            if (learned) {
                g.fill(-9, -9, 9, 9, 1342177280 | col & 16777215);
            }

            g.renderItem(this.icon, -8, -8);
            if (!learned && !can) {
                g.fill(-9, -9, 9, 9, -1877995500);
            }

            int pw = this.skill.max * 6 - 1;

            for (int i = 0; i < this.skill.max; i++) {
                TTGui.sprite(g, -pw / 2 + i * 6, 15, 5, 5, i < r ? 136 : 128, 0, 5, 5, -1);
            }

            g.pose().popPose();
        }
    }
}
