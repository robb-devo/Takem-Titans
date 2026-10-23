package com.takem.titans.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class Waypoints {
    public static final int LIFETIME = 1200;
    public static final List<Waypoints.Point> POINTS = new ArrayList<>();

    private Waypoints() {
    }

    public static void add(BlockPos pos) {
        POINTS.removeIf(p -> p.pos.distSqr(pos) < 256.0);
        POINTS.add(new Waypoints.Point(pos));
        if (POINTS.size() > 6) {
            POINTS.remove(0);
        }
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        if (Minecraft.getInstance().level == null) {
            POINTS.clear();
        } else if (!Minecraft.getInstance().isPaused()) {
            POINTS.removeIf(p -> ++p.age > 1200);
        }
    }

    public static final class Point {
        public final BlockPos pos;
        public int age;

        Point(BlockPos pos) {
            this.pos = pos;
        }
    }
}
