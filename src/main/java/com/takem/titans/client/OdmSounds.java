package com.takem.titans.client;

import com.takem.titans.item.GearTier;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.registry.ModSounds;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class OdmSounds {
    private static OdmSounds.Loop gas;
    private static OdmSounds.Loop wind;
    private static OdmSounds.Loop reel;
    private static OdmSounds.Loop afterburner;
    private static final Int2ObjectMap<OdmSounds.Loop> REMOTE_GAS = new Int2ObjectOpenHashMap();

    private OdmSounds() {
    }

    public static void startGas() {
        if (gas == null || gas.isStopped()) {
            gas = new OdmSounds.Loop(ModSounds.GAS.get(), null, () -> ClientOdm.INSTANCE.boosting ? 0.85 : 0.0, () -> 1.0, 0.35F);
            play(gas);
        }
    }

    public static void setRemoteGas(int entityId, boolean on) {
        OdmSounds.Loop existing = (OdmSounds.Loop)REMOTE_GAS.get(entityId);
        if (on && (existing == null || existing.isStopped())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }

            Entity e = mc.level.getEntity(entityId);
            if (e == null) {
                return;
            }

            OdmSounds.Loop loop = new OdmSounds.Loop(ModSounds.GAS.get(), e, () -> RemoteHooks.isBoosting(entityId) ? 0.8 : 0.0, () -> 1.0, 0.35F);
            REMOTE_GAS.put(entityId, loop);
            play(loop);
        }
    }

    private static void play(SoundInstance s) {
        Minecraft.getInstance().getSoundManager().play(s);
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            afterburner = null;
            reel = null;
            wind = null;
            gas = null;
            REMOTE_GAS.clear();
        } else {
            ClientOdm odm = ClientOdm.INSTANCE;
            if (odm.inFlight() && odm.speed > 0.45 && (wind == null || wind.isStopped())) {
                wind = new OdmSounds.Loop(
                    ModSounds.WIND.get(),
                    null,
                    () -> ClientOdm.INSTANCE.inFlight() ? Mth.clamp((ClientOdm.INSTANCE.speed - 0.4) / 2.2, 0.0, 1.0) : 0.0,
                    () -> 0.75 + Math.min(0.6, ClientOdm.INSTANCE.speed * 0.2),
                    0.08F
                );
                play(wind);
            }

            if (odm.anyReeling() && (reel == null || reel.isStopped())) {
                reel = new OdmSounds.Loop(
                    ModSounds.REEL.get(),
                    null,
                    () -> ClientOdm.INSTANCE.anyReeling() ? 0.45 : 0.0,
                    () -> ClientOdm.INSTANCE.left.reeling && ClientOdm.INSTANCE.right.reeling ? 1.12 : 1.0,
                    0.25F
                );
                play(reel);
            }

            if (odm.boosting && OdmGear.tier(p) == GearTier.GODSPEED && (afterburner == null || afterburner.isStopped())) {
                afterburner = new OdmSounds.Loop(
                    ModSounds.AFTERBURNER.get(),
                    null,
                    () -> ClientOdm.INSTANCE.boosting ? 0.9 : 0.0,
                    () -> 0.85 + Math.min(0.5, ClientOdm.INSTANCE.speed * 0.08),
                    0.2F
                );
                play(afterburner);
            }

            REMOTE_GAS.int2ObjectEntrySet().removeIf(e -> ((OdmSounds.Loop)e.getValue()).isStopped());
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final Entity follow;
        private final DoubleSupplier targetVolume;
        private final DoubleSupplier targetPitch;
        private final float fade;
        private int silentTicks;

        Loop(SoundEvent sound, Entity follow, DoubleSupplier targetVolume, DoubleSupplier targetPitch, float fade) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.follow = follow;
            this.targetVolume = targetVolume;
            this.targetPitch = targetPitch;
            this.fade = fade;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = (float)targetPitch.getAsDouble();
            if (follow == null) {
                this.relative = true;
                this.attenuation = Attenuation.NONE;
                this.x = this.y = this.z = 0.0;
            } else {
                this.x = follow.getX();
                this.y = follow.getY() + 1.0;
                this.z = follow.getZ();
            }
        }

        @Override
        public void tick() {
            if (this.follow != null) {
                if (this.follow.isRemoved()) {
                    this.stop();
                    return;
                }

                this.x = this.follow.getX();
                this.y = this.follow.getY() + 1.0;
                this.z = this.follow.getZ();
            }

            float target = (float)this.targetVolume.getAsDouble();
            this.volume = this.volume + Mth.clamp(target - this.volume, -this.fade, this.fade);
            this.pitch = this.pitch + ((float)this.targetPitch.getAsDouble() - this.pitch) * 0.2F;
            if (this.volume <= 0.01F && target <= 0.0F) {
                if (++this.silentTicks > 4) {
                    this.stop();
                }
            } else {
                this.silentTicks = 0;
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public boolean canPlaySound() {
            return true;
        }
    }
}
