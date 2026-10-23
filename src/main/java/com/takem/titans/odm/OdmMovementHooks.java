package com.takem.titans.odm;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class OdmMovementHooks {
    private static OdmMovementHooks.Controller controller;

    private OdmMovementHooks() {
    }

    public static void setController(OdmMovementHooks.Controller c) {
        controller = c;
    }

    public static boolean tryTravel(LivingEntity entity, Vec3 input) {
        OdmMovementHooks.Controller c = controller;
        if (c == null || !(entity instanceof Player player) || !player.isLocalPlayer() || !player.isControlledByLocalInstance()) {
            return false;
        }

        if (!c.shouldControl(player)) {
            return false;
        } else {
            c.travel(player, input);
            return true;
        }
    }

    public interface Controller {
        boolean shouldControl(Player var1);

        void travel(Player var1, Vec3 var2);
    }
}
