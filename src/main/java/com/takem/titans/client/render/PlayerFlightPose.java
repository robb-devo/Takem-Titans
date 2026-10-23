package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takem.titans.client.ClientEffects;
import com.takem.titans.client.ClientOdm;
import com.takem.titans.client.RemoteHooks;
import com.takem.titans.odm.HookState;
import com.takem.titans.odm.OdmGear;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent.Post;
import net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre;
import org.joml.Quaternionf;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class PlayerFlightPose {
    private static final Int2FloatMap LEAN = new Int2FloatOpenHashMap();
    private static final Int2FloatMap PUSHED = new Int2FloatOpenHashMap();

    private PlayerFlightPose() {
    }

    public static boolean isFlying(Player p) {
        if (!OdmGear.hasGear(p) || p.onGround() || p.isInWater() || p.isPassenger() || p.getAbilities().flying) {
            return false;
        } else if (p == Minecraft.getInstance().player) {
            return ClientOdm.INSTANCE.inFlight();
        } else if (RemoteHooks.isBoosting(p.getId())) {
            return true;
        } else {
            HookState[] hooks = RemoteHooks.get(p.getId());
            if (hooks != null) {
                for (HookState h : hooks) {
                    if (h.isAttached()) {
                        return true;
                    }
                }
            }

            return p.position().subtract(p.xo, p.yo, p.zo).lengthSqr() > 0.36;
        }
    }

    @SubscribeEvent
    public static void onPre(Pre event) {
        Player p = event.getEntity();
        float pt = event.getPartialTick();
        Vec3 vel = p == Minecraft.getInstance().player ? ClientOdm.INSTANCE.velocity : p.position().subtract(p.xo, p.yo, p.zo);
        double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        float target = isFlying(p) ? (float)Mth.clamp(h * 32.0, 0.0, 62.0) : 0.0F;
        float lean = LEAN.getOrDefault(p.getId(), 0.0F);
        lean += (target - lean) * 0.08F;
        LEAN.put(p.getId(), lean);
        float spin = p == Minecraft.getInstance().player ? ClientEffects.spinProgress(pt) : 0.0F;
        boolean leaning = lean >= 0.5F && h >= 1.0E-4;
        if (leaning || !(spin <= 0.0F)) {
            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            PUSHED.put(p.getId(), 1.0F);
            ps.translate(0.0F, 0.9F, 0.0F);
            if (leaning) {
                float axisX = (float)(vel.z / h);
                float axisZ = (float)(-vel.x / h);
                ps.mulPose(new Quaternionf().rotationAxis(lean * 0.017453292F, axisX, 0.0F, axisZ));
            }

            if (spin > 0.0F) {
                float eased = spin < 0.5F ? 2.0F * spin * spin : 1.0F - (float)Math.pow((double)(-2.0F * spin + 2.0F), 2.0) / 2.0F;
                ps.mulPose(new Quaternionf().rotationY(-eased * 6.2831855F));
            }

            ps.translate(0.0F, -0.9F, 0.0F);
        }
    }

    @SubscribeEvent
    public static void onPost(Post event) {
        if (PUSHED.remove(event.getEntity().getId()) != PUSHED.defaultReturnValue()) {
            event.getPoseStack().popPose();
        }
    }
}
