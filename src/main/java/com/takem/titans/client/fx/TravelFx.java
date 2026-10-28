package com.takem.titans.client.fx;

import com.takem.titans.client.ClientEffects;
import com.takem.titans.client.gui.ScreenFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A waystone journey. While it gathers, light spirals up around the traveller and a pale column rises; when it goes,
 * the column snaps shut on the spot they stood; at the far stone a beam comes down out of the sky and throws a ring of
 * dust off the street as they step out of it.
 */
public final class TravelFx {
    private static final int GOLD = 16766032;
    private static final int VIOLET = 11560703;

    private TravelFx() {
    }

    private static boolean self(int id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == id;
    }

    public static void charge(ClientLevel level, Vec3 at, int ticks, int id) {
        Entity e = level.getEntity(id);
        WorldFx.add(new TravelFx.Gather(e, at, ticks));
        if (self(id)) {
            ScreenFx.phase(0.6F);
        }
    }

    public static void out(ClientLevel level, Vec3 from, int id) {
        WorldFx.add(new EpicFx.Implode(from.add(0.0, 1.0, 0.0), 5.0F, 10, VIOLET));
        WorldFx.add(new WorldFx.Pillar(from, 1.4F, 60.0F, 10));
        RandomSource rnd = level.random;

        for (int i = 0; i < 40; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, from.x, from.y + 1.0, from.z, d.x * 0.3, Math.abs(d.y) * 0.6, d.z * 0.3);
        }

        if (self(id)) {
            ClientEffects.flash(16777215, 0.8F);
            ScreenFx.warp(1.0F);
        }
    }

    public static void arrive(ClientLevel level, Vec3 at, int id) {
        RandomSource rnd = level.random;
        WorldFx.add(new WorldFx.Pillar(at, 1.8F, 120.0F, 14));
        WorldFx.add(new WorldFx.ShockRing(at.add(0.0, 0.2, 0.0), 12.0F, 16, 1.0F, 0.0F, GOLD, 0.7F));
        WorldFx.add(new WorldFx.Starburst(at.add(0.0, 1.0, 0.0), 16, 5.0F, 0.25F, 12, GOLD, rnd));
        DebrisFx.surge(level, at, 1.2F, 9.0F, 1.0F, DebrisFx.dustColour(null), 20);

        for (int i = 0; i < 50; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            level.addAlwaysVisibleParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WAX_OFF, at.x, at.y + 1.0, at.z, d.x * 0.5, Math.abs(d.y) * 0.4, d.z * 0.5);
        }

        if (self(id)) {
            ClientEffects.shake(0.4F, 10);
            ScreenFx.warp(0.6F);
        }
    }

    public static void wake(ClientLevel level, Vec3 at) {
        RandomSource rnd = level.random;
        WorldFx.add(new WorldFx.Pillar(at.subtract(0.0, 0.5, 0.0), 0.9F, 90.0F, 30));
        WorldFx.add(new WorldFx.ShockRing(at, 8.0F, 18, 0.6F, 0.0F, VIOLET, 0.8F));
        WorldFx.add(new WorldFx.Starburst(at.add(0.0, 0.5, 0.0), 20, 4.0F, 0.2F, 16, VIOLET, rnd));

        for (int i = 0; i < 30; i++) {
            Vec3 d = ClientFx.randomDir(rnd);
            level.addAlwaysVisibleParticle(ParticleTypes.ENCHANT, at.x + d.x * 2.0, at.y + 1.0 + d.y, at.z + d.z * 2.0, -d.x, -d.y, -d.z);
        }
    }

    /** Light gathering round a traveller: a column that brightens and a spiral of motes climbing it. */
    static final class Gather extends WorldFx.Effect {
        final Entity who;
        final Vec3 at;

        Gather(Entity who, Vec3 at, int life) {
            super(life);
            this.who = who;
            this.at = at;
        }

        private Vec3 base(float pt) {
            return this.who != null && !this.who.isRemoved() ? this.who.getPosition(pt) : this.at;
        }

        @Override
        void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Vec3 c = this.base(1.0F);
                float p = (float)this.age / (float)this.life;
                for (int k = 0; k < 3; k++) {
                    double a = (double)((float)this.age * 0.6F) + (double)k * 2.0943951;
                    double r = 1.4 - (double)p * 0.9;
                    mc.level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + 0.2 + (double)p * 2.2, c.z + Math.sin(a) * r, 0.0, 0.04, 0.0);
                }
            }
        }

        @Override
        void render(WorldFx.Draw d, float pt) {
            float p = this.progress(pt);
            Vec3 c = this.base(pt);
            float a = Mth.clamp(p * 1.4F, 0.0F, 1.0F) * (0.35F + 0.25F * Mth.sin(((float)this.age + pt) * 0.8F));
            d.wall(c, 0.9 - (double)p * 0.3, c.y, c.y + 2.0 + (double)p * 30.0, 1.0F, 0.88F, 0.6F, a, 20);
            d.ring(c.add(0.0, 0.05, 0.0), 0.2, 1.6 - (double)p, 0.77F, 0.55F, 1.0F, a, 32);
        }
    }
}
