package com.takem.titans.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.common.Tags;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A fragment of a broken block, drawn as a small tumbling solid with that block's own texture: chips and chunks of
 * stone and brick, long splinters of timber, thin flat shards of glass and roof tile. It flies on a real ballistic arc,
 * bounces off the street a little depending on what it's made of, skids, and finally comes to lie flat on its widest
 * face before it crumbles away. The vanilla block particles are flat squares that just sink into the floor.
 */
public class RubbleParticle extends Particle {
    private static final double GRAVITY = 0.04;
    /** Fragments that ticked during the current / the last client tick. */
    private static int seen;
    private static int live;
    private final TextureAtlasSprite sprite;
    private final float uo;
    private final float vo;
    private final float hx;
    private final float hy;
    private final float hz;
    private final float rest;
    private final float bounce;
    private final Quaternionf rot = new Quaternionf();
    private final Quaternionf rotO = new Quaternionf();
    private final Vector3f axis = new Vector3f();
    private float spin;
    private int still;
    private Quaternionf lie;

    protected RubbleParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, BlockState state) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.sprite = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
        this.hasPhysics = true;
        this.friction = 1.0F;
        BlockPos pos = BlockPos.containing(x, y, z);
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        if (IClientBlockExtensions.of(state).areBreakingParticlesTinted(state, level, pos)) {
            int c = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
            this.rCol = (float)(c >> 16 & 0xFF) / 255.0F;
            this.gCol = (float)(c >> 8 & 0xFF) / 255.0F;
            this.bCol = (float)(c & 0xFF) / 255.0F;
        }

        // most of what comes off a wall is grit and fist-sized chips; now and then a proper lump
        float r = this.random.nextFloat();
        float base = r < 0.72F ? 0.03F + this.random.nextFloat() * 0.055F : (r < 0.95F ? 0.085F + this.random.nextFloat() * 0.07F : 0.16F + this.random.nextFloat() * 0.1F);
        SoundType sound = state.getSoundType();
        boolean glass = state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES) || sound == SoundType.GLASS;
        boolean wood = state.is(BlockTags.LOGS)
            || state.is(BlockTags.PLANKS)
            || sound == SoundType.WOOD
            || sound == SoundType.CHERRY_WOOD
            || sound == SoundType.BAMBOO_WOOD
            || sound == SoundType.NETHER_WOOD;
        boolean tile = state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock;
        float a;
        float b;
        float c;
        if (glass) {
            a = base * (1.0F + this.random.nextFloat() * 0.8F);
            b = 0.006F + base * 0.05F;
            c = base * (0.7F + this.random.nextFloat() * 0.7F);
            this.bounce = 0.12F;
        } else if (wood) {
            // timber splits along the grain into long splinters and laths
            a = base * (2.4F + this.random.nextFloat() * 3.2F);
            b = base * (0.25F + this.random.nextFloat() * 0.3F);
            c = base * (0.35F + this.random.nextFloat() * 0.45F);
            this.bounce = 0.3F;
        } else if (tile) {
            a = base * (1.1F + this.random.nextFloat() * 0.6F);
            b = base * (0.25F + this.random.nextFloat() * 0.2F);
            c = base * (0.9F + this.random.nextFloat() * 0.6F);
            this.bounce = 0.18F;
        } else {
            a = base * (0.7F + this.random.nextFloat() * 0.6F);
            b = base * (0.6F + this.random.nextFloat() * 0.6F);
            c = base * (0.7F + this.random.nextFloat() * 0.6F);
            this.bounce = sound == SoundType.WOOL ? 0.05F : 0.2F;
        }

        this.hx = Math.min(a, 0.48F);
        this.hy = Math.min(b, 0.48F);
        this.hz = Math.min(c, 0.48F);
        this.rest = Math.min(this.hx, Math.min(this.hy, this.hz));
        float box = Math.max(0.04F, this.rest * 2.0F);
        this.setSize(box, box);
        // the fragment shows a random patch of the block's face, the size of the fragment itself
        float span = Math.min(1.0F, Math.max(this.hx, Math.max(this.hy, this.hz)) * 2.0F);
        this.uo = this.random.nextFloat() * (1.0F - Math.min(span, 0.98F));
        this.vo = this.random.nextFloat() * (1.0F - Math.min(span, 0.98F));
        this.rot.rotateXYZ(this.random.nextFloat() * 6.2831855F, this.random.nextFloat() * 6.2831855F, this.random.nextFloat() * 6.2831855F);
        this.rotO.set(this.rot);
        this.randomAxis();
        double speed = Math.sqrt(xd * xd + yd * yd + zd * zd);
        // small bits spin fast, big lumps turn over slowly
        this.spin = (float)((0.08 + this.random.nextDouble() * 0.3) * Math.min(1.6, 0.35 + speed * 2.0) * (0.07 / Math.max(0.05, (double)base)));
        this.lifetime = 60 + this.random.nextInt(70) + (int)(base * 500.0F);
        seen++;
    }

    private void randomAxis() {
        float u = this.random.nextFloat() * 2.0F - 1.0F;
        float t = this.random.nextFloat() * 6.2831855F;
        float s = Mth.sqrt(1.0F - u * u);
        this.axis.set(s * Mth.cos(t), u, s * Mth.sin(t));
    }

    public static int live() {
        return live;
    }

    /** Called once per client tick: how many fragments are in flight decides how many more an impact may throw. */
    public static void endTick() {
        live = seen;
        seen = 0;
    }

    @Override
    public void tick() {
        seen++;
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.rotO.set(this.rot);
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        if (this.still == 0) {
            this.yd -= GRAVITY;
        }

        Vec3 want = new Vec3(this.xd, this.yd, this.zd);
        Vec3 got = want;
        if (want.lengthSqr() > 1.0E-8) {
            got = Entity.collideBoundingBox(null, want, this.getBoundingBox(), this.level, List.of());
            this.setBoundingBox(this.getBoundingBox().move(got));
            this.setLocationFromBoundingbox();
        }

        boolean hitY = Math.abs(got.y - want.y) > 1.0E-6;
        this.onGround = hitY && want.y < 0.0;
        if (hitY) {
            if (want.y < -0.09) {
                // a real knock: some of the fall comes back as a hop, the skid loses about a third, the tumble changes
                this.yd = -want.y * (double)this.bounce * (0.6 + this.random.nextDouble() * 0.7);
                double keep = 0.55 + this.random.nextDouble() * 0.2;
                this.xd *= keep;
                this.zd *= keep;
                this.randomAxis();
                this.spin = Math.min(this.spin, 0.04F + (float)(-want.y) * 0.6F) * (0.5F + this.random.nextFloat() * 0.7F);
            } else {
                this.yd = 0.0;
                this.xd *= 0.62;
                this.zd *= 0.62;
                this.spin *= 0.55F;
            }
        }

        if (Math.abs(got.x - want.x) > 1.0E-6) {
            this.xd = -want.x * 0.25;
        }

        if (Math.abs(got.z - want.z) > 1.0E-6) {
            this.zd = -want.z * 0.25;
        }

        this.xd *= 0.985;
        this.yd *= 0.985;
        this.zd *= 0.985;
        if (this.onGround && this.xd * this.xd + this.zd * this.zd < 4.0E-4 && Math.abs(this.yd) < 0.02) {
            this.still++;
        } else if (!this.onGround) {
            this.still = 0;
            this.lie = null;
        }

        if (this.still > 0) {
            this.xd = 0.0;
            this.zd = 0.0;
            this.spin = 0.0F;
            if (this.lie == null) {
                this.lie = this.restingPose();
            }

            this.rot.slerp(this.lie, 0.4F);
        } else if (this.spin > 1.0E-4F) {
            this.rot.premul(new Quaternionf().rotationAxis(this.spin, this.axis.x, this.axis.y, this.axis.z)).normalize();
            this.spin *= 0.996F;
        }
    }

    /** Lying on its widest face: the thinnest local axis turned to point straight up or down. */
    private Quaternionf restingPose() {
        Vector3f thin = this.hy <= this.hx && this.hy <= this.hz ? new Vector3f(0.0F, 1.0F, 0.0F) : (this.hx <= this.hz ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 0.0F, 1.0F));
        this.rot.transform(thin);
        Vector3f up = new Vector3f(0.0F, thin.y >= 0.0F ? 1.0F : -1.0F, 0.0F);
        return new Quaternionf().rotationTo(thin, up).mul(this.rot);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float pt) {
        Vec3 cam = camera.getPosition();
        float px = (float)(Mth.lerp((double)pt, this.xo, this.x) - cam.x);
        float py = (float)(Mth.lerp((double)pt, this.yo, this.y) - cam.y) + this.rest;
        float pz = (float)(Mth.lerp((double)pt, this.zo, this.z) - cam.z);
        Quaternionf q = new Quaternionf(this.rotO).slerp(this.rot, pt);
        // the last half second it crumbles into the street
        float left = (float)this.lifetime - ((float)this.age + pt);
        float k = Mth.clamp(left / 12.0F, 0.0F, 1.0F);
        if (k <= 0.0F) {
            return;
        }

        int light = this.getLightColor(pt);
        float x = this.hx * k;
        float y = this.hy * k;
        float z = this.hz * k;
        this.face(buffer, q, px, py, pz, 1, 0, 0, 0, 1, 0, 0, 0, 1, x, y, z, light);
        this.face(buffer, q, px, py, pz, -1, 0, 0, 0, 0, 1, 0, 1, 0, x, z, y, light);
        this.face(buffer, q, px, py, pz, 0, 1, 0, 0, 0, 1, 1, 0, 0, y, z, x, light);
        this.face(buffer, q, px, py, pz, 0, -1, 0, 1, 0, 0, 0, 0, 1, y, x, z, light);
        this.face(buffer, q, px, py, pz, 0, 0, 1, 1, 0, 0, 0, 1, 0, z, x, y, light);
        this.face(buffer, q, px, py, pz, 0, 0, -1, 0, 1, 0, 1, 0, 0, z, y, x, light);
    }

    /**
     * One face of the box: outward normal n at distance {@code dn}, spanned by t1 (half length {@code d1}) and t2
     * (half length {@code d2}), with t1 x t2 = n so the corners wind counter-clockwise seen from outside.
     */
    private void face(
        VertexConsumer buffer,
        Quaternionf q,
        float px,
        float py,
        float pz,
        int nx,
        int ny,
        int nz,
        int ax,
        int ay,
        int az,
        int bx,
        int by,
        int bz,
        float dn,
        float d1,
        float d2,
        int light
    ) {
        Vector3f n = q.transform(new Vector3f((float)nx, (float)ny, (float)nz));
        // the same fixed sun as placed blocks: tops bright, undersides dark, east/west a shade darker than north/south
        float shade = Mth.clamp(0.76F + 0.24F * n.y - 0.1F * Math.abs(n.x), 0.45F, 1.0F);
        float r = this.rCol * shade;
        float g = this.gCol * shade;
        float b = this.bCol * shade;
        float u0 = this.sprite.getU(this.uo);
        float u1 = this.sprite.getU(Math.min(1.0F, this.uo + d1 * 2.0F));
        float v0 = this.sprite.getV(this.vo);
        float v1 = this.sprite.getV(Math.min(1.0F, this.vo + d2 * 2.0F));
        this.corner(buffer, q, px, py, pz, nx * dn - ax * d1 - bx * d2, ny * dn - ay * d1 - by * d2, nz * dn - az * d1 - bz * d2, u0, v1, r, g, b, light);
        this.corner(buffer, q, px, py, pz, nx * dn + ax * d1 - bx * d2, ny * dn + ay * d1 - by * d2, nz * dn + az * d1 - bz * d2, u1, v1, r, g, b, light);
        this.corner(buffer, q, px, py, pz, nx * dn + ax * d1 + bx * d2, ny * dn + ay * d1 + by * d2, nz * dn + az * d1 + bz * d2, u1, v0, r, g, b, light);
        this.corner(buffer, q, px, py, pz, nx * dn - ax * d1 + bx * d2, ny * dn - ay * d1 + by * d2, nz * dn - az * d1 + bz * d2, u0, v0, r, g, b, light);
    }

    private void corner(
        VertexConsumer buffer, Quaternionf q, float px, float py, float pz, float lx, float ly, float lz, float u, float v, float r, float g, float b, int light
    ) {
        Vector3f p = q.transform(new Vector3f(lx, ly, lz));
        buffer.addVertex(px + p.x, py + p.y, pz + p.z).setUv(u, v).setColor(r, g, b, this.alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    public static final class Provider implements ParticleProvider<BlockParticleOption> {
        @Override
        public Particle createParticle(BlockParticleOption o, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            BlockState s = o.getState();
            return !s.isAir() && s.getRenderShape() != RenderShape.INVISIBLE ? new RubbleParticle(level, x, y, z, xd, yd, zd, s) : null;
        }
    }
}
