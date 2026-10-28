package com.takem.titans.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Iris (the shader loader in the modpack) ships with R to reload shaders and K to switch them off, the keys this mod
 * uses for blade reload and skills. Once per start-up, if they still sit on those keys, they are moved aside: reload is
 * left unbound and the shader toggle goes to F6.
 */
@EventBusSubscriber(modid = "takemtitans", value = Dist.CLIENT)
public final class IrisKeys {
    private static boolean done;

    private IrisKeys() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (done) {
            return;
        }

        done = true;
        Minecraft mc = Minecraft.getInstance();
        boolean changed = false;
        for (KeyMapping k : mc.options.keyMappings) {
            String name = k.getName();
            if (name.equals("iris.keybind.reload") && k.getKey().getValue() == GLFW.GLFW_KEY_R) {
                k.setKey(InputConstants.UNKNOWN);
                changed = true;
            } else if (name.equals("iris.keybind.toggleShaders") && k.getKey().getValue() == GLFW.GLFW_KEY_K) {
                k.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F6));
                changed = true;
            }
        }

        if (changed) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
    }
}
