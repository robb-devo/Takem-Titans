package com.takem.titans.client;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.TitanAction;
import com.takem.titans.network.OdmActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ClientTickEvent.Pre;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class TitanGrabClient {
    private static boolean wasGrabbed;

    private TitanGrabClient() {
    }

    @SubscribeEvent
    public static void onTickPre(Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && mc.level != null) {
            boolean grabbed = ClientTitanState.isGrabbed();
            if (grabbed && !wasGrabbed) {
                ClientOdm.INSTANCE.releaseAll(p);
                ClientEffects.shake(0.6F, 10);
            }

            wasGrabbed = grabbed;
            if (grabbed) {
                if (!p.isAlive()) {
                    ClientTitanState.reset();
                } else {
                    while (mc.options.keyAttack.consumeClick()) {
                        ClientTitanState.struggleClicks++;
                        p.swing(InteractionHand.MAIN_HAND);
                        PacketDistributor.sendToServer(new OdmActionPayload(3, 0));
                    }

                    while (mc.options.keyUse.consumeClick()) {
                    }
                }
            }
        } else {
            ClientTitanState.reset();
            wasGrabbed = false;
        }
    }

    @SubscribeEvent
    public static void onTickPost(Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && mc.level != null && ClientTitanState.isGrabbed()) {
            if (mc.level.getEntity(ClientTitanState.grabbedBy()) instanceof AbstractTitan titan && titan.isAlive()) {
                TitanAction a = titan.getAction();
                float side = a.side() == 0.0F ? -1.0F : a.side();
                Vec3 hold = titan.holdPoint(side).subtract(0.0, (double)p.getBbHeight() * 0.5, 0.0);
                p.setPos(hold.x, hold.y, hold.z);
                p.setDeltaMovement(Vec3.ZERO);
                p.fallDistance = 0.0F;
                return;
            }

            ClientTitanState.reset();
        }
    }

    public static void footstep(AbstractTitan titan) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null) {
            double d = p.position().distanceTo(titan.position());
            double range = 10.0 + (double)titan.getTitanHeight() * 1.6;
            if (d < range) {
                float s = (float)(1.0 - d / range) * Math.min(1.0F, titan.getTitanScale() / 4.0F) * 0.35F;
                if (p.onGround() || d < range * 0.4) {
                    ClientEffects.shake(s, 6);
                }
            }
        }
    }
}
