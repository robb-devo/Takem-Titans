package com.takem.titans.client;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.ArmoredTitan;
import com.takem.titans.entity.titan.BeastTitan;
import com.takem.titans.entity.titan.BossTitan;
import com.takem.titans.entity.titan.ColossalTitan;
import com.takem.titans.entity.titan.RavagerTitan;
import com.takem.titans.entity.titan.TrainingDummy;
import com.takem.titans.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

/**
 * The score follows the fight: a quiet theme now and then while the land is calm, a drone and a heartbeat drum once
 * titans are about, war drums when blades are out among them, and the boss theme near a Titan Shifter. One layer plays
 * at a time and they cross-fade; vanilla music keeps quiet while any of them does.
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class MusicDirector {
    private static final int EXPLORE = 0;
    private static final int DANGER = 1;
    private static final int BATTLE = 2;
    private static final int BOSS = 3;
    private static final Layer[] LAYERS = new Layer[4];
    private static int want = -1;
    private static int sinceCombat = 10000;
    private static int calm;
    private static int exploreLeft;
    private static int exploreRest = 20 * 90;
    private static int holdBattle;

    private MusicDirector() {
    }

    /** A blade bit, a titan died, a hand closed round the player: the fight is on. */
    public static void combat() {
        sinceCombat = 0;
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            for (int i = 0; i < LAYERS.length; i++) {
                if (LAYERS[i] != null) {
                    LAYERS[i].stopNow();
                    LAYERS[i] = null;
                }
            }

            want = -1;
            return;
        }

        if (mc.isPaused()) {
            return;
        }

        sinceCombat++;
        double nearest = Double.MAX_VALUE;
        boolean boss = false;
        int titans = 0;

        for (AbstractTitan t : mc.level.getEntitiesOfClass(AbstractTitan.class, mc.player.getBoundingBox().inflate(130.0))) {
            if (!t.isAlive() || t.dyingTicks > 0 || t instanceof TrainingDummy) {
                continue;
            }

            double d = t.distanceTo(mc.player) - t.getBbWidth() * 0.5;
            if (isBoss(t) && d < 120.0) {
                boss = true;
            }

            if (d < 64.0) {
                titans++;
                nearest = Math.min(nearest, d);
            }
        }

        int next;
        if (boss) {
            next = BOSS;
        } else if (titans > 0 && (sinceCombat < 20 * 12 || nearest < 22.0)) {
            holdBattle = 20 * 8;
            next = BATTLE;
        } else if (holdBattle > 0 && titans > 0) {
            holdBattle--;
            next = BATTLE;
        } else if (titans > 0) {
            next = DANGER;
        } else {
            next = -1;
        }

        // the quiet theme is a treat, not wallpaper: after a long calm it plays for a while, then rests for minutes
        if (next == -1) {
            calm++;
            if (exploreLeft > 0) {
                exploreLeft--;
                next = EXPLORE;
                if (exploreLeft == 0) {
                    exploreRest = 20 * (180 + mc.level.random.nextInt(180));
                }
            } else if (exploreRest > 0) {
                exploreRest--;
            } else if (calm > 20 * 20) {
                exploreLeft = 20 * 115;
                next = EXPLORE;
            }
        } else {
            calm = 0;
            exploreLeft = 0;
        }

        want = next;
        boolean any = false;

        for (int i = 0; i < LAYERS.length; i++) {
            Layer l = LAYERS[i];
            if (i == want && (l == null || l.isStopped())) {
                l = new Layer(sound(i), i);
                LAYERS[i] = l;
                mc.getSoundManager().play(l);
            }

            if (l != null && !l.isStopped()) {
                any = true;
            }
        }

        if (any) {
            mc.getMusicManager().stopPlaying();
        }
    }

    private static boolean isBoss(AbstractTitan t) {
        return t instanceof ArmoredTitan || t instanceof BeastTitan || t instanceof BossTitan || t instanceof ColossalTitan || t instanceof RavagerTitan;
    }

    private static SoundEvent sound(int layer) {
        return switch (layer) {
            case EXPLORE -> ModSounds.MUSIC_EXPLORE.get();
            case DANGER -> ModSounds.MUSIC_DANGER.get();
            case BATTLE -> ModSounds.MUSIC_BATTLE.get();
            default -> ModSounds.BOSS_MUSIC.get();
        };
    }

    private static final class Layer extends AbstractTickableSoundInstance {
        private final int index;
        private float fade;

        Layer(SoundEvent event, int index) {
            super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.index = index;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void stopNow() {
            this.stop();
        }

        @Override
        public void tick() {
            boolean on = MusicDirector.want == this.index;
            // fights swell in fast and ebb slowly; the quiet theme drifts in
            float in = this.index == EXPLORE ? 0.006F : (this.index == DANGER ? 0.012F : 0.03F);
            float out = this.index == BATTLE || this.index == BOSS ? 0.008F : 0.012F;
            this.fade = Mth.clamp(this.fade + (on ? in : -out), 0.0F, 1.0F);
            float level = this.index == EXPLORE ? 0.55F : (this.index == DANGER ? 0.7F : 0.85F);
            this.volume = Math.max(0.001F, this.fade * this.fade * level);
            if (!on && this.fade <= 0.0F) {
                this.stop();
            }
        }
    }
}
