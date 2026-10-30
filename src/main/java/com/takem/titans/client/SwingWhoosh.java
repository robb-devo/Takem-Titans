package com.takem.titans.client;

import com.takem.titans.odm.HookState;
import com.takem.titans.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

/**
 * The rush of air at the bottom of a swing: when you pass the lowest point of an arc on a hooked cable at speed, a
 * whoosh, louder and higher the faster you go. Nothing while falling free or hanging still.
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class SwingWhoosh {
    private static double lastVy;
    private static int cooldown;

    private SwingWhoosh() {
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused()) {
            lastVy = 0.0;
            return;
        }

        if (cooldown > 0) {
            cooldown--;
        }

        boolean hooked = false;
        HookState[] hooks = ClientOdm.INSTANCE.allHooks();
        if (hooks != null) {
            for (HookState h : hooks) {
                hooked |= h != null && h.isAttached();
            }
        }

        Vec3 v = p.getDeltaMovement();
        double across = Math.sqrt(v.x * v.x + v.z * v.z);
        // the bottom of the arc: coming down, now level or rising again, still moving fast along the ground
        if (hooked && cooldown == 0 && lastVy < -0.12 && v.y >= -0.02 && across > 0.8) {
            float k = (float)Mth.clamp((across - 0.8) / 2.0, 0.0, 1.0);
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), ModSounds.ODM_SWING.get(), SoundSource.PLAYERS, 0.35F + 0.5F * k, 0.85F + 0.35F * k, false);
            cooldown = 14;
        }

        lastVy = v.y;
    }
}
