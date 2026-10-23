package com.takem.titans.client.gui;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.takem.titans.TakemTitans;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class TTGui {
    public static final ResourceLocation PANEL = TakemTitans.id("textures/gui/panel.png");
    public static final ResourceLocation HUD_PANEL = TakemTitans.id("textures/gui/hud_panel.png");
    public static final ResourceLocation ATLAS = TakemTitans.id("textures/gui/skill_ui.png");
    @Nullable
    public static ShaderInstance skillAura;
    @Nullable
    public static ShaderInstance screenFx;

    private TTGui() {
    }

    public static void nine(GuiGraphics g, ResourceLocation t, int x, int y, int w, int h, int c, int tex) {
        RenderSystem.enableBlend();
        int m = tex - 2 * c;
        int iw = Math.max(0, w - 2 * c);
        int ih = Math.max(0, h - 2 * c);
        g.blit(t, x, y, 0.0F, 0.0F, c, c, tex, tex);
        g.blit(t, x + w - c, y, (float)(tex - c), 0.0F, c, c, tex, tex);
        g.blit(t, x, y + h - c, 0.0F, (float)(tex - c), c, c, tex, tex);
        g.blit(t, x + w - c, y + h - c, (float)(tex - c), (float)(tex - c), c, c, tex, tex);
        if (iw > 0) {
            g.blit(t, x + c, y, iw, c, (float)c, 0.0F, m, c, tex, tex);
            g.blit(t, x + c, y + h - c, iw, c, (float)c, (float)(tex - c), m, c, tex, tex);
        }

        if (ih > 0) {
            g.blit(t, x, y + c, c, ih, 0.0F, (float)c, c, m, tex, tex);
            g.blit(t, x + w - c, y + c, c, ih, (float)(tex - c), (float)c, c, m, tex, tex);
        }

        if (iw > 0 && ih > 0) {
            g.blit(t, x + c, y + c, iw, ih, (float)c, (float)c, m, m, tex, tex);
        }

        RenderSystem.disableBlend();
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        nine(g, PANEL, x, y, w, h, 16, 64);
    }

    public static void hudPanel(GuiGraphics g, int x, int y, int w, int h) {
        nine(g, HUD_PANEL, x, y, w, h, 8, 32);
    }

    public static void sprite(GuiGraphics g, int x, int y, int w, int h, int u, int v, int uw, int vh, int argb) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(
            (float)(argb >> 16 & 0xFF) / 255.0F, (float)(argb >> 8 & 0xFF) / 255.0F, (float)(argb & 0xFF) / 255.0F, (float)(argb >>> 24) / 255.0F
        );
        g.blit(ATLAS, x, y, w, h, (float)u, (float)v, uw, vh, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    public static void button(GuiGraphics g, int x, int y, int w, int h, int state) {
        RenderSystem.enableBlend();
        int u = state * 64;
        int v = 216;
        g.blit(ATLAS, x, y, 4, h, (float)u, (float)v, 4, 18, 256, 256);
        g.blit(ATLAS, x + 4, y, w - 8, h, (float)(u + 4), (float)v, 56, 18, 256, 256);
        g.blit(ATLAS, x + w - 4, y, 4, h, (float)(u + 60), (float)v, 4, 18, 256, 256);
        RenderSystem.disableBlend();
    }

    public static boolean shaderQuad(GuiGraphics g, @Nullable ShaderInstance sh, float x, float y, float w, float h) {
        if (sh == null) {
            return false;
        } else {
            g.flush();
            Matrix4f m = g.pose().last().pose();
            RenderSystem.setShader(() -> sh);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            BufferBuilder b = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            b.addVertex(m, x, y + h, 0.0F).setUv(0.0F, 1.0F);
            b.addVertex(m, x + w, y + h, 0.0F).setUv(1.0F, 1.0F);
            b.addVertex(m, x + w, y, 0.0F).setUv(1.0F, 0.0F);
            b.addVertex(m, x, y, 0.0F).setUv(0.0F, 0.0F);
            BufferUploader.drawWithShader(b.buildOrThrow());
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            return true;
        }
    }

    public static void uniform(@Nullable ShaderInstance sh, String name, float... v) {
        if (sh != null) {
            AbstractUniform u = sh.safeGetUniform(name);
            switch (v.length) {
                case 1:
                    u.set(v[0]);
                    break;
                case 2:
                    u.set(v[0], v[1]);
                    break;
                case 3:
                    u.set(v[0], v[1], v[2]);
                    break;
                default:
                    u.set(v[0], v[1], v[2], v[3]);
            }
        }
    }
}
