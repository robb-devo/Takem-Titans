package com.takem.titans.entity.titan;

import com.takem.titans.item.BladeItem;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class TitanGrabs {
    public static final int CLICKS_ARMED = 14;
    public static final int CLICKS_UNARMED = 30;

    private TitanGrabs() {
    }

    public static void struggle(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        if (d.grabbedBy >= 0) {
            if (player.level().getEntity(d.grabbedBy) instanceof AbstractTitan titan && titan.getHeld() == player) {
                d.struggle++;
                ItemStack blades = player.getMainHandItem();
                boolean armed = blades.getItem() instanceof BladeItem && !BladeItem.isBroken(blades);
                if (d.struggle >= (armed ? 14 : 30)) {
                    d.struggle = 0;
                    titan.cutFree(player, armed);
                }

                return;
            }

            d.grabbedBy = -1;
        }
    }
}
