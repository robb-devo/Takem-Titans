package com.takem.titans.client;

import com.takem.titans.TakemConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class ClientEffects {
    private static float shakeStrength;
    private static int shakeTicks;
    private static int shakeDuration = 1;
    private static float roll;
    private static float prevRoll;
    private static float kick;
    private static float prevKick;
    private static float fovBoost;
    private static float fovPunch;
    private static int spinTicks;
    private static int spinDuration = 1;
    private static int spinTurns = 1;
    private static int rushTicks;
    public static float flash;
    public static float flashO;
    public static int flashColor = 16722458;
    public static int slashTicks;
    public static float slashAngle;
    public static boolean slashBig;
    public static final int SLASH_TICKS = 6;

    private ClientEffects() {
    }

    public static void shake(float strength, int ticks) {
        if (TakemConfig.SCREEN_SHAKE.get()) {
            float current = shakeTicks > 0 ? shakeStrength * (float)shakeTicks / (float)shakeDuration : 0.0F;
            if (strength >= current) {
                shakeStrength = strength;
                shakeTicks = ticks;
                shakeDuration = Math.max(1, ticks);
            }
        }
    }

    public static void kick(float degrees) {
        kick = Math.min(4.0F, kick + degrees);
    }

    public static void onHit(int power, boolean nape) {
        shake(Math.min(0.75F, 0.14F + (float)power / 170.0F), nape ? 9 : 6);
        fovPunch = Math.min(fovPunch, -(0.025F + Math.min(0.09F, (float)power / 1100.0F)));
        flash(16722458, 0.4F + Math.min(0.4F, (float)power / 250.0F));
        slash(false);
        ClientOdm.INSTANCE.hitStop(Mth.clamp(1 + power / 30, 2, 5) + (nape ? 1 : 0));
    }

    public static void onKill() {
        shake(0.8F, 16);
        fovPunch = -0.16F;
        flash(16773856, 0.6F);
        slash(true);
        ClientOdm.INSTANCE.hitStop(6);
    }

    public static void flash(int color, float strength) {
        if (TakemConfig.HIT_FLASH.get() && strength >= flash) {
            flash = strength;
            flashO = strength;
            flashColor = color;
        }
    }

    private static void slash(boolean big) {
        Minecraft mc = Minecraft.getInstance();
        slashTicks = 6;
        slashBig = big;
        slashAngle = (mc.player != null ? mc.player.getRandom().nextFloat() : 0.5F) * 50.0F
            - 25.0F
            + (big ? 0.0F : 35.0F) * (float)(mc.player != null && mc.player.getRandom().nextBoolean() ? 1 : -1);
    }

    public static void spin(int ticks, int turns) {
        spinTicks = ticks;
        spinDuration = Math.max(1, ticks);
        spinTurns = turns;
    }

    public static void rush() {
        shake(0.45F, 8);
        fovPunch = 0.32F;
        rushTicks = 8;
        flash(16777215, 0.25F);
    }

    public static boolean rushing() {
        return rushTicks > 0;
    }

    public static float spinProgress(float partialTick) {
        if (spinTicks <= 0) {
            return 0.0F;
        } else {
            float f = 1.0F - ((float)spinTicks - partialTick) / (float)spinDuration;
            return Mth.clamp(f, 0.0F, 1.0F);
        }
    }

    public static float currentRoll(float partialTick) {
        return Mth.lerp(partialTick, prevRoll, roll);
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && !mc.isPaused()) {
            prevRoll = roll;
            prevKick = kick;
            if (shakeTicks > 0) {
                shakeTicks--;
            }

            kick *= 0.55F;
            fovPunch = fovPunch * (fovPunch > 0.0F ? 0.82F : 0.72F);
            if (Math.abs(fovPunch) < 0.002F) {
                fovPunch = 0.0F;
            }

            flashO = flash;
            flash = Math.max(0.0F, flash * 0.8F - 0.01F);
            if (slashTicks > 0) {
                slashTicks--;
            }

            if (spinTicks > 0) {
                spinTicks--;
            }

            if (rushTicks > 0) {
                rushTicks--;
            }

            ClientOdm odm = ClientOdm.INSTANCE;
            float target = 0.0F;
            if (odm.inFlight() && TakemConfig.CAMERA_ROLL.get()) {
                Vec3 acc = odm.velocity.subtract(odm.prevVelocity);
                float yaw = p.getYRot() * 0.017453292F;
                Vec3 rightV = new Vec3((double)(-Mth.cos(yaw)), 0.0, (double)(-Mth.sin(yaw)));
                double lateral = acc.dot(rightV);
                target = (float)Mth.clamp(lateral * 110.0, -20.0, 20.0) * TakemConfig.CAMERA_ROLL_STRENGTH.get().floatValue();
            }

            roll = roll + (target - roll) * 0.14F;
            float fovTarget = 0.0F;
            if (odm.inFlight() && TakemConfig.SPEED_FOV.get()) {
                fovTarget = (float)Mth.clamp((odm.speed - 0.55) * 0.11, 0.0, 0.3);
            }

            if (odm.ticksSinceDash < 7) {
                fovTarget += 0.09F * (1.0F - (float)odm.ticksSinceDash / 7.0F);
            }

            fovBoost = fovBoost + (fovTarget - fovBoost) * 0.3F;
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            float pt = (float)event.getPartialTick();
            event.setRoll(event.getRoll() + Mth.lerp(pt, prevRoll, roll));
            if (spinTicks > 0) {
                float f = spinProgress(pt);
                float eased = f < 0.5F ? 2.0F * f * f : 1.0F - (float)Math.pow((double)(-2.0F * f + 2.0F), 2.0) / 2.0F;
                event.setRoll(event.getRoll() + 360.0F * (float)spinTurns * eased);
            }

            event.setPitch(event.getPitch() - Mth.lerp(pt, prevKick, kick));
            if (shakeTicks > 0) {
                float s = shakeStrength * Math.max(0.0F, (float)shakeTicks - pt) / (float)shakeDuration;
                float t = (float)mc.player.tickCount + pt;
                event.setYaw(event.getYaw() + (Mth.sin(t * 2.1F) + Mth.sin(t * 3.7F + 1.3F)) * s * 1.6F);
                event.setPitch(event.getPitch() + (Mth.sin(t * 2.9F + 2.0F) + Mth.sin(t * 4.3F)) * s * 1.6F);
                event.setRoll(event.getRoll() + Mth.sin(t * 3.3F + 0.7F) * s * 1.2F);
            }
        }
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if (fovBoost > 0.001F || fovPunch != 0.0F) {
            event.setNewFovModifier(event.getNewFovModifier() * (1.0F + fovBoost + fovPunch));
        }
    }
}
