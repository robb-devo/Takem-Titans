package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.takem.titans.client.ClientOdm;
import com.takem.titans.client.DevTune;
import com.takem.titans.item.BladeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class BladeHandRenderer {
    private static float flyBlend;
    private static float prevFlyBlend;
    private static float speedBlend;
    private static float prevSpeedBlend;

    private BladeHandRenderer() {
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.isPaused()) {
            prevFlyBlend = flyBlend;
            prevSpeedBlend = speedBlend;
            ClientOdm odm = ClientOdm.INSTANCE;
            float target = odm.inFlight() ? 1.0F : 0.0F;
            flyBlend = flyBlend + (target - flyBlend) * 0.18F;
            float sp = (float)Mth.clamp((odm.speed - 0.3) / 2.0, 0.0, 1.0);
            speedBlend = speedBlend + (sp - speedBlend) * 0.2F;
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null) {
            ItemStack main = p.getMainHandItem();
            if (main.getItem() instanceof BladeItem && (event.getHand() != InteractionHand.OFF_HAND || p.getOffhandItem().isEmpty())) {
                event.setCanceled(true);
                HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
                float equip = event.getHand() == InteractionHand.MAIN_HAND ? event.getEquipProgress() : 0.0F;
                render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), p, main, arm, event.getPartialTick(), equip);
            }
        }
    }

    private static void render(PoseStack ps, MultiBufferSource buffers, int light, LocalPlayer p, ItemStack stack, HumanoidArm arm, float pt, float equip) {
        float s = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float fly = Mth.lerp(pt, prevFlyBlend, flyBlend);
        float spd = Mth.lerp(pt, prevSpeedBlend, speedBlend);
        float swing = p.getAttackAnim(pt);
        if (arm != p.getMainArm()) {
            swing = swing <= 0.0F ? 0.0F : Mth.clamp((swing - 0.18F) / 0.82F, 0.0F, 1.0F);
        }

        ClientOdm odm = ClientOdm.INSTANCE;
        float reload = 0.0F;
        if (odm.reloadTicks > 0) {
            float r = 1.0F - ((float)odm.reloadTicks - pt) / 14.0F;
            reload = Mth.sin(Mth.clamp(r, 0.0F, 1.0F) * 3.1415927F);
        }

        float time = (float)p.tickCount + pt;
        fly = Math.max(fly, DevTune.get("force_fly", 0.0F));
        float gx = Mth.lerp(fly, DevTune.get("bx", 0.44F), DevTune.get("fx", 0.55F));
        float gy = Mth.lerp(fly, DevTune.get("by", -0.47F), DevTune.get("fy", -0.55F));
        float gz = Mth.lerp(fly, DevTune.get("bz", -0.74F), DevTune.get("fz", -0.7F));
        float elev = Mth.lerp(fly, DevTune.get("be", -3.0F), DevTune.get("fe", -12.0F));
        float inward = Mth.lerp(fly, DevTune.get("bi", 5.0F), DevTune.get("fi", -10.0F));
        float twist = Mth.lerp(fly, DevTune.get("bt", -25.0F), DevTune.get("ft", -35.0F));
        float roll = Mth.lerp(fly, DevTune.get("br", 0.0F), DevTune.get("fr", -20.0F));
        float scale = DevTune.get("bs", 0.5F);
        float shake = spd * 0.01F;
        gy += Mth.sin(time * 0.08F + (s > 0.0F ? 0.0F : 1.3F)) * 0.008F * (1.0F - fly) + Mth.sin(time * 2.3F + 1.0F) * shake;
        gx += Mth.sin(time * 1.9F) * shake;
        gy -= equip * 0.6F + reload * 0.55F;
        if (swing > 0.0F) {
            float arc = Mth.sin(Mth.sqrt(swing) * 3.1415927F);
            float sweep = swing < 0.5F ? 2.0F * swing * swing : 1.0F - (float)Math.pow((double)(-2.0F * swing + 2.0F), 2.0) / 2.0F;
            gx += -0.22F * arc + 0.1F * (1.0F - sweep) * arc;
            gy += 0.1F * arc;
            gz += -0.12F * arc;
            inward += DevTune.get("slash_from", -45.0F) + (DevTune.get("slash_to", 70.0F) - DevTune.get("slash_from", -45.0F)) * sweep;
            elev += 18.0F * arc;
            twist += 25.0F * arc;
            roll += -25.0F * arc;
        }

        elev -= 70.0F * reload;
        ps.pushPose();
        ps.translate(s * gx, gy, gz);
        ps.mulPose(Axis.YP.rotationDegrees(s * inward));
        ps.mulPose(Axis.ZP.rotationDegrees(s * roll));
        ps.mulPose(Axis.XP.rotationDegrees(-(90.0F - elev)));
        ps.mulPose(Axis.YP.rotationDegrees(s * twist));
        ps.translate(0.0F, 0.328125F * scale, 0.0F);
        ps.scale(scale, scale, scale);
        ItemDisplayContext var10003 = s > 0.0F ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        Minecraft.getInstance().gameRenderer.itemInHandRenderer.renderItem(p, stack, var10003, s < 0.0F, ps, buffers, light);
        ps.popPose();
    }
}
