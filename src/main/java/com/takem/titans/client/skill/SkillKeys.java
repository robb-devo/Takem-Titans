package com.takem.titans.client.skill;

import com.takem.titans.client.OdmKeys;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class SkillKeys {
    private SkillKeys() {
    }

    @SubscribeEvent
    public static void onClientTick(Post event) {
        Minecraft mc = Minecraft.getInstance();

        while (OdmKeys.SKILLS.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new SkillScreen());
            }
        }

        while (OdmKeys.MANUAL.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new com.takem.titans.client.gui.FieldManualScreen());
            }
        }
    }
}
