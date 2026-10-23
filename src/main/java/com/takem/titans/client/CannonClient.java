package com.takem.titans.client;

import com.takem.titans.TakemTitans;
import com.takem.titans.entity.CannonEntity;
import com.takem.titans.network.OdmActionPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Pre;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class CannonClient {
    public static final ResourceLocation HUD = TakemTitans.id("cannon_hud");

    private CannonClient() {
    }

    @SubscribeEvent
    public static void onTick(Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof CannonEntity c && mc.screen == null) {
            boolean fired = false;

            while (mc.options.keyAttack.consumeClick()) {
                fired = true;
            }

            if (fired && c.readiness() >= 1.0F) {
                PacketDistributor.sendToServer(new OdmActionPayload(20, 0));
                ClientEffects.kick(0.6F + (float)c.tier() * 0.3F);
            }
        }
    }

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.hideGui && mc.player.getVehicle() instanceof CannonEntity c) {
            int w = 120;
            int x = (g.guiWidth() - w) / 2;
            int y = g.guiHeight() / 2 + 22;
            float r = c.readiness();
            g.fill(x - 1, y - 1, x + w + 1, y + 6, -1072689128);
            int col = r >= 1.0F ? -16320 : -7710166;
            g.fill(x, y, x + (int)((float)w * r), y + 5, col);

            Component name = Component.translatable("item.takemtitans." + switch (c.tier()) {
                case 2 -> "siege_cannon";
                case 3 -> "thunder_battery";
                default -> "field_cannon";
            });
            g.drawCenteredString(mc.font, name, g.guiWidth() / 2, y + 9, -1517392);
            Component hint = Component.translatable(r >= 1.0F ? "hud.takemtitans.cannon_ready" : "hud.takemtitans.cannon_reload");
            g.drawCenteredString(mc.font, hint, g.guiWidth() / 2, y + 20, r >= 1.0F ? -12192 : -6649232);
        }
    }
}
