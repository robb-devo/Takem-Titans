package com.takem.titans.client.fx;

import com.takem.titans.entity.FallingSectionEntity;
import com.takem.titans.network.FxPayload;
import com.takem.titans.network.SectionDetachPayload;
import com.takem.titans.registry.ModParticles;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientFx {
    public static final int WHITE_BLUE = 13625599;
    public static final int ACKERMAN_RED = 16720440;
    public static final int DUST = 12103068;

    private ClientFx() {
    }

    public static void handle(FxPayload msg, IPayloadContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != null) {
            RandomSource rnd = level.random;
            Vec3 at = new Vec3(msg.x(), msg.y(), msg.z());
            switch (msg.kind()) {
                case 0:
                    dustPlume(level, at, msg.a(), msg.b(), DebrisFx.stateOf(msg.c()));
                    break;
                case 1:
                    DebrisFx.collapse(level, at, msg.a(), msg.b(), null, (int)(msg.a() * 12.0F));
                    break;
                case 30:
                    // a falling piece of building lands: a = diameter, b = impact speed, c = material, d = blocks
                    DebrisFx.collapse(level, at, msg.a(), msg.b(), DebrisFx.stateOf(msg.c()), (int)msg.d());
                    break;
                case 31:
                    // a = fragments, b = burst speed (0 = trickle), c = material, d = source width
                    DebrisFx.crumble(level, at, DebrisFx.stateOf(msg.c()), (int)msg.a(), msg.b(), msg.d());
                    break;
                case 32:
                    HyperFx.phase(level, at, (int)msg.a(), (int)msg.b());
                    break;
                case 33:
                    HyperFx.jump(level, at, new Vec3((double)msg.a(), (double)msg.b(), (double)msg.c()), (int)msg.d());
                    break;
                case 35:
                    TravelFx.charge(level, at, (int)msg.a(), (int)msg.b());
                    break;
                case 36:
                    TravelFx.out(level, at, (int)msg.d());
                    break;
                case 37:
                    TravelFx.arrive(level, at, (int)msg.a());
                    break;
                case 38:
                    TravelFx.wake(level, at);
                    break;
                case 39:
                    BossFx.scream(level, at, msg.a());
                    break;
                case 42:
                    BossFx.crystal(level, at, msg.a());
                    break;
                case 43:
                    BossFx.harden(level, at, msg.a() > 0.5F);
                    break;
                case 44:
                    BossFx.muzzle(level, at, new Vec3((double)msg.a(), (double)msg.b(), (double)msg.c()));
                    break;
                case 45:
                    BossFx.forge(level, at);
                    break;
                case 46:
                    RendFx.rend(level, at, new Vec3((double)msg.a(), (double)msg.b(), (double)msg.c()), msg.d());
                    break;
                case 47:
                    RendFx.thread(level, at, new Vec3((double)msg.a(), (double)msg.b(), (double)msg.c()));
                    break;
                case 48:
                    RendFx.snap(level, at);
                    break;
                case 49:
                    GoreFx.sinew(level, (int)msg.a(), (int)msg.b(), (int)msg.c(), msg.d());
                    break;
                case 50:
                    BuildFx.placed(level, at, (int)msg.a());
                    break;
                case 34:
                    SeverFx.nova(level, at, (int)msg.a());
                    break;
                case 2:
                    int life = Math.max(6, (int)(msg.a() / Math.max(0.5F, msg.b())));
                    WorldFx.add(new WorldFx.ShockRing(at, msg.a(), life, Math.max(1.0F, msg.a() * 0.06F), msg.c(), 16773340, 0.55F));
                    int n = Mth.clamp((int)(msg.a() * 2.0F), 16, 120);

                    for (int i = 0; i < n; i++) {
                        double ang = (double)(6.2831855F * (float)i / (float)n);
                        double sp = (double)msg.b() * (0.6 + rnd.nextDouble() * 0.5);
                        spawn(
                            level,
                            ModParticles.DUST_CLOUD.get(),
                            at.x + Math.cos(ang) * 1.5,
                            at.y + 0.3,
                            at.z + Math.sin(ang) * 1.5,
                            Math.cos(ang) * sp,
                            0.02,
                            Math.sin(ang) * sp,
                            1.2F + rnd.nextFloat()
                        );
                    }
                    break;
                case 3: {
                    boolean red = msg.d() < 0.0F;
                    float rad = Math.abs(msg.d());
                    Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
                    Vec3 rel = at.subtract(cam.getPosition());
                    Vec3 look = new Vec3(cam.getLookVector());
                    double depth = rel.dot(look);
                    Vec3 arcAt = rel.length() < (double)rad + 2.0 && depth < (double)rad * 0.5 ? at.add(look.scale((double)rad * 0.5 - depth)) : at;
                    WorldFx.add(new WorldFx.SlashArc(arcAt, msg.a(), msg.b(), msg.c(), rad, red ? 16720440 : 13625599, rad > 3.5F));
                    break;
                }
                case 4:
                    spearBlast(level, at, msg.a());
                    break;
                case 5: {
                    Vec3 to = new Vec3((double)msg.a(), (double)msg.b(), (double)msg.c());
                    boolean red = msg.d() < 0.0F;
                    WorldFx.add(new WorldFx.RushTrail(at, to, Math.abs(msg.d()), red ? 16720440 : 13625599));
                    int count = (int)Math.min(40.0, at.distanceTo(to) * 2.0);
                    Vec3 dir = to.subtract(at).normalize();

                    for (int i = 0; i < count; i++) {
                        Vec3 p = at.lerp(to, rnd.nextDouble());
                        level.addAlwaysVisibleParticle(
                            ModParticles.GAS.get(),
                            p.x,
                            p.y,
                            p.z,
                            dir.x * 0.1 + (rnd.nextDouble() - 0.5) * 0.1,
                            (rnd.nextDouble() - 0.5) * 0.1,
                            dir.z * 0.1 + (rnd.nextDouble() - 0.5) * 0.1
                        );
                    }
                    break;
                }
                case 6:
                    cyclone(level, at, msg.a(), msg.b(), msg.c(), msg.d() < 0.0F);
                    break;
                case 7:
                    bloodBurst(level, at, new Vec3((double)msg.a(), (double)msg.b(), (double)msg.c()), msg.d());
                    break;
                case 8:
                    Entity e = level.getEntity((int)msg.a());
                    if (e == null) {
                        return;
                    }

                    Vec3 c = e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0);

                    for (int i = 0; i < 2; i++) {
                        Vec3 a = c.add((rnd.nextDouble() - 0.5) * 1.6, (rnd.nextDouble() - 0.5) * 2.0, (rnd.nextDouble() - 0.5) * 1.6);
                        Vec3 b = a.add((rnd.nextDouble() - 0.5) * 1.6, (rnd.nextDouble() - 0.5) * 1.6, (rnd.nextDouble() - 0.5) * 1.6);
                        WorldFx.add(new WorldFx.Bolt(a, b, 0.05F, 16720440, rnd));
                    }
                    break;
                case 9:
                case 10:
                case 12:
                case 15:
                case 16:
                case 17:
                case 18:
                case 19:
                case 20:
                case 21:
                case 22:
                case 23:
                case 24:
                case 25:
                default:
                    EpicFx.handle(level, msg, at);
                    break;
                case 60:
                case 61:
                case 62:
                case 63:
                case 64:
                case 65:
                case 66:
                case 67:
                case 68:
                    CombatFx.handle(level, msg, at);
                    break;
                case 11:
                    Vec3 top = at.add((rnd.nextDouble() - 0.5) * 6.0, (double)msg.a(), (rnd.nextDouble() - 0.5) * 6.0);
                    WorldFx.add(new WorldFx.Bolt(top, at, 0.35F, 16771496, rnd));

                    for (int i = 0; i < 12; i++) {
                        level.addAlwaysVisibleParticle(
                            ModParticles.EMBER.get(),
                            at.x,
                            at.y + 0.5,
                            at.z,
                            (rnd.nextDouble() - 0.5) * 0.6,
                            rnd.nextDouble() * 0.5,
                            (rnd.nextDouble() - 0.5) * 0.6
                        );
                    }
                    break;
                case 13:
                    WorldFx.add(new WorldFx.ShockRing(at.add(0.0, -0.9, 0.0), 14.0F, 14, 1.4F, 3.0F, 16720440, 0.8F));
                    WorldFx.add(new WorldFx.Starburst(at, 18, 7.0F, 0.5F, 14, 16720440, rnd));

                    for (int i = 0; i < 10; i++) {
                        Vec3 a = at.add(randomDir(rnd).scale(0.6));
                        WorldFx.add(new WorldFx.Bolt(a, a.add(randomDir(rnd).scale(4.0 + rnd.nextDouble() * 5.0)), 0.08F, 16720440, rnd));
                    }

                    for (int i = 0; i < 60; i++) {
                        Vec3 d = randomDir(rnd);
                        level.addAlwaysVisibleParticle(ModParticles.EMBER.get(), at.x, at.y, at.z, d.x * 0.6, d.y * 0.6, d.z * 0.6);
                    }
                    break;
                case 14: {
                    float h = Math.max(4.0F, msg.a());
                    boolean red = msg.b() > 0.0F;
                    int color = red ? 16720440 : 16774352;
                    Vec3 burst = clearOfCamera(at, Math.max(5.0, (double)h * 0.6));
                    WorldFx.add(new WorldFx.Starburst(burst, 26, h * 0.9F, h * 0.06F, 12, color, rnd));
                    WorldFx.add(new WorldFx.ShockRing(burst, h * 1.6F, 14, h * 0.06F, 0.0F, color, 0.7F));
                    WorldFx.add(new WorldFx.Flash(burst, h * 0.35F, 5));
                    // the bigger the titan, the more there is in it
                    bloodBurst(level, at, new Vec3(0.0, 1.0, 0.0), 6.0F + h * 0.35F);
                    bloodBurst(level, at, randomDir(rnd), 4.0F + h * 0.15F);
                    WorldFx.add(new WorldFx.Geyser(at, h, 26));
                    Vec3 s = clearOfCamera(at, 4.0);

                    for (int i = 0; i < 30; i++) {
                        Vec3 d = randomDir(rnd);
                        spawn(level, ModParticles.STEAM.get(), s.x, s.y, s.z, d.x * 0.25, d.y * 0.25 + 0.1, d.z * 0.25, 2.5F);
                    }
                    break;
                }
                case 40: {
                    // the Coordinate: a violet bolt from the fallen nape to the next one
                    Vec3 to = new Vec3(msg.a(), msg.b(), msg.c());
                    WorldFx.add(new WorldFx.Bolt(at, to, 0.45F, 11560703, rnd));
                    WorldFx.add(new WorldFx.Bolt(at, to, 0.15F, 16771496, rnd));
                    WorldFx.add(new WorldFx.Bolt(at, to.add(randomDir(rnd).scale(3.0)), 0.08F, 11560703, rnd));
                    WorldFx.add(new WorldFx.Starburst(to, 14, 6.0F, 0.4F, 12, 11560703, rnd));
                    WorldFx.add(new WorldFx.ShockRing(to, 9.0F, 12, 0.8F, 0.0F, 11560703, 0.7F));

                    for (int i = 0; i < 24; i++) {
                        Vec3 d = randomDir(rnd);
                        level.addAlwaysVisibleParticle(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, to.x, to.y, to.z, d.x * 0.5, d.y * 0.5, d.z * 0.5);
                    }
                    break;
                }
                case 41: {
                    float r = msg.a();
                    Vec3 fc = clearOfCamera(at, 6.0);
                    WorldFx.add(new WorldFx.Flash(fc, r * 0.6F, 8));
                    WorldFx.add(new WorldFx.ShockRing(at, r * 2.2F, 22, r * 0.08F, 0.0F, 11560703, 0.8F));
                    WorldFx.add(new WorldFx.ShockRing(at.add(0.0, -1.0, 0.0), r * 1.5F, 16, r * 0.05F, 0.0F, 16766032, 0.6F));
                    WorldFx.add(new WorldFx.Starburst(fc, 30, r * 0.8F, r * 0.05F, 14, 11560703, rnd));

                    for (int i = 0; i < 8; i++) {
                        WorldFx.add(new WorldFx.Bolt(at, at.add(randomDir(rnd).scale(r * (0.4 + rnd.nextDouble() * 0.5))), 0.12F, 11560703, rnd));
                    }

                    for (int i = 0; i < 60; i++) {
                        Vec3 d = randomDir(rnd);
                        level.addAlwaysVisibleParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WITCH, at.x, at.y, at.z, d.x * 0.9, d.y * 0.9, d.z * 0.9);
                    }
                    break;
                }
                case 26:
                    EpicFx.megaMeteor(level, at, msg.a(), msg.b(), msg.c() > 0.5F, msg.d() > 0.5F, rnd);
                    break;
                case 27:
                    EpicFx.novaFireball(level, at, msg.a(), msg.b() > 0.5F, rnd);
                    break;
                case 28:
                    EpicFx.thermoCloud(level, at, msg.a(), rnd);
                    break;
                case 29:
                    EpicFx.thermoBlast(level, at, msg.a(), rnd);
            }
        }
    }

    public static void handleDetach(SectionDetachPayload msg, IPayloadContext ctx) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(msg.entityId()) instanceof FallingSectionEntity s) {
            s.clientDetach(msg.indices());
        }
    }

    static Particle spawn(ClientLevel level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz, float scale) {
        if (type == ModParticles.DUST_CLOUD.get() && level.random.nextInt(5) < 2) {
            // two dust puffs in five are enough: the plume reads the same and the blades stay in view
            return null;
        }

        Particle p = Minecraft.getInstance().particleEngine.createParticle(type, x, y, z, vx, vy, vz);
        if (p != null && scale != 1.0F) {
            p.scale(scale);
        }

        return p;
    }

    public static void dustPlume(ClientLevel level, Vec3 at, float radius, float thickness) {
        dustPlume(level, at, radius, thickness, null);
    }

    /** A burst of dust, tinted by what it came from ({@code mat} null: plain mortar dust). */
    public static void dustPlume(ClientLevel level, Vec3 at, float radius, float thickness, @Nullable BlockState mat) {
        RandomSource rnd = level.random;
        float[] rgb = DebrisFx.dustColour(mat);
        int n = Mth.clamp((int)(4.0F + radius * 1.8F * thickness), 4, 66);

        for (int i = 0; i < n; i++) {
            double ang = rnd.nextDouble() * 6.2831854820251465;
            double d = Math.sqrt(rnd.nextDouble()) * (double)radius * 0.6;
            double sp = 0.04 + rnd.nextDouble() * 0.1 * (0.5 + (double)radius / 8.0);
            DebrisFx.dust(
                ModParticles.DUST_CLOUD.get(),
                at.x + Math.cos(ang) * d,
                at.y + rnd.nextDouble() * (double)radius * 0.25,
                at.z + Math.sin(ang) * d,
                Math.cos(ang) * sp,
                0.01 + rnd.nextDouble() * 0.04,
                Math.sin(ang) * sp,
                Mth.clamp(radius / 5.0F, 0.6F, 2.2F) * (0.7F + rnd.nextFloat() * 0.6F),
                rgb
            );
        }
    }

    static void spearBlast(ClientLevel level, Vec3 at, float radius) {
        RandomSource rnd = level.random;
        WorldFx.add(new WorldFx.Fireball(at, radius * 1.1F, 3, 16));
        WorldFx.add(new WorldFx.ShockRing(at, radius * 4.0F, 12, 1.0F, radius * 0.8F, 16769712, 0.6F));

        for (int i = 0; i < 46; i++) {
            Vec3 d = randomDir(rnd);
            double sp = 0.15 + rnd.nextDouble() * 0.35;
            spawn(
                level,
                ModParticles.FIREBALL.get(),
                at.x + d.x,
                at.y + d.y,
                at.z + d.z,
                d.x * sp,
                d.y * sp + 0.05,
                d.z * sp,
                0.7F + rnd.nextFloat() * radius / 4.0F
            );
        }

        for (int i = 0; i < 70; i++) {
            Vec3 d = randomDir(rnd);
            double sp = 0.4 + rnd.nextDouble() * 0.9;
            level.addAlwaysVisibleParticle(ModParticles.EMBER.get(), at.x, at.y, at.z, d.x * sp, Math.abs(d.y) * sp + 0.2, d.z * sp);
        }

        dustPlume(level, at.add(0.0, (double)(-radius) * 0.5, 0.0), radius * 1.5F, 1.5F);
    }

    static void cyclone(ClientLevel level, Vec3 at, float radius, float yaw, float pitch, boolean red) {
        RandomSource rnd = level.random;

        for (int k = 0; k < 3; k++) {
            WorldFx.add(new WorldFx.SlashArc(at, yaw, pitch, rnd.nextFloat() * 360.0F, radius * (0.8F + 0.2F * (float)k), red ? 16720440 : 13625599, k == 0));
        }

        float yr = yaw * 0.017453292F;
        float pr = pitch * 0.017453292F;
        Vec3 fwd = new Vec3((double)(-Mth.sin(yr) * Mth.cos(pr)), (double)(-Mth.sin(pr)), (double)(Mth.cos(yr) * Mth.cos(pr)));
        Vec3 side = fwd.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        }

        side = side.normalize();
        Vec3 up = side.cross(fwd).normalize();

        for (int i = 0; i < 26; i++) {
            double a = rnd.nextDouble() * 6.2831854820251465;
            Vec3 out = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 tangent = fwd.cross(out);
            double sp = 0.5 + rnd.nextDouble() * 0.6;
            Vec3 v = out.scale(sp).add(tangent.scale(sp * 0.8));
            Vec3 p = at.add(out.scale((double)radius * 0.6));
            level.addAlwaysVisibleParticle(ModParticles.BLOOD.get(), p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    static void bloodBurst(ClientLevel level, Vec3 at, Vec3 dir, float strength) {
        RandomSource rnd = level.random;
        Vec3 d0 = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : dir.normalize();
        int n = Mth.clamp((int)(strength * 14.0F), 10, 120);
        at = clearOfCamera(at, 2.5);

        for (int i = 0; i < n; i++) {
            Vec3 d = d0.add(randomDir(rnd).scale(0.55)).normalize();
            double sp = (0.35 + rnd.nextDouble() * 0.75) * Math.min(2.5, 0.6 + (double)strength * 0.25);
            level.addAlwaysVisibleParticle(ModParticles.BLOOD.get(), at.x, at.y, at.z, d.x * sp, d.y * sp + 0.1, d.z * sp);
        }

        for (int i = 0; i < n / 4; i++) {
            Vec3 d = d0.add(randomDir(rnd).scale(0.8));
            spawn(level, ModParticles.STEAM.get(), at.x, at.y, at.z, d.x * 0.08, d.y * 0.08 + 0.04, d.z * 0.08, 1.4F);
        }
    }

    static Vec3 clearOfCamera(Vec3 at, double min) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 off = at.subtract(cam.getPosition());
        double d = off.length();
        if (d >= min) {
            return at;
        } else {
            Vec3 dir = d < 0.5 ? new Vec3(cam.getLookVector()) : off.scale(1.0 / d);
            return cam.getPosition().add(dir.scale(min));
        }
    }

    public static Vec3 randomDir(RandomSource rnd) {
        double u = rnd.nextDouble() * 2.0 - 1.0;
        double a = rnd.nextDouble() * 6.2831854820251465;
        double s = Math.sqrt(1.0 - u * u);
        return new Vec3(s * Math.cos(a), u, s * Math.sin(a));
    }
}
