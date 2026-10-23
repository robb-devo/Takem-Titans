package com.takem.titans.client.gui;

import com.takem.titans.TakemTitans;
import com.takem.titans.client.ClientOdm;
import com.takem.titans.client.story.Cutscene;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class ScreenFx {
    public static final ResourceLocation LAYER = TakemTitans.id("screen_fx");
    private static final float[] cur = new float[4];
    private static float blood;
    private static float bloodSeed;

    public static void blood(float amount, int seed) {
        blood = Math.min(1.0F, Math.max(blood, 0.0F) + amount);
        bloodSeed = (float)(seed % 997);
    }

    private ScreenFx() {
    }

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p != null && TTGui.screenFx != null && !Cutscene.active() && !mc.options.hideGui) {
            ClientOdm odm = ClientOdm.INSTANCE;
            float[] want = new float[4];
            want[0] = odm.overdrive > 0 ? Math.min(1.0F, (float)odm.overdrive / 20.0F) : 0.0F;
            if (odm.diveTicks > 0) {
                want[1] = (float)Mth.clamp((odm.diveStartY - p.getY() - 20.0) / 90.0, 0.0, 1.0);
            }

            double dx = p.getX() - 304.0;
            double dz = p.getZ() + 1103.0;
            if (p.getY() < 60.0 && dx * dx + dz * dz < 72900.0 && !p.level().canSeeSky(BlockPos.containing(p.getEyePosition()))) {
                want[2] = 1.0F;
            }

            float hp = p.getHealth() / Math.max(1.0F, p.getMaxHealth());
            want[3] = p.isAlive() && hp < 0.3F ? (0.3F - hp) / 0.3F * 0.8F + 0.2F : 0.0F;
            boolean any = blood > 0.01F;
            blood *= 0.992F;

            for (int i = 0; i < 4; i++) {
                cur[i] = cur[i] + (want[i] - cur[i]) * 0.08F;
                if (cur[i] > 0.005F) {
                    any = true;
                }
            }

            if (any) {
                TTGui.uniform(TTGui.screenFx, "Time", (float)(Util.getMillis() % 1000000L) / 1000.0F);
                TTGui.uniform(TTGui.screenFx, "Aspect", (float)g.guiWidth() / (float)Math.max(1, g.guiHeight()));
                TTGui.uniform(TTGui.screenFx, "Fx", cur[0], cur[1], cur[2], cur[3]);
                TTGui.uniform(TTGui.screenFx, "Blood", blood, bloodSeed);
                TTGui.shaderQuad(g, TTGui.screenFx, 0.0F, 0.0F, (float)g.guiWidth(), (float)g.guiHeight());
            }
        }
    }
}
