package com.takem.titans.client;

import com.takem.titans.network.HookSyncPayload;
import com.takem.titans.odm.HookSide;
import com.takem.titans.odm.HookState;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.registry.ModParticles;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class RemoteHooks {
    private static final Int2ObjectMap<HookState[]> HOOKS = new Int2ObjectOpenHashMap();
    private static final IntSet BOOSTING = new IntOpenHashSet();

    private RemoteHooks() {
    }

    @Nullable
    public static HookState[] get(int playerId) {
        return (HookState[])HOOKS.get(playerId);
    }

    public static boolean isBoosting(int playerId) {
        return BOOSTING.contains(playerId);
    }

    public static void setBoosting(int playerId, boolean on) {
        if (on) {
            BOOSTING.add(playerId);
            OdmSounds.setRemoteGas(playerId, true);
        } else {
            BOOSTING.remove(playerId);
        }
    }

    public static void handle(HookSyncPayload msg, IPayloadContext ctx) {
        HookState[] arr = (HookState[])HOOKS.computeIfAbsent(msg.playerId(), k -> new HookState[]{new HookState(HookSide.LEFT), new HookState(HookSide.RIGHT)});
        HookState incoming = msg.state();
        HookState h = arr[incoming.side.ordinal()];
        Vec3 from = h.isActive() ? h.pos : incoming.pos;
        if (incoming.phase == HookState.Phase.RETRACTING || incoming.phase == HookState.Phase.IDLE && h.isActive()) {
            ClientLevel level = Minecraft.getInstance().level;
            Vec3 at = h.isAttached() && level != null ? h.anchor(level, 1.0F) : from;
            h.copyFrom(incoming);
            h.phase = HookState.Phase.RETRACTING;
            h.pos = at;
            h.prevPos = at;
            h.entityId = -1;
        } else {
            h.copyFrom(incoming);
            h.prevPos = h.pos;
            h.traveled = 0.0;
        }
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            HOOKS.clear();
            BOOSTING.clear();
        } else if (!mc.isPaused()) {
            ObjectIterator<Entry<HookState[]>> it = HOOKS.int2ObjectEntrySet().iterator();

            while (it.hasNext()) {
                Entry<HookState[]> entry = (Entry<HookState[]>)it.next();
                Entity e = level.getEntity(entry.getIntKey());
                if (e instanceof Player player && !e.isRemoved()) {
                    boolean any = false;

                    for (HookState h : (HookState[])entry.getValue()) {
                        tick(player, h);
                        any |= h.isActive();
                    }

                    if (!any && !BOOSTING.contains(entry.getIntKey())) {
                        it.remove();
                    }
                    continue;
                }

                it.remove();
            }

            for (int id : BOOSTING.toIntArray()) {
                Entity e = level.getEntity(id);
                if (e instanceof Player player && !e.isRemoved()) {
                    Vec3 look = player.getViewVector(1.0F);
                    Vec3 base = player.position().add(0.0, 0.95, 0.0).subtract(look.scale(0.45));
                    Vec3 v = player.position().subtract(player.xo, player.yo, player.zo).scale(0.6);

                    for (int k = 0; k < 2; k++) {
                        level.addParticle(
                            ModParticles.GAS.get(),
                            base.x + (level.random.nextDouble() - 0.5) * 0.3,
                            base.y + (level.random.nextDouble() - 0.5) * 0.3,
                            base.z + (level.random.nextDouble() - 0.5) * 0.3,
                            v.x - look.x * 0.2,
                            v.y - look.y * 0.2,
                            v.z - look.z * 0.2
                        );
                    }
                    continue;
                }

                BOOSTING.remove(id);
            }
        }
    }

    private static void tick(Player player, HookState h) {
        h.prevPos = h.pos;
        switch (h.phase) {
            case FLYING:
                h.pos = h.pos.add(h.vel);
                h.traveled = h.traveled + h.vel.length();
                if (h.traveled > OdmGear.hookRange() + 4.0) {
                    h.phase = HookState.Phase.RETRACTING;
                }
                break;
            case RETRACTING:
                Vec3 target = HookState.launcherPos(player, h.side, 1.0F);
                Vec3 d = target.subtract(h.pos);
                double len = d.length();
                double step = 8.0 + player.position().subtract(player.xo, player.yo, player.zo).length();
                if (len <= step) {
                    h.reset();
                    h.pos = target;
                    h.prevPos = target;
                } else {
                    h.pos = h.pos.add(d.scale(step / len));
                }
        }
    }
}
