package com.takem.titans.client.gui;

import com.takem.titans.TakemTitans;
import com.takem.titans.client.ClientOdm;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.registry.ModItems;
import com.takem.titans.skill.ClientSkills;
import com.takem.titans.skill.Skill;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AbilityHud {
    public static final ResourceLocation LAYER = TakemTitans.id("abilities");
    private static ItemStack meteor;
    private static ItemStack nova;

    private AbilityHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.hideGui && OdmGear.hasGear(mc.player)) {
            if (meteor == null) {
                meteor = new ItemStack(Items.FIRE_CHARGE);
                nova = new ItemStack(ModItems.THERMOBARIC_CANISTER.get());
            }

            ClientOdm o = ClientOdm.INSTANCE;
            int x = g.guiWidth() / 2 + 98;
            int y = g.guiHeight() - 22;
            slot(g, mc, x, y, meteor, o.cd[0], o.cdMax[0]);
            if (ClientSkills.rank(Skill.GAS_NOVA) > 0) {
                slot(g, mc, x + 23, y, nova, o.cd[1], o.cdMax[1]);
            }
        }
    }

    private static void slot(GuiGraphics g, Minecraft mc, int x, int y, ItemStack icon, int cd, int max) {
        TTGui.hudPanel(g, x, y, 21, 21);
        g.renderItem(icon, x + 2, y + 2);
        if (cd > 0) {
            int h = (int)(17.0F * (float)cd / (float)Math.max(1, max));
            g.fill(x + 2, y + 19 - h, x + 19, y + 19, -1341124588);
            String s = String.valueOf((cd + 19) / 20);
            g.drawString(mc.font, s, x + 11 - mc.font.width(s) / 2, y + 7, -8080, true);
        }
    }
}
