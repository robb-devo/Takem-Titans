package com.takem.titans.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.ParticleEngine.SpriteParticleRegistration;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Building dust. Unlike steam it never flips through frames: every puff keeps one billow sprite, swells quickly and
 * then ever more slowly, drags to a halt in the air, drifts with the wind and only then thins out.
 * <ul>
 * <li>{@link Style#CLOUD} the plume thrown up where something breaks or lands;</li>
 * <li>{@link Style#WAVE} the surge that rolls out along the street from a collapse and stops against walls;</li>
 * <li>{@link Style#HAZE} the pale pall that hangs over a ruin for half a minute afterwards.</li>
 * </ul>
 * Puffs right in front of the camera fade out, so flying through a dust cloud on ODM gear dims the view instead of
 * plastering a flat grey sprite over it.
 */
public class DustParticle extends TextureSheetParticle {
    /** Dry mortar and plaster: the colour of dust when nobody says what it came from. */
    public static final float DUST_R = 0.74F;
    public static final float DUST_G = 0.7F;
    public static final float DUST_B = 0.64F;
    /** Everything drifts the same way, so plumes lean a little instead of sitting in perfect domes. */
    private static final double WIND_X = 0.0007;
    private static final double WIND_Z = 0.0004;
    private final DustParticle.Style style;
    private float size0;
    private float size1;
    private float sizeO;
    private final float tau;
    private final float baseAlpha;
    private final float drag;
    private final float buoyancy;
    private float rollSpeed;

    protected DustParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, DustParticle.Style style) {
        super(level, x, y, z);
        this.style = style;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.pickSprite(sprites);
        this.roll = this.random.nextFloat() * 6.2831855F;
        this.oRoll = this.roll;
        this.rollSpeed = (this.random.nextFloat() - 0.5F) * 0.02F;
        this.hasPhysics = false;
        float shade = 0.93F + this.random.nextFloat() * 0.1F;
        this.setColor(DUST_R * shade, DUST_G * shade, DUST_B * shade);
        switch (style) {
            case WAVE:
                this.lifetime = 170 + this.random.nextInt(150);
                this.size0 = 0.9F + this.random.nextFloat() * 0.6F;
                this.size1 = this.size0 * (2.4F + this.random.nextFloat() * 1.0F);
                this.tau = 30.0F + this.random.nextFloat() * 25.0F;
                this.baseAlpha = 0.5F;
                this.drag = 0.962F;
                this.buoyancy = 0.0005F + this.random.nextFloat() * 0.0008F;
                this.hasPhysics = true;
                this.setSize(0.6F, 0.6F);
                break;
            case HAZE:
                this.lifetime = 520 + this.random.nextInt(420);
                this.size0 = 3.5F + this.random.nextFloat() * 2.5F;
                this.size1 = this.size0 * (1.7F + this.random.nextFloat() * 0.6F);
                this.tau = 140.0F;
                this.baseAlpha = 0.1F + this.random.nextFloat() * 0.06F;
                this.drag = 0.985F;
                this.buoyancy = 0.00025F;
                this.rollSpeed *= 0.3F;
                break;
            default:
                this.lifetime = 90 + this.random.nextInt(100);
                this.size0 = 0.6F + this.random.nextFloat() * 0.6F;
                this.size1 = this.size0 * (2.0F + this.random.nextFloat() * 1.0F);
                this.tau = 16.0F + this.random.nextFloat() * 16.0F;
                this.baseAlpha = 0.56F;
                this.drag = 0.94F;
                this.buoyancy = 0.0005F + this.random.nextFloat() * 0.0012F;
        }

        this.quadSize = this.size0;
        this.sizeO = this.size0;
        this.alpha = 0.0F;
    }

    @Override
    public Particle scale(float s) {
        this.size0 *= s;
        this.size1 *= s;
        this.quadSize = this.size0;
        this.sizeO = this.size0;
        return this;
    }

    /** Stretches or shortens how long the puff hangs around. */
    public DustParticle life(float f) {
        this.lifetime = Math.max(10, (int)((float)this.lifetime * f));
        return this;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        this.sizeO = this.quadSize;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        float t = (float)this.age;
        this.xd += WIND_X;
        this.zd += WIND_Z;
        // dust is heavier than air, but the churn of a collapse keeps it aloft and lifts it slowly once it's spent
        this.yd += (double)(this.buoyancy * Math.min(1.0F, t / 30.0F));
        this.move(this.xd, this.yd, this.zd);
        this.xd *= (double)this.drag;
        this.zd *= (double)this.drag;
        // the surge hugs the street: what lifts off it is pressed back down by the dust still pushing out on top
        this.yd *= this.style == DustParticle.Style.WAVE ? 0.86 : (double)this.drag;
        this.quadSize = this.size0 + (this.size1 - this.size0) * (1.0F - (float)Math.exp((double)(-t / this.tau)));
        float life = t / (float)this.lifetime;
        float in = Math.min(1.0F, t / (this.style == DustParticle.Style.HAZE ? 40.0F : 5.0F));
        float out = life < 0.45F ? 1.0F : 1.0F - (life - 0.45F) / 0.55F;
        this.alpha = this.baseAlpha * in * out * out;
        this.roll = this.roll + this.rollSpeed;
        this.rollSpeed *= 0.985F;
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (this.style != DustParticle.Style.WAVE) {
            super.move(dx, dy, dz);
            return;
        }

        // vanilla particle physics freeze a particle for good once it touches the ground; the surge has to keep rolling
        Vec3 want = new Vec3(dx, dy, dz);
        Vec3 got = Entity.collideBoundingBox(null, want, this.getBoundingBox(), this.level, List.of());
        this.setBoundingBox(this.getBoundingBox().move(got));
        this.setLocationFromBoundingbox();
        if (got.x != dx) {
            this.xd = 0.0;
        }

        if (got.z != dz) {
            this.zd = 0.0;
        }

        if (got.y != dy) {
            this.yd = 0.0;
        }
    }

    @Override
    public float getQuadSize(float pt) {
        return Mth.lerp(pt, this.sizeO, this.quadSize);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float pt) {
        float keep = this.alpha;
        Vec3 cam = camera.getPosition();
        double dx = Mth.lerp((double)pt, this.xo, this.x) - cam.x;
        double dy = Mth.lerp((double)pt, this.yo, this.y) - cam.y;
        double dz = Mth.lerp((double)pt, this.zo, this.z) - cam.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float size = this.getQuadSize(pt);
        // a puff the camera is inside of would be one flat grey sheet: thin it out instead
        float near = (float)Mth.clamp((d - (double)size * 0.35) / (double)(size * 1.4F + 1.0F), 0.0, 1.0);
        this.alpha = keep * near;
        if (this.alpha > 0.004F) {
            super.render(buffer, camera, pt);
        }

        this.alpha = keep;
    }

    @Override
    protected int getLightColor(float pt) {
        // a big puff sits half inside walls and ground: take the light of the open air around it, not the block at its foot
        BlockPos at = BlockPos.containing(this.x, this.y + (double)this.quadSize * 0.4, this.z);
        if (!this.level.hasChunkAt(at)) {
            return 0;
        } else {
            int a = LevelRenderer.getLightColor(this.level, at);
            int b = LevelRenderer.getLightColor(this.level, at.above());
            return LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(b)), Math.max(LightTexture.sky(a), LightTexture.sky(b)));
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return TakemParticle.SOFT;
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, DustParticle.Style style) {
        return (type, level, x, y, z, xd, yd, zd) -> new DustParticle(level, x, y, z, xd, yd, zd, sprites, style);
    }

    public static SpriteParticleRegistration<SimpleParticleType> factory(DustParticle.Style style) {
        return sprites -> provider(sprites, style);
    }

    public static enum Style {
        CLOUD,
        WAVE,
        HAZE;
    }
}
