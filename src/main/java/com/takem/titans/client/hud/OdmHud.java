package com.takem.titans.client.hud;

import com.mojang.math.Axis;
import com.takem.titans.TakemConfig;
import com.takem.titans.TakemTitans;
import com.takem.titans.client.ClientEffects;
import com.takem.titans.client.ClientOdm;
import com.takem.titans.client.ClientTitanState;
import com.takem.titans.client.OdmKeys;
import com.takem.titans.client.gui.TTGui;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanHit;
import com.takem.titans.entity.titan.TitanPart;
import com.takem.titans.item.BladeItem;
import com.takem.titans.odm.HookState;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.odm.OdmTuning;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class OdmHud {
    public static final ResourceLocation PANEL = TakemTitans.id("odm_panel");
    public static final ResourceLocation SIGHTS = TakemTitans.id("odm_sights");
    public static final ResourceLocation SPEED_LINES = TakemTitans.id("speed_lines");
    public static final ResourceLocation HIT_FX = TakemTitans.id("hit_fx");
    private static final List<OdmHud.Popup> POPUPS = new ArrayList<>();
    private static int bladeBreakFlash;
    private static int kills = -1;
    private static int hudTicks;
    private static final int HINT_DURATION = 1200;
    private static final float HINT_SCALE = 0.75F;
    private static int hintStart = -1;
    private static float smoothGas = -1.0F;
    private static int combo;
    private static int comboMult = 100;
    private static int comboAge = 1000;
    private static int comboFade;
    private static float comboPop;
    private static float comboPopO;

    private OdmHud() {
    }

    public static void addHit(int damage, int part, int sizeTimes10) {
        boolean nape = part == 1;
        Component text = Component.translatable(TitanHit.partKey(part)).append(" " + damage);
        int color = nape ? -11443 : (part == 5 ? -5011201 : -30080);
        POPUPS.add(new OdmHud.Popup(text, color, nape ? 1.5F : 1.0F, 26, false));
        trim();
    }

    public static void addKill(int damage, int kind, int totalKills) {
        kills = totalKills;
        POPUPS.add(new OdmHud.Popup(Component.translatable("hud.takemtitans.titan_slain"), -16121, 2.2F, 55, true));
        POPUPS.add(new OdmHud.Popup(Component.translatable(TitanHit.kindKey(kind)).append(" · " + damage), -1, 1.0F, 55, true));
        trim();
    }

    public static void setCombo(int count, int multX100) {
        if (count <= 0) {
            if (combo > 0) {
                comboFade = 14;
            }
        } else {
            if (count > combo || comboFade > 0) {
                comboPop = 1.0F;
            }

            combo = count;
            comboMult = multX100;
            comboAge = 0;
            comboFade = 0;
        }
    }

    public static void overdriveStarted() {
        POPUPS.add(new OdmHud.Popup(Component.translatable("hud.takemtitans.overdrive"), -56776, 2.6F, 50, true));
        POPUPS.add(new OdmHud.Popup(Component.translatable("hud.takemtitans.overdrive_sub"), -12080, 1.0F, 50, true));
        trim();
    }

    public static void bladesBroke() {
        bladeBreakFlash = 24;
    }

    private static void trim() {
        while (POPUPS.size() > 8) {
            POPUPS.remove(0);
        }
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            POPUPS.clear();
            kills = -1;
        } else if (!mc.isPaused()) {
            hudTicks++;
            POPUPS.removeIf(p -> ++p.age > p.lifetime);
            comboAge++;
            comboPopO = comboPop;
            comboPop *= 0.7F;
            if (comboFade > 0 && --comboFade == 0) {
                combo = 0;
            }

            if (bladeBreakFlash > 0) {
                bladeBreakFlash--;
            }

            if (ClientTitanState.isGrabbed()) {
                ClientTitanState.grabTicks++;
            }

            ItemStack gear = OdmGear.getGear(mc.player);
            float g = gear.isEmpty() ? 0.0F : OdmGear.gasFraction(gear);
            smoothGas = smoothGas < 0.0F ? g : smoothGas + (g - smoothGas) * 0.35F;
        }
    }

    private static boolean hidden(Minecraft mc) {
        return mc.options.hideGui || mc.player == null || mc.player.isSpectator();
    }

    public static void renderPanel(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!hidden(mc)) {
            LocalPlayer p = mc.player;
            ItemStack gear = OdmGear.getGear(p);
            Font font = mc.font;
            float pt = delta.getGameTimeDeltaPartialTick(false);
            int sw = g.guiWidth();
            int sh = g.guiHeight();
            renderPopups(g, font, sw, sh, pt);
            renderCombo(g, font, sw, sh, pt);
            renderPrompts(g, font, p, gear, sw, sh);
            if (!gear.isEmpty()) {
                float scale = TakemConfig.HUD_SCALE.get().floatValue();
                int pw = 86;
                int ph = 56;
                g.pose().pushPose();
                g.pose().translate((float)(sw - 6) - (float)pw * scale, (float)(sh - 6) - (float)ph * scale, 0.0F);
                g.pose().scale(scale, scale, 1.0F);
                TTGui.hudPanel(g, -3, -3, pw + 6, ph + 6);
                ClientOdm odm = ClientOdm.INSTANCE;
                int kmh = (int)Math.round(OdmTuning.toKmh(odm.speed));
                boolean flying = odm.inFlight();
                String speed = Integer.toString(kmh);
                g.drawString(font, speed, 5, 4, flying ? -1 : -6248272, true);
                g.pose().pushPose();
                g.pose().translate((float)(6 + font.width(speed)), 6.5F, 0.0F);
                g.pose().scale(0.6F, 0.6F, 1.0F);
                g.drawString(font, "km/h", 0, 0, -6248272, false);
                g.pose().popPose();
                if (kills >= 0) {
                    String k = "☠ " + kills;
                    g.drawString(font, k, pw - 5 - font.width(k), 4, -2047872, true);
                }

                float gas = smoothGas < 0.0F ? OdmGear.gasFraction(gear) : smoothGas;
                boolean low = gas < 0.15F;
                boolean blink = low && hudTicks / 6 % 2 == 0;
                int gasColor = gas > 0.5F ? -10497025 : (gas > 0.15F ? -16307 : -45747);
                drawTank(g, 5, 15, 10, 30, gas, gasColor, odm.boosting, blink);
                drawTank(g, 18, 15, 10, 30, gas, gasColor, odm.boosting, blink);
                String pct = Math.round(gas * 100.0F) + "%";
                drawSmall(g, font, pct, 16.5F - (float)font.width(pct) * 0.35F, 47.5F, low ? -38037 : -2563350);
                ItemStack blades = p.getMainHandItem();
                float sharp = 0.0F;
                boolean broken = false;
                if (blades.getItem() instanceof BladeItem) {
                    sharp = 1.0F - (float)blades.getDamageValue() / (float)Math.max(1, blades.getMaxDamage());
                    broken = BladeItem.isBroken(blades);
                }

                int bx = 34;
                g.fill(bx, 15, bx + 5, 45, -14670292);
                int sharpH = Math.round(28.0F * sharp);
                int sharpColor = broken ? -45747 : (sharp > 0.35F ? -1511694 : -18611);
                if (bladeBreakFlash > 0 && bladeBreakFlash / 3 % 2 == 0) {
                    sharpColor = -57312;
                }

                g.fill(bx + 1, 44 - sharpH, bx + 4, 44, sharpColor);

                for (int i = 1; i < 4; i++) {
                    g.fill(bx + 1, 16 + i * 7, bx + 4, 16 + i * 7 + 1, 1610612736);
                }

                int spare = OdmGear.getSpareBlades(gear);
                int max = Math.max(1, OdmGear.maxSpareBlades());
                int shown = Math.min(8, max);

                for (int i = 0; i < shown; i++) {
                    int col = i % 2;
                    int row = i / 2;
                    int x = 44 + col * 20;
                    int y = 16 + row * 7;
                    boolean lit = i < Math.round((float)(spare * shown) / (float)max);
                    drawBladeIcon(g, x, y, lit);
                }

                String count = spare + "/" + max;
                drawSmall(g, font, count, 64.0F - (float)font.width(count) * 0.35F, 47.5F, spare == 0 ? -38037 : -2563350);
                g.pose().popPose();
            }
        }
    }

    private static void drawTank(GuiGraphics g, int x, int y, int w, int h, float frac, int color, boolean boosting, boolean blink) {
        int outline = blink ? -45747 : -7694429;
        g.fill(x + 1, y, x + w - 1, y + h, outline);
        g.fill(x, y + 1, x + w, y + h - 1, outline);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, -15328222);
        int innerH = h - 4;
        int fillH = Math.round((float)innerH * Mth.clamp(frac, 0.0F, 1.0F));
        if (fillH > 0) {
            int top = y + 2 + innerH - fillH;
            int light = brighten(color, boosting && System.currentTimeMillis() / 60L % 2L == 0L ? 0.45F : 0.25F);
            g.fillGradient(x + 2, top, x + w - 2, y + h - 2, light, color);
            g.fill(x + 3, top, x + 4, y + h - 3, 1358954495);
        }

        g.fill(x + w / 2 - 2, y - 2, x + w / 2 + 2, y, outline);
    }

    private static void drawBladeIcon(GuiGraphics g, int x, int y, boolean lit) {
        int c = lit ? -2234642 : -12959159;
        int edge = lit ? -1 : -11906213;
        int handle = lit ? -9745869 : -13424353;
        g.fill(x, y + 2, x + 4, y + 4, handle);

        for (int i = 0; i < 12; i++) {
            int yy = y + (i < 6 ? 1 : 2) - (i > 9 ? 1 : 0);
            g.fill(x + 4 + i, yy, x + 5 + i, y + 4, c);
        }

        g.fill(x + 4, y + 1, x + 16, y + 2, edge);
    }

    private static int brighten(int argb, float t) {
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int gg = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        r = (int)((float)r + (float)(255 - r) * t);
        gg = (int)((float)gg + (float)(255 - gg) * t);
        b = (int)((float)b + (float)(255 - b) * t);
        return a << 24 | r << 16 | gg << 8 | b;
    }

    private static void drawSmall(GuiGraphics g, Font font, String text, float x, float y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(0.7F, 0.7F, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static void renderPrompts(GuiGraphics g, Font font, LocalPlayer p, ItemStack gear, int sw, int sh) {
        int y = sh - 72;
        boolean pulse = hudTicks / 8 % 2 == 0;
        if (ClientTitanState.isGrabbed()) {
            float shake = Mth.sin((float)hudTicks * 1.7F) * 2.0F;
            g.pose().pushPose();
            g.pose().translate((float)sw / 2.0F + shake, (float)sh / 2.0F + 30.0F, 0.0F);
            g.pose().scale(1.6F, 1.6F, 1.0F);
            g.drawCenteredString(font, Component.translatable("hud.takemtitans.grabbed").withStyle(ChatFormatting.BOLD), 0, 0, -49088);
            g.pose().popPose();
            g.drawCenteredString(
                font,
                Component.translatable("hud.takemtitans.struggle", Minecraft.getInstance().options.keyAttack.getTranslatedKeyMessage()),
                sw / 2,
                sh / 2 + 48,
                -1
            );
            int w = 100;
            float prog = Math.min(1.0F, (float)ClientTitanState.struggleClicks / 14.0F);
            g.fill(sw / 2 - w / 2 - 1, sh / 2 + 60, sw / 2 + w / 2 + 1, sh / 2 + 66, -16777216);
            g.fill(sw / 2 - w / 2, sh / 2 + 61, sw / 2 - w / 2 + Math.round((float)w * prog), sh / 2 + 65, -16121);
        } else if (!gear.isEmpty()) {
            ItemStack blades = p.getMainHandItem();
            if (blades.getItem() instanceof BladeItem && BladeItem.isBroken(blades)) {
                Component msg = OdmGear.getSpareBlades(gear) > 0
                    ? Component.translatable("hud.takemtitans.reload", OdmKeys.label(OdmKeys.RELOAD, null))
                    : Component.translatable("hud.takemtitans.no_spare_blades");
                g.drawCenteredString(font, msg, sw / 2, y, pulse ? -42406 : -20304);
                y -= 11;
            }

            if (OdmGear.getGas(gear) <= 0 && !p.getAbilities().instabuild) {
                g.drawCenteredString(font, Component.translatable("hud.takemtitans.out_of_gas"), sw / 2, y, pulse ? -42406 : -20304);
            } else if (OdmGear.gasFraction(gear) < 0.15F && !p.getAbilities().instabuild) {
                g.drawCenteredString(font, Component.translatable("hud.takemtitans.gas_low"), sw / 2, y, pulse ? -16307 : -8032);
            }

            ClientOdm odm = ClientOdm.INSTANCE;
            if (OdmGear.holdingBlades(p) && hintStart < 0) {
                hintStart = p.tickCount;
            }

            if (hintStart > p.tickCount) {
                hintStart = p.tickCount;
            }

            if (TakemConfig.SHOW_CONTROLS_HINT.get()
                && !odm.everSwung
                && hintStart >= 0
                && p.tickCount - hintStart < 1200
                && OdmGear.holdingBlades(p)
                && p.onGround()) {
                Options o = Minecraft.getInstance().options;
                String[][] lines = new String[][]{
                    {OdmKeys.label(OdmKeys.HOOK_LEFT, null) + " / " + OdmKeys.label(OdmKeys.HOOK_RIGHT, null), "hud.takemtitans.hint.hooks"},
                    {o.keyUse.getTranslatedKeyMessage().getString(), "hud.takemtitans.hint.both"},
                    {OdmKeys.label(OdmKeys.BOOST, o.keyJump), "hud.takemtitans.hint.boost"},
                    {OdmKeys.label(OdmKeys.DASH, o.keySprint), "hud.takemtitans.hint.dash"},
                    {OdmKeys.label(OdmKeys.WINCH_LOCK, o.keyShift), "hud.takemtitans.hint.lock"},
                    {OdmKeys.label(OdmKeys.RELOAD, null), "hud.takemtitans.hint.reload"},
                    {o.keyAttack.getTranslatedKeyMessage().getString(), "hud.takemtitans.hint.slash"},
                    {OdmKeys.label(OdmKeys.DASH, o.keySprint) + " x2", "hud.takemtitans.hint.spin"},
                    {"Hold " + o.keyAttack.getTranslatedKeyMessage().getString(), "hud.takemtitans.hint.rush"}
                };
                int keyW = 0;
                int textW = 0;

                for (String[] line : lines) {
                    keyW = Math.max(keyW, font.width("[" + line[0] + "]"));
                    textW = Math.max(textW, font.width(Component.translatable(line[1])));
                }

                g.pose().pushPose();
                g.pose().scale(0.75F, 0.75F, 1.0F);
                int hy = (int)((float)sh / 0.75F / 2.0F) - lines.length * 6;
                g.fill(4, hy - 14, 8 + keyW + 6 + textW + 4, hy + lines.length * 11 + 2, 1879048192);
                g.drawString(font, Component.translatable("hud.takemtitans.hint.title").withStyle(ChatFormatting.BOLD), 8, hy - 11, -11443, true);

                for (String[] line : lines) {
                    g.drawString(font, "[" + line[0] + "]", 8, hy, -6629121, true);
                    g.drawString(font, Component.translatable(line[1]), 8 + keyW + 6, hy, -1513240, true);
                    hy += 11;
                }

                g.pose().popPose();
            }
        }
    }

    private static void renderCombo(GuiGraphics g, Font font, int sw, int sh, float pt) {
        if (combo >= 2 && comboAge <= 80) {
            float alpha = comboFade > 0 ? ((float)comboFade - pt) / 14.0F : Mth.clamp(((float)(80 - comboAge) - pt) / 12.0F, 0.0F, 1.0F);
            if (!(alpha <= 0.02F)) {
                float pop = Mth.lerp(pt, comboPopO, comboPop);
                float heat = Mth.clamp((float)(combo - 2) / 10.0F, 0.0F, 1.0F);
                int r = 255;
                int gC = (int)Mth.lerp(heat, 240.0F, 70.0F);
                int b = (int)Mth.lerp(heat, 200.0F, 40.0F);
                int a = Math.max(4, (int)(alpha * 255.0F));
                int color = a << 24 | r << 16 | gC << 8 | b;
                float x = (float)sw / 2.0F + 48.0F;
                float y = (float)sh / 2.0F - 8.0F;
                String num = "×" + combo;
                g.pose().pushPose();
                g.pose().translate(x, y, 0.0F);
                float s = 2.2F * (1.0F + pop * 0.45F);
                g.pose().scale(s, s, 1.0F);
                g.pose().mulPose(Axis.ZP.rotationDegrees(-6.0F + pop * 8.0F));
                g.drawString(font, num, 0, 0, color, true);
                g.pose().popPose();
                Component label = Component.translatable("hud.takemtitans.combo").append(" +" + (comboMult - 100) + "%");
                g.pose().pushPose();
                g.pose().translate(x + 1.0F, y + 21.0F, 0.0F);
                g.pose().scale(0.8F, 0.8F, 1.0F);
                g.drawString(font, label, 0, 0, a << 24 | 16777215, true);
                g.pose().popPose();
            }
        }
    }

    public static void renderHitFx(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.hideGui) {
            float pt = delta.getGameTimeDeltaPartialTick(false);
            int sw = g.guiWidth();
            int sh = g.guiHeight();
            renderOverdrive(g, sw, sh, pt);
            float f = Mth.lerp(pt, ClientEffects.flashO, ClientEffects.flash);
            if (f > 0.01F) {
                int rgb = ClientEffects.flashColor & 16777215;
                int bands = 8;
                int bw = Math.max(2, (int)((float)Math.min(sw, sh) * 0.03F));

                for (int i = 0; i < bands; i++) {
                    float k = 1.0F - (float)i / (float)bands;
                    int a = (int)(Mth.clamp(f, 0.0F, 1.0F) * k * k * 255.0F);
                    if (a > 2) {
                        int c = a << 24 | rgb;
                        int in = i * bw;
                        g.fill(in, in, sw - in, in + bw, c);
                        g.fill(in, sh - in - bw, sw - in, sh - in, c);
                        g.fill(in, in + bw, in + bw, sh - in - bw, c);
                        g.fill(sw - in - bw, in + bw, sw - in, sh - in - bw, c);
                    }
                }

                if (rgb == 16773856) {
                    g.fill(0, 0, sw, sh, (int)(f * 0.35F * 255.0F) << 24 | rgb);
                }
            }

            if (ClientEffects.slashTicks > 0 && mc.options.getCameraType().isFirstPerson()) {
                float p = 1.0F - ((float)ClientEffects.slashTicks - pt) / 6.0F;
                p = Mth.clamp(p, 0.0F, 1.0F);
                boolean big = ClientEffects.slashBig;
                float len = (big ? 1.5F : 0.9F) * (float)Math.hypot((double)sw, (double)sh) * Math.min(1.0F, p * 3.5F);
                float fade = 1.0F - p;
                g.pose().pushPose();
                g.pose().translate((float)sw / 2.0F, (float)sh / 2.0F, 0.0F);
                g.pose().mulPose(Axis.ZP.rotationDegrees(ClientEffects.slashAngle));
                g.pose().translate(-len / 2.0F, (p - 0.5F) * (float)(big ? 30 : 16), 0.0F);
                int thick = Math.max(1, Math.round((float)(big ? 5 : 3) * fade));
                g.fill(0, -thick * 3, (int)len, thick * 3, (int)(fade * 120.0F) << 24 | 11540504);
                g.fill(0, -thick, (int)len, thick, (int)(fade * 255.0F) << 24 | 16777215);
                g.pose().popPose();
            }
        }
    }

    private static void renderOverdrive(GuiGraphics g, int sw, int sh, float pt) {
        int left = ClientOdm.INSTANCE.overdrive;
        if (left > 0) {
            float t = (float)hudTicks + pt;
            float beat = 0.5F + 0.5F * Mth.sin(t * 0.55F);
            float end = left < 40 ? (left % 8 < 4 ? 0.4F : 1.0F) : 1.0F;
            float strength = (0.28F + 0.2F * beat) * end;
            int bands = 10;
            int bw = Math.max(2, (int)((float)Math.min(sw, sh) * 0.022F));

            for (int i = 0; i < bands; i++) {
                float k = 1.0F - (float)i / (float)bands;
                int a = (int)(strength * k * k * 255.0F);
                if (a > 2) {
                    int c = a << 24 | 14684190;
                    int in = i * bw;
                    g.fill(in, in, sw - in, in + bw, c);
                    g.fill(in, sh - in - bw, sw - in, sh - in, c);
                    g.fill(in, in + bw, in + bw, sh - in - bw, c);
                    g.fill(sw - in - bw, in + bw, sw - in, sh - in - bw, c);
                }
            }

            float frac = Mth.clamp(((float)left - pt) / 240.0F, 0.0F, 1.0F);
            int w = 60;
            int x0 = sw / 2 - w / 2;
            int y0 = sh / 2 + 34;
            g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + 3, -1728053248);
            g.fill(x0, y0, x0 + Math.round((float)w * frac), y0 + 2, -56776);
            Font font = Minecraft.getInstance().font;
            Component label = Component.translatable("hud.takemtitans.overdrive_short");
            g.pose().pushPose();
            g.pose().translate((float)sw / 2.0F, (float)(y0 + 5), 0.0F);
            g.pose().scale(0.7F, 0.7F, 1.0F);
            g.drawString(font, label, -font.width(label) / 2, 0, -40848, true);
            g.pose().popPose();
        }
    }

    private static void renderPopups(GuiGraphics g, Font font, int sw, int sh, float pt) {
        int hits = 0;
        int banners = 0;

        for (int i = POPUPS.size() - 1; i >= 0; i--) {
            OdmHud.Popup p = POPUPS.get(i);
            float age = (float)p.age + pt;
            float alpha = Mth.clamp(((float)p.lifetime - age) / 10.0F, 0.0F, 1.0F);
            float pop = age < 4.0F ? 1.0F + (4.0F - age) * 0.12F : 1.0F;
            int a = Math.max(4, (int)(alpha * 255.0F));
            int color = a << 24 | p.color & 16777215;
            float x = (float)sw / 2.0F;
            float y;
            if (p.banner) {
                y = (float)sh / 2.0F - 52.0F + (float)(banners * 22);
                banners++;
            } else {
                y = (float)sh / 2.0F + 20.0F + (float)(hits * 12) - age * 0.5F;
                hits++;
            }

            g.pose().pushPose();
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(p.scale * pop, p.scale * pop, 1.0F);
            g.drawString(font, p.text, -font.width(p.text) / 2, 0, color, true);
            g.pose().popPose();
        }
    }

    public static void renderSights(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!hidden(mc) && mc.options.getCameraType().isFirstPerson()) {
            LocalPlayer p = mc.player;
            if (!OdmGear.getGear(p).isEmpty()) {
                ClientOdm odm = ClientOdm.INSTANCE;
                ClientOdm.Aim aim = odm.aim;
                int cx = g.guiWidth() / 2;
                int cy = g.guiHeight() / 2;
                int base = aim == null ? 1442840575 : (aim.entity() ? -38053 : (aim.assisted() ? -575082241 : -285212673));
                drawBracket(g, cx, cy, -1, colorFor(odm.left, base), odm.left.isAttached());
                drawBracket(g, cx, cy, 1, colorFor(odm.right, base), odm.right.isAttached());
                float charge = odm.rushChargeFraction();
                if (charge > 0.0F) {
                    boolean ready = charge >= 1.0F;
                    boolean red = odm.red(p);
                    int ticks = 32;
                    int lit = Math.round((float)ticks * charge);
                    float pulse = ready ? 0.6F + 0.4F * Mth.sin(((float)hudTicks + delta.getGameTimeDeltaPartialTick(false)) * 1.2F) : 1.0F;
                    int rgb = ready ? (red ? 16720440 : 16777215) : (red ? 16740480 : 10148095);

                    for (int i = 0; i < ticks; i++) {
                        double ang = -1.5707963267948966 + (double)(6.2831855F * (float)i / (float)ticks);
                        int x = cx + (int)Math.round(Math.cos(ang) * 16.0);
                        int y = cy + (int)Math.round(Math.sin(ang) * 16.0);
                        int a = i < lit ? (int)(255.0F * pulse) : 64;
                        int sz = ready ? 2 : 1;
                        g.fill(x - sz / 2, y - sz / 2, x - sz / 2 + sz, y - sz / 2 + sz, a << 24 | (i < lit ? rgb : 16777215));
                    }
                }

                if (aimingAtNape(mc, p)) {
                    // the only cut that kills: a gold diamond closes in on the crosshair
                    float t = ((float)hudTicks + delta.getGameTimeDeltaPartialTick(false)) * 0.35F;
                    int r = 13 + Math.round(Mth.sin(t) * 1.5F);
                    int gold = 0xFFFFC23A;
                    for (int i = 0; i <= 4; i++) {
                        g.fill(cx - r + i, cy - i, cx - r + i + 2, cy - i + 1, gold);
                        g.fill(cx - r + i, cy + i, cx - r + i + 2, cy + i + 1, gold);
                        g.fill(cx + r - i - 1, cy - i, cx + r - i + 1, cy - i + 1, gold);
                        g.fill(cx + r - i - 1, cy + i, cx + r - i + 1, cy + i + 1, gold);
                    }
                    String n = Component.translatable("hud.takemtitans.nape").getString();
                    g.pose().pushPose();
                    g.pose().translate((float)cx, (float)(cy - 22), 0.0F);
                    g.pose().scale(0.75F, 0.75F, 1.0F);
                    g.drawString(mc.font, n, -mc.font.width(n) / 2, 0, gold, true);
                    g.pose().popPose();
                }

                if (aim != null) {
                    String d = (int)Math.round(aim.distance()) + "m";
                    g.pose().pushPose();
                    g.pose().translate((float)cx, (float)(cy + 10), 0.0F);
                    g.pose().scale(0.7F, 0.7F, 1.0F);
                    g.drawString(mc.font, d, -mc.font.width(d) / 2, 0, base, true);
                    g.pose().popPose();
                }
            }
        }
    }

    private static boolean aimingAtNape(Minecraft mc, LocalPlayer p) {
        if (!(p.getMainHandItem().getItem() instanceof BladeItem)) {
            return false;
        }

        for (AbstractTitan t : mc.level.getEntitiesOfClass(AbstractTitan.class, p.getBoundingBox().inflate(48.0), e -> e.isAlive())) {
            TitanPart part = t.aimedPart(p);
            if (part != null && part.kind == 1) {
                return true;
            }
        }

        return false;
    }

    private static int colorFor(HookState h, int base) {
        return switch (h.phase) {
            case ATTACHED -> h.reeling ? -11541505 : -13653816;
            case FLYING -> -10929;
            case RETRACTING -> -1996488705;
            default -> base;
        };
    }

    private static void drawBracket(GuiGraphics g, int cx, int cy, int side, int color, boolean thick) {
        int r = 9;

        for (int i = -5; i <= 5; i++) {
            double ang = (double)i * 0.17;
            int x = cx + side * (int)Math.round((double)r * Math.cos(ang));
            int y = cy + (int)Math.round((double)r * Math.sin(ang) * 1.15);
            int w = thick ? 2 : 1;
            g.fill(side > 0 ? x : x - w + 1, y, side > 0 ? x + w : x + 1, y + 1, color);
        }
    }

    public static void renderSpeedLines(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && TakemConfig.SPEED_LINES.get() && mc.options.getCameraType().isFirstPerson()) {
            ClientOdm odm = ClientOdm.INSTANCE;
            if (odm.inFlight() && !(odm.speed < 1.15)) {
                float intensity = (float)Mth.clamp((odm.speed - 1.15) / 1.6, 0.0, 1.0);
                int count = 10 + (int)(intensity * 34.0F);
                int sw = g.guiWidth();
                int sh = g.guiHeight();
                float radius = (float)Math.sqrt((double)(sw * sw + sh * sh)) * 0.5F;
                RandomSource rnd = RandomSource.create((long)mc.player.tickCount * 7919L);
                g.pose().pushPose();
                g.pose().translate((float)sw / 2.0F, (float)sh / 2.0F, 0.0F);

                for (int i = 0; i < count; i++) {
                    float angle = rnd.nextFloat() * 6.2831855F;
                    float inner = radius * (0.55F + rnd.nextFloat() * 0.25F);
                    float len = radius * (0.15F + rnd.nextFloat() * 0.35F) * (0.6F + intensity);
                    int a = (int)((0.12F + rnd.nextFloat() * 0.28F) * intensity * 255.0F);
                    g.pose().pushPose();
                    g.pose().mulPose(Axis.ZP.rotation(angle));
                    g.fill((int)inner, 0, (int)(inner + len), 1, a << 24 | 16777215);
                    g.pose().popPose();
                }

                g.pose().popPose();
            }
        }
    }

    private static final class Popup {
        final Component text;
        final int color;
        final float scale;
        final int lifetime;
        final boolean banner;
        int age;

        Popup(Component text, int color, float scale, int lifetime, boolean banner) {
            this.text = text;
            this.color = color;
            this.scale = scale;
            this.lifetime = lifetime;
            this.banner = banner;
        }
    }
}
