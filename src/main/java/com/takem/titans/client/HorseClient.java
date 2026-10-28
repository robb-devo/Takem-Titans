package com.takem.titans.client;

import com.takem.titans.entity.ScoutHorseEntity;
import com.takem.titans.network.OdmActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

/** In the saddle the dash key throws the horse sideways, to whichever side you are steering. */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class HorseClient {
    private static boolean dashWasDown;

    private HorseClient() {
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean down = p != null && mc.screen == null && OdmKeys.dashDown();
        if (down && !dashWasDown && p.getVehicle() instanceof ScoutHorseEntity) {
            float strafe = p.input.leftImpulse;
            int side = strafe > 0.1F ? 1 : (strafe < -0.1F ? -1 : 0);
            PacketDistributor.sendToServer(new OdmActionPayload(25, side));
            ClientEffects.shake(0.25F, 6);
        }

        dashWasDown = down;
    }
}
