package com.takem.titans.entity.titan;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A titan whose life sits in heart cores outside its nape (the Wall Titan's, the War Hammer's crystal cocoon). */
public interface CoreHost {
    Vec3 corePos(int slot);

    void onCoreDestroyed(ServerLevel level, int slot, @Nullable Player by);

    int getId();

    boolean isAlive();
}
