package com.takem.titans.client.fx;

import com.takem.titans.client.particle.DustParticle;
import com.takem.titans.client.particle.RubbleParticle;
import com.takem.titans.fx.LocalFx;
import com.takem.titans.registry.ModParticles;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.common.Tags;

/**
 * What breaking buildings look like up close. Everything here is spawned on the client from a single small packet
 * (or from the client's own copy of a falling block), so a collapse can throw hundreds of fragments and a street-wide
 * dust surge without any of it crossing the network.
 * <p>
 * The ingredients, mixed per event:
 * <ul>
 * <li><b>rubble</b> - 3D fragments of the very block that broke, on ballistic arcs, bouncing and settling;</li>
 * <li><b>billow</b> - dust tinted by the material (brick dust is red, plaster white, timber brown) that swells up where
 * the impact was;</li>
 * <li><b>surge</b> - the low dust wave that rolls out along the street from a big collapse and banks up at walls;</li>
 * <li><b>haze</b> - the faint pall left hanging over the ruin.</li>
 * </ul>
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class DebrisFx {
    /** Fragments in flight at once, beyond which impacts throw fewer. */
    private static final int RUBBLE_CAP = 1600;
    /** What falling blocks and sections draw on the client by themselves. */
    public static final LocalFx.Sink LOCAL = new LocalFx.Sink() {
        @Override
        public void debrisImpact(Level level, Vec3 at, BlockState s, double kick, Vec3 v) {
            if (level instanceof ClientLevel cl) {
                DebrisFx.debrisImpact(cl, at, s, kick, v);
            }
        }

        @Override
        public void crumbleOff(Level level, Vec3 at, BlockState s, Vec3 v, float dust) {
            if (level instanceof ClientLevel cl) {
                DebrisFx.crumbleOff(cl, at, s, v, dust);
            }
        }
    };

    private DebrisFx() {
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        RubbleParticle.endTick();
    }

    @Nullable
    public static BlockState stateOf(float id) {
        int i = (int)id;
        if (i <= 0) {
            return null;
        } else {
            BlockState s = Block.stateById(i);
            return s.isAir() ? null : s;
        }
    }

    /** How many of {@code wanted} fragments still fit under the cap. */
    private static int room(int wanted) {
        int free = RUBBLE_CAP - RubbleParticle.live();
        if (free <= 0) {
            return 0;
        } else {
            // thin out gradually as the sky fills, rather than cutting off hard
            float k = Mth.clamp((float)free / (float)RUBBLE_CAP * 1.6F, 0.15F, 1.0F);
            return Math.min(free, Math.max(1, Math.round((float)wanted * k)));
        }
    }

    /** Dust colour for a material: its map colour, washed out towards pale mortar, because powder is always lighter. */
    public static float[] dustColour(@Nullable BlockState s) {
        if (s == null) {
            return new float[]{DustParticle.DUST_R, DustParticle.DUST_G, DustParticle.DUST_B};
        } else {
            MapColor mc = s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            int c = mc == null || mc == MapColor.NONE ? 0 : mc.col;
            if (c == 0) {
                return new float[]{DustParticle.DUST_R, DustParticle.DUST_G, DustParticle.DUST_B};
            } else {
                float r = (float)(c >> 16 & 0xFF) / 255.0F;
                float g = (float)(c >> 8 & 0xFF) / 255.0F;
                float b = (float)(c & 0xFF) / 255.0F;
                float k = 0.5F;
                return new float[]{
                    Mth.lerp(k, r, DustParticle.DUST_R) * 0.88F + 0.1F, Mth.lerp(k, g, DustParticle.DUST_G) * 0.88F + 0.1F, Mth.lerp(k, b, DustParticle.DUST_B) * 0.88F + 0.1F
                };
            }
        }
    }

    private static boolean dustless(@Nullable BlockState s) {
        return s != null && (s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES) || s.getSoundType() == SoundType.GLASS || s.is(BlockTags.LEAVES));
    }

    /** Top of the first solid block at or below {@code at}, searching {@code depth} blocks down. */
    public static double groundY(ClientLevel level, Vec3 at, int depth) {
        MutableBlockPos m = BlockPos.containing(at).mutable();
        for (int i = 0; i < depth; i++) {
            BlockState s = level.getBlockState(m);
            if (!s.getCollisionShape(level, m).isEmpty()) {
                double top = s.getCollisionShape(level, m).max(net.minecraft.core.Direction.Axis.Y);
                return (double)m.getY() + top;
            }

            m.move(0, -1, 0);
        }

        return at.y;
    }

    @Nullable
    private static Particle make(ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        Minecraft mc = Minecraft.getInstance();
        ParticleStatus status = mc.options.particles().get();
        // the player's particle setting still counts, even though these skip the vanilla limiter
        if (status != ParticleStatus.ALL && mc.level != null && mc.level.random.nextFloat() < (status == ParticleStatus.MINIMAL ? 0.8F : 0.4F)) {
            return null;
        } else {
            return mc.particleEngine.createParticle(type, x, y, z, vx, vy, vz);
        }
    }

    /**
     * Detail by distance: fragments are specks beyond a few dozen blocks and not worth simulating, while dust clouds
     * stay big enough to read from far away and only thin out a little.
     */
    private static float lod(Vec3 at) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(at);
        return d < 40.0 ? 1.0F : (float)Math.max(0.12, 40.0 / d);
    }

    private static int scaled(int n, float k) {
        return n <= 0 ? 0 : Math.max(1, Math.round((float)n * k));
    }

    @Nullable
    static DustParticle dust(ParticleOptions type, double x, double y, double z, double vx, double vy, double vz, float scale, float[] rgb) {
        if (sideFilter != 0 && com.takem.titans.world.ShiganshinaLayout.region(x, z).ordinal() + 1 != sideFilter) {
            return null;
        }

        if (make(type, x, y, z, vx, vy, vz) instanceof DustParticle d) {
            // a little light and dark between puffs, or the cloud looks like one flat sticker
            float shade = 0.9F + (float)Math.random() * 0.18F;
            d.setColor(rgb[0] * shade, rgb[1] * shade, rgb[2] * shade);
            d.scale(scale);
            return d;
        } else {
            return null;
        }
    }

    static void rubble(BlockState s, double x, double y, double z, double vx, double vy, double vz) {
        make(new BlockParticleOption(ModParticles.RUBBLE.get(), s), x, y, z, vx, vy, vz);
    }

    static Vec3 randomDir(RandomSource rnd) {
        return ClientFx.randomDir(rnd);
    }

    /**
     * A falling piece of building hits the ground (or another building). {@code size} is the piece's diameter,
     * {@code speed} how hard it came down, {@code blocks} how much of it there was.
     */
    public static void collapse(ClientLevel level, Vec3 at, float size, float speed, @Nullable BlockState mat, int blocks) {
        RandomSource rnd = level.random;
        BlockState s = mat != null ? mat : Blocks.COBBLESTONE.defaultBlockState();
        float[] rgb = dustColour(mat);
        double ground = groundY(level, at.add(0.0, 1.0, 0.0), 8);
        float hit = Mth.clamp(speed, 0.2F, 1.6F);
        float r = Math.max(1.5F, size * 0.5F);
        float lod = lod(at);
        float far = Math.max(0.35F, lod);
        // the fragments: most fly out low and fast, a few are flung high
        int n = room(scaled(Mth.clamp(10 + blocks / 5, 10, 110), lod * lod));
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(rnd.nextDouble()) * (double)r * 0.6;
            double out = (0.06 + rnd.nextDouble() * 0.22) * (0.6 + (double)hit);
            double up = rnd.nextFloat() < 0.15F ? 0.35 + rnd.nextDouble() * 0.3 : 0.08 + rnd.nextDouble() * 0.24;
            rubble(
                s,
                at.x + Math.cos(a) * d,
                Math.max(ground + 0.05, at.y - 0.5 + rnd.nextDouble() * Math.min(3.0, (double)r * 0.4)),
                at.z + Math.sin(a) * d,
                Math.cos(a) * out,
                up * (0.6 + (double)hit * 0.5),
                Math.sin(a) * out
            );
        }

        if (!dustless(mat)) {
            // the billow over the impact: low and wide first, then swelling upwards
            int billow = scaled(Mth.clamp((int)(5.0F + size * 1.3F + (float)blocks / 40.0F), 6, 48), far);
            for (int i = 0; i < billow; i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double d = Math.sqrt(rnd.nextDouble()) * (double)r * 0.7;
                double h = rnd.nextDouble() * rnd.nextDouble() * (double)Math.min(10.0F, r * 0.9F);
                double sp = (0.02 + rnd.nextDouble() * 0.06) * (double)(0.5F + hit);
                dust(
                    ModParticles.DUST_CLOUD.get(),
                    at.x + Math.cos(a) * d,
                    ground + 0.3 + h,
                    at.z + Math.sin(a) * d,
                    Math.cos(a) * sp,
                    0.015 + rnd.nextDouble() * 0.05 * (double)hit,
                    Math.sin(a) * sp,
                    Mth.clamp(size / 7.0F, 0.7F, 2.4F) * (0.7F + rnd.nextFloat() * 0.6F),
                    rgb
                );
            }

            // the surge that runs out along the ground
            float reach = Math.min(42.0F, 4.0F + size * 1.1F + (float)blocks / 30.0F) * (0.5F + hit * 0.5F);
            surge(level, new Vec3(at.x, ground, at.z), r * 0.5F, reach, Math.min(3.0F, 0.6F + size / 10.0F), rgb, scaled(Mth.clamp((int)(6.0F + size * 1.6F), 8, 64), far));
            if (blocks > 80) {
                haze(level, new Vec3(at.x, ground, at.z), Math.min(30.0F, r * 1.5F + 4.0F), Mth.clamp(blocks / 120, 1, 7), rgb);
            }
        }
    }

    /**
     * Rubble breaking off something: grit trickling from a crack ({@code speed <= 0}) or a burst of chips flung out
     * from {@code at} ({@code speed > 0}). {@code spread} is how wide the source is.
     */
    public static void crumble(ClientLevel level, Vec3 at, @Nullable BlockState mat, int count, float speed, float spread) {
        RandomSource rnd = level.random;
        BlockState s = mat != null ? mat : Blocks.COBBLESTONE.defaultBlockState();
        float lod = lod(at);
        int n = room(scaled(Mth.clamp(count, 1, 40), lod * lod));
        double half = Math.max(0.2, (double)spread * 0.5);
        for (int i = 0; i < n; i++) {
            double x = at.x + (rnd.nextDouble() - 0.5) * half * 2.0;
            double y = at.y + (rnd.nextDouble() - 0.5) * half * 2.0;
            double z = at.z + (rnd.nextDouble() - 0.5) * half * 2.0;
            if (speed > 0.0F) {
                Vec3 d = randomDir(rnd);
                double sp = (double)speed * (0.4 + rnd.nextDouble() * 0.8);
                rubble(s, x, y, z, d.x * sp, Math.abs(d.y) * sp * 0.8 + 0.06, d.z * sp);
            } else {
                rubble(s, x, y, z, (rnd.nextDouble() - 0.5) * 0.06, -rnd.nextDouble() * 0.04, (rnd.nextDouble() - 0.5) * 0.06);
            }
        }

        if (!dustless(mat)) {
            float[] rgb = dustColour(mat);
            int puffs = scaled(Mth.clamp(1 + count / 5, 1, 6), Math.max(0.35F, lod));
            for (int i = 0; i < puffs; i++) {
                double sp = Math.max(0.0, (double)speed) * 0.15;
                DustParticle d = dust(
                    ModParticles.DUST_CLOUD.get(),
                    at.x + (rnd.nextDouble() - 0.5) * half,
                    at.y + (rnd.nextDouble() - 0.5) * half,
                    at.z + (rnd.nextDouble() - 0.5) * half,
                    (rnd.nextDouble() - 0.5) * (0.03 + sp),
                    speed > 0.0F ? 0.01 + rnd.nextDouble() * 0.03 : -0.01,
                    (rnd.nextDouble() - 0.5) * (0.03 + sp),
                    Mth.clamp(spread * 0.35F, 0.35F, 1.2F) * (0.7F + rnd.nextFloat() * 0.5F),
                    rgb
                );
                if (d != null) {
                    d.life(0.55F);
                }
            }
        }
    }

    /**
     * The low dust wave of a collapse. It starts as a ring {@code r0} wide around {@code at} (on the ground) and runs
     * out about {@code reach} blocks, {@code height} deep, slowing as it spreads.
     */
    public static void surge(ClientLevel level, Vec3 at, float r0, float reach, float height, float[] rgb, int count) {
        RandomSource rnd = level.random;
        for (int i = 0; i < count; i++) {
            double a = (double)i / (double)count * Math.PI * 2.0 + rnd.nextDouble() * 0.4;
            double d = (double)r0 * (0.6 + rnd.nextDouble() * 0.6);
            // the wave's drag (0.962 a tick) means it covers about speed / 0.038 blocks before it stalls
            double sp = (double)reach * 0.038 * (0.6 + rnd.nextDouble() * 0.6);
            dust(
                ModParticles.DUST_WAVE.get(),
                at.x + Math.cos(a) * d,
                at.y + 0.15 + rnd.nextDouble() * (double)height * 0.6,
                at.z + Math.sin(a) * d,
                Math.cos(a) * sp,
                0.0,
                Math.sin(a) * sp,
                Mth.clamp(height * 0.75F, 0.6F, 2.6F) * (0.75F + rnd.nextFloat() * 0.5F),
                rgb
            );
        }
    }

    /** A few huge, faint puffs that hang over the ruin for half a minute. */
    public static void haze(ClientLevel level, Vec3 at, float r, int count, float[] rgb) {
        RandomSource rnd = level.random;
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(rnd.nextDouble()) * (double)r;
            dust(
                ModParticles.HAZE.get(),
                at.x + Math.cos(a) * d,
                at.y + 1.5 + rnd.nextDouble() * (double)r * 0.4,
                at.z + Math.sin(a) * d,
                (rnd.nextDouble() - 0.5) * 0.02,
                0.004,
                (rnd.nextDouble() - 0.5) * 0.02,
                Mth.clamp(r / 9.0F, 0.8F, 3.0F),
                rgb
            );
        }
    }

    /** A flung block hits the ground: a few chips jump off it and a small puff of its dust rises. */
    public static void debrisImpact(ClientLevel level, Vec3 at, BlockState s, double kick, Vec3 v) {
        RandomSource rnd = level.random;
        float lod = lod(at);
        if (lod < 0.2F) {
            return;
        }

        int n = room(scaled(Mth.clamp((int)(kick * 5.0), 2, 9), lod * lod));
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double out = (0.03 + rnd.nextDouble() * 0.1) * kick;
            rubble(
                s,
                at.x + (rnd.nextDouble() - 0.5) * 0.8,
                at.y + 0.05,
                at.z + (rnd.nextDouble() - 0.5) * 0.8,
                Math.cos(a) * out + v.x * 0.25,
                (0.06 + rnd.nextDouble() * 0.16) * Math.min(1.5, kick),
                Math.sin(a) * out + v.z * 0.25
            );
        }

        if (!dustless(s) && kick > 0.35) {
            float[] rgb = dustColour(s);
            int puffs = kick > 0.9 ? 2 : 1;
            for (int i = 0; i < puffs; i++) {
                DustParticle d = dust(
                    ModParticles.DUST_CLOUD.get(),
                    at.x + (rnd.nextDouble() - 0.5) * 0.6,
                    at.y + 0.1,
                    at.z + (rnd.nextDouble() - 0.5) * 0.6,
                    (rnd.nextDouble() - 0.5) * 0.06 + v.x * 0.1,
                    0.01 + rnd.nextDouble() * 0.02,
                    (rnd.nextDouble() - 0.5) * 0.06 + v.z * 0.1,
                    (float)Mth.clamp(kick * 0.45, 0.4, 0.9),
                    rgb
                );
                if (d != null) {
                    d.life(0.6F);
                }
            }
        }
    }

    /**
     * A piece of building on its way down sheds bits from its broken faces: a chip falls away with the piece's own
     * motion and a little dust streams off behind it ({@code dust} 0..1: how much).
     */
    public static void crumbleOff(ClientLevel level, Vec3 at, BlockState s, Vec3 v, float dust) {
        RandomSource rnd = level.random;
        float lod = lod(at);
        if (rnd.nextFloat() > lod) {
            return;
        }

        if (room(1) > 0) {
            rubble(
                s,
                at.x + (rnd.nextDouble() - 0.5) * 0.9,
                at.y - 0.5,
                at.z + (rnd.nextDouble() - 0.5) * 0.9,
                v.x * 0.9 + (rnd.nextDouble() - 0.5) * 0.05,
                Math.min(v.y, 0.0) * 0.9 - rnd.nextDouble() * 0.03,
                v.z * 0.9 + (rnd.nextDouble() - 0.5) * 0.05
            );
        }

        if (dust > 0.0F && !dustless(s) && rnd.nextFloat() < dust) {
            DustParticle d = dust(
                ModParticles.DUST_CLOUD.get(),
                at.x + (rnd.nextDouble() - 0.5),
                at.y - 0.4,
                at.z + (rnd.nextDouble() - 0.5),
                v.x * 0.35,
                v.y * 0.2,
                v.z * 0.35,
                0.5F + rnd.nextFloat() * 0.4F,
                dustColour(s)
            );
            if (d != null) {
                d.life(0.5F);
            }
        }
    }

    /** A wide, rolling dust surge for explosions and giants smashing into the ground. */
    /** While set (a region's ordinal + 1), dust is only drawn on that side of the Walls: the blast stopped there. */
    static int sideFilter;

    public static void dustSurge(ClientLevel level, Vec3 at, float R, float h) {
        RandomSource rnd = level.random;
        double ground = groundY(level, at.add(0.0, 1.0, 0.0), 10);
        Vec3 base = new Vec3(at.x, ground, at.z);
        float[] rgb = dustColour(null);
        surge(level, base, 3.0F, R * 0.85F, Math.min(5.0F, 1.2F + h * 0.4F), rgb, Mth.clamp((int)(R * 1.4F), 24, 96));
        int n = Mth.clamp((int)(R * 0.6F), 10, 40);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(rnd.nextDouble()) * (double)R * 0.12;
            double sp = (double)R / 300.0 * (0.5 + rnd.nextDouble());
            dust(
                ModParticles.DUST_CLOUD.get(),
                at.x + Math.cos(a) * d,
                ground + 0.5 + rnd.nextDouble() * (double)h,
                at.z + Math.sin(a) * d,
                Math.cos(a) * sp,
                0.02 + rnd.nextDouble() * 0.06,
                Math.sin(a) * sp,
                Mth.clamp(R / 18.0F, 1.0F, 3.2F) * (0.7F + rnd.nextFloat() * 0.6F),
                rgb
            );
        }

        haze(level, base, Math.min(40.0F, R * 0.6F), Mth.clamp((int)(R / 12.0F), 2, 8), rgb);
    }

    /** Glass blowing out: thin glittering shards on fast flat arcs, no dust. */
    public static void glass(ClientLevel level, Vec3 at, float R) {
        RandomSource rnd = level.random;
        BlockState g = Blocks.GLASS.defaultBlockState();
        float lod = lod(at);
        int n = room(scaled(Mth.clamp((int)(8.0F + R * 2.5F), 8, 60), lod * lod));
        for (int i = 0; i < n; i++) {
            Vec3 d = randomDir(rnd);
            double sp = 0.15 + rnd.nextDouble() * 0.4;
            rubble(
                g,
                at.x + d.x * (double)R * 0.4,
                at.y + d.y * (double)R * 0.4,
                at.z + d.z * (double)R * 0.4,
                d.x * sp,
                Math.abs(d.y) * sp * 0.5 + 0.05,
                d.z * sp
            );
        }
    }
}
