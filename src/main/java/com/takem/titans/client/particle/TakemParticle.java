package com.takem.titans.client.particle;

import com.takem.titans.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.ParticleEngine.SpriteParticleRegistration;
import net.minecraft.client.particle.SingleQuadParticle.FacingCameraMode;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

public class TakemParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final TakemParticle.Style style;
    private final float baseSize;
    private final float baseAlpha;
    private final float growth;
    private boolean splat;
    private final Quaternionf splatRot = new Quaternionf();
    private float glow;

    protected TakemParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, TakemParticle.Style style) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.style = style;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.roll = this.random.nextFloat() * 6.2831855F;
        this.oRoll = this.roll;
        float alpha = 0.9F;
        float grow = 1.0F;
        float size;
        switch (style) {
            case GAS: {
                this.lifetime = 12 + this.random.nextInt(10);
                size = 0.22F + this.random.nextFloat() * 0.12F;
                grow = 1.07F;
                this.friction = 0.86F;
                this.gravity = -0.015F;
                float c = 0.92F + this.random.nextFloat() * 0.08F;
                this.setColor(c, c, c);
                alpha = 0.75F;
                break;
            }
            case STEAM: {
                this.lifetime = 30 + this.random.nextInt(25);
                size = 0.45F + this.random.nextFloat() * 0.35F;
                grow = 1.03F;
                this.friction = 0.92F;
                this.gravity = -0.05F;
                float c = 0.88F + this.random.nextFloat() * 0.1F;
                this.setColor(c, c, c);
                alpha = 0.6F;
                break;
            }
            case BIG_STEAM: {
                this.lifetime = 60 + this.random.nextInt(50);
                size = 2.0F + this.random.nextFloat() * 1.6F;
                grow = 1.012F;
                this.friction = 0.94F;
                this.gravity = -0.08F;
                float c = 0.9F + this.random.nextFloat() * 0.1F;
                this.setColor(c, c * 0.97F, c * 0.95F);
                alpha = 0.42F;
                break;
            }
            case BLOOD:
                this.lifetime = 20 + this.random.nextInt(14);
                size = 0.09F + this.random.nextFloat() * 0.08F;
                grow = 0.995F;
                this.friction = 0.96F;
                this.gravity = 0.9F;
                this.hasPhysics = true;
                this.setColor(0.72F + this.random.nextFloat() * 0.1F, 0.06F, 0.05F);
                alpha = 1.0F;
                break;
            case SPARK:
                this.lifetime = 5 + this.random.nextInt(5);
                size = 0.06F + this.random.nextFloat() * 0.05F;
                grow = 0.9F;
                this.friction = 0.8F;
                this.gravity = 0.4F;
                this.setColor(1.0F, 0.85F + this.random.nextFloat() * 0.15F, 0.45F);
                alpha = 1.0F;
                this.xd = this.xd + (this.random.nextDouble() - 0.5) * 0.25;
                this.yd = this.yd + this.random.nextDouble() * 0.18;
                this.zd = this.zd + (this.random.nextDouble() - 0.5) * 0.25;
                break;
            case FLARE_SMOKE:
                this.setColor((float)xd, (float)yd, (float)zd);
                this.xd = (this.random.nextDouble() - 0.5) * 0.02;
                this.yd = 0.015 + this.random.nextDouble() * 0.02;
                this.zd = (this.random.nextDouble() - 0.5) * 0.02;
                this.lifetime = 160 + this.random.nextInt(80);
                size = 0.7F + this.random.nextFloat() * 0.4F;
                grow = 1.012F;
                this.friction = 0.985F;
                this.gravity = -0.002F;
                alpha = 0.92F;
                break;
            case WIND_STREAK:
                this.lifetime = 4 + this.random.nextInt(3);
                size = 0.05F;
                grow = 1.0F;
                this.friction = 1.0F;
                this.gravity = 0.0F;
                this.setColor(1.0F, 1.0F, 1.0F);
                alpha = 0.5F;
                break;
            case DUST_CLOUD: {
                this.lifetime = 70 + this.random.nextInt(70);
                size = 1.3F + this.random.nextFloat() * 1.7F;
                grow = 1.006F;
                this.friction = 0.955F;
                this.gravity = -0.0025F;
                float c = 0.62F + this.random.nextFloat() * 0.14F;
                this.setColor(c, c * 0.95F, c * 0.88F);
                alpha = 0.45F;
                break;
            }
            case FIREBALL:
                this.lifetime = 22 + this.random.nextInt(26);
                size = 1.2F + this.random.nextFloat() * 2.2F;
                grow = 1.035F;
                this.friction = 0.9F;
                this.gravity = -0.035F;
                this.setColor(1.0F, 0.93F, 0.7F);
                alpha = 0.95F;
                break;
            case EMBER:
                this.lifetime = 30 + this.random.nextInt(50);
                size = 0.05F + this.random.nextFloat() * 0.07F;
                grow = 0.995F;
                this.friction = 0.985F;
                this.gravity = 0.35F;
                this.hasPhysics = true;
                this.setColor(1.0F, 0.55F + this.random.nextFloat() * 0.35F, 0.15F);
                alpha = 1.0F;
                break;
            case MUSHROOM: {
                this.lifetime = 380 + this.random.nextInt(320);
                size = 5.0F + this.random.nextFloat() * 6.0F;
                grow = 1.0025F;
                this.friction = 0.975F;
                this.gravity = -0.004F;
                float c = 0.86F + this.random.nextFloat() * 0.12F;
                this.setColor(c, c * 0.95F, c * 0.9F);
                alpha = 0.7F;
                this.glow = 1.0F;
                break;
            }
            default:
                size = 0.2F;
        }

        this.quadSize = size;
        this.baseSize = size;
        this.baseAlpha = alpha;
        this.alpha = alpha;
        this.growth = grow;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
            if (!this.splat) {
                this.quadSize = this.quadSize * this.growth;
            }

            float life = (float)this.age / (float)this.lifetime;
            switch (this.style) {
                case BLOOD:
                    if (this.onGround) {
                        this.xd *= 0.5;
                        this.zd *= 0.5;
                    }

                    if (this.age == this.lifetime - 1 && this.random.nextInt(3) == 0) {
                        this.level.addParticle(ModParticles.STEAM.get(), this.x, this.y, this.z, 0.0, 0.03, 0.0);
                    }

                    this.alpha = this.baseAlpha * (life < 0.6F ? 1.0F : 1.0F - (life - 0.6F) / 0.4F);
                    break;
                case SPARK:
                case WIND_STREAK:
                default:
                    this.alpha = this.baseAlpha * (1.0F - life);
                    break;
                case FLARE_SMOKE:
                    this.alpha = this.baseAlpha * (life < 0.7F ? 1.0F : 1.0F - (life - 0.7F) / 0.3F);
                    break;
                case DUST_CLOUD:
                    this.alpha = this.baseAlpha * Math.min(1.0F, (float)this.age / 8.0F) * (life < 0.55F ? 1.0F : 1.0F - (life - 0.55F) / 0.45F);
                    break;
                case FIREBALL:
                    if (life < 0.35F) {
                        this.setColor(1.0F, Mth.lerp(life / 0.35F, 0.93F, 0.55F), Mth.lerp(life / 0.35F, 0.7F, 0.12F));
                    } else {
                        float k = (life - 0.35F) / 0.65F;
                        this.setColor(Mth.lerp(k, 1.0F, 0.22F), Mth.lerp(k, 0.55F, 0.2F), Mth.lerp(k, 0.12F, 0.19F));
                    }

                    this.alpha = this.baseAlpha * (1.0F - life * life);
                    break;
                case EMBER:
                    if (this.onGround) {
                        this.xd *= 0.6;
                        this.zd *= 0.6;
                    }

                    this.alpha = (this.random.nextInt(4) == 0 ? 0.5F : 1.0F) * (1.0F - life);
                    break;
                case MUSHROOM:
                    this.glow = Math.max(0.0F, 1.0F - (float)this.age / 90.0F);
                    this.alpha = this.baseAlpha * Math.min(1.0F, (float)this.age / 12.0F) * (life < 0.7F ? 1.0F : 1.0F - (life - 0.7F) / 0.3F);
            }

            this.oRoll = this.roll;
            if (this.style == TakemParticle.Style.STEAM
                || this.style == TakemParticle.Style.BIG_STEAM
                || this.style == TakemParticle.Style.FLARE_SMOKE
                || this.style == TakemParticle.Style.DUST_CLOUD
                || this.style == TakemParticle.Style.MUSHROOM) {
                this.roll += 0.006F;
            }
        }
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (this.style == TakemParticle.Style.BLOOD && !this.splat) {
            double x0 = this.x;
            double y0 = this.y;
            double z0 = this.z;
            super.move(dx, dy, dz);
            if (Math.abs(this.x - x0 - dx) > 1.0E-4) {
                this.stick(Axis.X, dx);
            } else if (Math.abs(this.z - z0 - dz) > 1.0E-4) {
                this.stick(Axis.Z, dz);
            } else if (Math.abs(this.y - y0 - dy) > 1.0E-4) {
                this.stick(Axis.Y, dy);
            }
        } else {
            super.move(dx, dy, dz);
        }
    }

    private void stick(Axis axis, double towards) {
        this.splat = true;
        this.lifetime = this.age + 500 + this.random.nextInt(400);
        this.quadSize = this.quadSize * (1.8F + this.random.nextFloat() * 1.6F);
        this.xd = this.yd = this.zd = 0.0;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        float sign = towards > 0.0 ? 1.0F : -1.0F;
        switch (axis) {
            case Y:
                this.splatRot.identity().rotateX(-1.5707964F);
                if (sign > 0.0F) {
                    this.setPos(this.x, this.y + 0.19, this.z);
                } else {
                    this.setPos(this.x, this.y + 0.012, this.z);
                }
                break;
            case X:
                this.splatRot.identity().rotateY(1.5707964F);
                this.setPos(this.x + (double)sign * 0.088, this.y + 0.1, this.z);
                break;
            default:
                this.splatRot.identity();
                this.setPos(this.x, this.y + 0.1, this.z + (double)sign * 0.088);
        }

        this.splatRot.rotateZ(this.random.nextFloat() * 6.2831855F);
        this.quadSize = this.baseSize * (2.2F + this.random.nextFloat() * 1.6F);
        this.lifetime = this.age + 140 + this.random.nextInt(120);
        this.setColor(this.rCol * 0.8F, this.gCol, this.bCol);
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return this.splat ? (q, camera, pt) -> q.set(this.splatRot) : super.getFacingCameraMode();
    }

    @Override
    protected int getLightColor(float partialTick) {
        if (this.style == TakemParticle.Style.SPARK || this.style == TakemParticle.Style.FLARE_SMOKE || this.style == TakemParticle.Style.EMBER) {
            return 15728880;
        } else if (this.style == TakemParticle.Style.FIREBALL) {
            return 15728880;
        } else if (this.style == TakemParticle.Style.MUSHROOM && this.glow > 0.0F) {
            int base = super.getLightColor(partialTick);
            int bl = Math.max(base & 65535, (int)(this.glow * 240.0F));
            return base & -65536 | bl;
        } else {
            return super.getLightColor(partialTick);
        }
    }

    /**
     * Smoke, steam and dust: blended like any translucent particle but never written to the depth buffer, so a faint
     * cloud in front of a slash wave or a titan no longer cuts a hole in it.
     */
    private static final ParticleRenderType SOFT = new ParticleRenderType() {
        @Override
        public com.mojang.blaze3d.vertex.BufferBuilder begin(com.mojang.blaze3d.vertex.Tesselator tesselator, net.minecraft.client.renderer.texture.TextureManager textures) {
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            return tesselator.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "TAKEMTITANS_SOFT";
        }
    };

    @Override
    public ParticleRenderType getRenderType() {
        return switch (this.style) {
            case DUST_CLOUD, STEAM, BIG_STEAM, FLARE_SMOKE, MUSHROOM -> SOFT;
            default -> ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        };
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, TakemParticle.Style style) {
        return (type, level, x, y, z, xd, yd, zd) -> new TakemParticle(level, x, y, z, xd, yd, zd, sprites, style);
    }

    public static SpriteParticleRegistration<SimpleParticleType> factory(TakemParticle.Style style) {
        return sprites -> provider(sprites, style);
    }

    public static enum Style {
        GAS,
        STEAM,
        BIG_STEAM,
        BLOOD,
        SPARK,
        FLARE_SMOKE,
        WIND_STREAK,
        DUST_CLOUD,
        FIREBALL,
        EMBER,
        MUSHROOM;
    }
}
