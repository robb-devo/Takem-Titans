package com.takem.titans.entity.titan;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class TitanAnimator {
    private static final float PI = 3.1415927F;

    private TitanAnimator() {
    }

    public static void pose(AbstractTitan t, float pt, TitanSkeleton.Pose p, TitanSkeleton.Frames f) {
        p.reset();
        p.headScale = t.headScale();
        float time = (float)t.tickCount + pt + (float)(t.getId() * 37);
        float phase = t.walkPhase(pt);
        float amt = t.walkAmount(pt);
        float run = t.runBlend(pt);
        if (t.isCrawler()) {
            crawl(t, p, time, phase, amt);
        } else {
            upright(t, p, time, phase, amt, run);
        }

        float headYaw = Mth.wrapDegrees(Mth.rotLerp(pt, t.yHeadRotO, t.yHeadRot) - Mth.rotLerp(pt, t.yBodyRotO, t.yBodyRot));
        float headPitch = Mth.lerp(pt, t.xRotO, t.getXRot());
        p.yRot[3] = p.yRot[3] + Mth.clamp(headYaw, -75.0F, 75.0F) * 0.017453292F;
        p.xRot[3] = p.xRot[3] + Mth.clamp(headPitch, -50.0F, 45.0F) * 0.017453292F;
        if (t.isDowned()) {
            downed(p, time);
        }

        f.compute(p);
        action(t, pt, p, f, time);
        if (t.dyingTicks > 0) {
            dying(t, pt, p);
        }

        f.compute(p);
    }

    private static void upright(AbstractTitan t, TitanSkeleton.Pose p, float time, float phase, float amt, float run) {
        float swing = Mth.lerp(run, 0.55F, 0.95F) * amt;
        float c = Mth.cos(phase);
        float s = Mth.sin(phase);
        p.xRot[11] = c * swing;
        p.xRot[13] = -c * swing;
        p.xRot[12] = (Mth.sin(phase - 0.7F) + 1.0F) * 0.38F * amt * (1.0F + run * 0.6F);
        p.xRot[14] = (Mth.sin(phase + 3.1415927F - 0.7F) + 1.0F) * 0.38F * amt * (1.0F + run * 0.6F);
        p.rootY = -Math.abs(c) * 1.4F * amt + 1.0F * amt;
        float armSwing = Mth.lerp(run, 0.35F, 1.25F) * amt;
        float idle = Mth.sin(time * 0.045F) * 0.05F;
        p.xRot[5] = -c * armSwing + idle;
        p.xRot[8] = c * armSwing - idle;
        float flail = run * (0.45F + 0.35F * Mth.sin(phase * 0.5F + 1.0F));
        p.zRot[5] = 0.12F + flail + Mth.sin(time * 0.031F) * 0.03F;
        p.zRot[8] = -0.12F - flail - Mth.sin(time * 0.027F) * 0.03F;
        p.xRot[6] = -0.22F - 0.25F * amt - run * 0.4F;
        p.xRot[9] = -0.22F - 0.25F * amt - run * 0.4F;
        p.xRot[2] = 0.07F + run * 0.45F + Mth.sin(time * 0.04F) * 0.02F;
        p.yRot[2] = s * 0.07F * amt;
        p.zRot[2] = s * 0.03F * amt;
        p.zRot[3] = Mth.sin(time * 0.013F) * 0.16F * t.headTilt();
        p.xRot[4] = 0.04F + Math.max(0.0F, Mth.sin(time * 0.05F)) * 0.06F;
    }

    private static void crawl(AbstractTitan t, TitanSkeleton.Pose p, float time, float phase, float amt) {
        float c = Mth.cos(phase);
        float s = Mth.sin(phase);
        p.rootY = 9.0F;
        float tilt = 0.28F;
        p.xRot[2] = 1.5707964F - tilt + Mth.sin(time * 0.05F) * 0.03F;
        p.yRot[2] = s * 0.1F * amt;
        p.xRot[3] = -(1.5707964F - tilt);
        p.xRot[11] = 0.55F + c * 0.35F * amt;
        p.xRot[13] = 0.55F - c * 0.35F * amt;
        p.xRot[12] = 1.05F + Math.max(0.0F, s) * 0.3F * amt;
        p.xRot[14] = 1.05F + Math.max(0.0F, -s) * 0.3F * amt;
        p.xRot[5] = -(1.5707964F - tilt) - c * 0.45F * amt - 0.25F;
        p.xRot[8] = -(1.5707964F - tilt) + c * 0.45F * amt - 0.25F;
        p.zRot[5] = 0.25F;
        p.zRot[8] = -0.25F;
        p.xRot[6] = 0.55F + Math.max(0.0F, -s) * 0.4F * amt;
        p.xRot[9] = 0.55F + Math.max(0.0F, s) * 0.4F * amt;
        p.xRot[7] = -0.6F;
        p.xRot[10] = -0.6F;
        p.xRot[4] = 0.15F + Math.max(0.0F, Mth.sin(time * 0.11F)) * 0.25F;
    }

    private static void downed(TitanSkeleton.Pose p, float time) {
        p.rootY = 9.0F;
        p.xRot[11] = -1.25F;
        p.xRot[13] = -1.25F;
        p.xRot[12] = 1.75F;
        p.xRot[14] = 1.75F;
        p.xRot[2] = 0.55F + Mth.sin(time * 0.08F) * 0.03F;
        p.xRot[5] = -0.75F;
        p.xRot[8] = -0.75F;
        p.zRot[5] = 0.25F;
        p.zRot[8] = -0.25F;
        p.xRot[6] = 0.0F;
        p.xRot[9] = 0.0F;
        p.xRot[3] = -0.35F;
        p.xRot[4] = 0.3F;
    }

    private static void dying(AbstractTitan t, float pt, TitanSkeleton.Pose p) {
        float d = Math.min(1.0F, ((float)t.dyingTicks + pt) / 36.0F);
        float fall = d * d * (3.0F - 2.0F * d);
        if (t.isCrawler()) {
            p.rootY = Mth.lerp(fall, p.rootY, 16.0F);
            p.xRot[4] = 0.4F;
        } else {
            p.xRot[0] = fall * 1.5107964F;
            p.xRot[5] = Mth.lerp(fall, p.xRot[5], -2.6F);
            p.xRot[8] = Mth.lerp(fall, p.xRot[8], -2.5F);
            p.zRot[5] = Mth.lerp(fall, p.zRot[5], 0.5F);
            p.zRot[8] = Mth.lerp(fall, p.zRot[8], -0.4F);
            p.xRot[3] = Mth.lerp(fall, p.xRot[3], -0.6F);
            p.yRot[3] = Mth.lerp(fall, p.yRot[3], 0.7F);
            p.xRot[4] = Mth.lerp(fall, p.xRot[4], 0.5F);
            p.xRot[11] = p.xRot[11] * (1.0F - fall);
            p.xRot[13] = p.xRot[13] * (1.0F - fall);
            p.xRot[12] = Mth.lerp(fall, p.xRot[12], 0.3F);
            p.xRot[14] = Mth.lerp(fall, p.xRot[14], 0.1F);
        }
    }

    private static void action(AbstractTitan t, float pt, TitanSkeleton.Pose p, TitanSkeleton.Frames f, float time) {
        TitanAction a = t.getAction();
        if (a != TitanAction.NONE) {
            float ticks = (float)t.actionTicks + pt;
            float k = Mth.clamp(ticks / (float)a.duration, 0.0F, 1.0F);
            float side = a.side();
            int shoulder = side < 0.0F ? 5 : 8;
            if (!t.isCrawler() || !a.isHold() && !a.isBite()) {
                switch (a) {
                    case GRAB_RIGHT:
                    case GRAB_LEFT: {
                        Vector3f target = targetInModel(t, pt);
                        float bend = k < 0.5F ? ease(Mth.clamp((k - 0.1F) / 0.4F, 0.0F, 1.0F)) : (k < 0.66F ? 1.0F : 1.0F - ease((k - 0.66F) / 0.34F));
                        float lean = Mth.sin(Mth.clamp((k - 0.3F) / 0.5F, 0.0F, 1.0F) * 3.1415927F);
                        stoop(p, grabStoop(target) * bend);
                        p.xRot[2] = p.xRot[2] + 0.28F * lean;
                        p.yRot[2] = p.yRot[2] + side * -0.25F * lean;
                        f.compute(p);
                        Vector3f cocked = shoulderPoint(f, shoulder, side * 16.0F, -14.0F, 12.0F);
                        Vector3f rest = shoulderPoint(f, shoulder, side * 4.0F, 30.0F, -4.0F);
                        Vector3f hand;
                        if (k < 0.38F) {
                            hand = lerp(rest, cocked, ease(k / 0.38F));
                        } else if (k < 0.62F) {
                            hand = lerp(cocked, target, ease((k - 0.38F) / 0.24F));
                        } else {
                            hand = lerp(target, rest, ease((k - 0.62F) / 0.38F));
                        }

                        TitanSkeleton.reach(p, f, shoulder, hand, side, 0.6F);
                        p.xRot[4] = 0.1F + 0.35F * lean;
                        break;
                    }
                    case HOLD_RIGHT:
                    case HOLD_LEFT: {
                        float lift = ease(Mth.clamp((k - 0.15F) / 0.75F, 0.0F, 1.0F));
                        p.xRot[3] = Mth.lerp(lift, p.xRot[3], 0.35F);
                        p.yRot[3] = Mth.lerp(lift, p.yRot[3], side * -0.25F);
                        p.xRot[4] = Mth.lerp(lift, 0.1F, 0.75F);
                        p.xRot[2] = p.xRot[2] + 0.12F * lift;
                        f.compute(p);
                        Vector3f low = shoulderPoint(f, shoulder, side * -4.0F, 18.0F, -20.0F);
                        Vector3f mouth = mouthFront(f, p, 9.0F);
                        Vector3f hand = lerp(low, mouth, lift);
                        hand.add(Mth.sin(time * 1.3F) * 0.6F, Mth.sin(time * 1.7F) * 0.4F, 0.0F);
                        TitanSkeleton.reach(p, f, shoulder, hand, side, 0.8F);
                        break;
                    }
                    case BITE_RIGHT:
                    case BITE_LEFT: {
                        p.xRot[3] = 0.35F;
                        p.yRot[3] = side * -0.25F;
                        p.xRot[2] = p.xRot[2] + 0.12F;
                        float open = k < 0.45F ? ease(k / 0.45F) : (k < 0.55F ? 1.0F - ease((k - 0.45F) / 0.1F) : 0.15F * Mth.abs(Mth.sin(ticks * 0.9F)));
                        p.xRot[4] = 0.1F + 0.85F * open;
                        f.compute(p);
                        Vector3f mouth = mouthFront(f, p, k < 0.5F ? 7.0F : 2.0F);
                        TitanSkeleton.reach(p, f, shoulder, mouth, side, 0.8F);
                        break;
                    }
                    case SWAT_RIGHT:
                    case SWAT_LEFT: {
                        Vector3f target = targetInModel(t, pt);
                        float height = Mth.clamp(target.y, -40.0F, 20.0F);
                        Vector3f from = shoulderPoint(f, shoulder, side * 30.0F, 0.0F, 6.0F);
                        from.y = height;
                        Vector3f to = new Vector3f(-side * 20.0F, height, -26.0F);
                        Vector3f rest = shoulderPoint(f, shoulder, side * 4.0F, 30.0F, -4.0F);
                        Vector3f hand;
                        if (k < 0.35F) {
                            hand = lerp(rest, from, ease(k / 0.35F));
                        } else if (k < 0.6F) {
                            hand = lerp(from, to, ease((k - 0.35F) / 0.25F));
                        } else {
                            hand = lerp(to, rest, ease((k - 0.6F) / 0.4F));
                        }

                        float twist = k < 0.35F ? ease(k / 0.35F) : (k < 0.6F ? 1.0F - 2.0F * ease((k - 0.35F) / 0.25F) : -1.0F + ease((k - 0.6F) / 0.4F));
                        p.yRot[2] = p.yRot[2] + side * 0.35F * twist;
                        f.compute(p);
                        TitanSkeleton.reach(p, f, shoulder, hand, side, 0.4F);
                        break;
                    }
                    case STOMP:
                        float up = k < 0.5F ? ease(k / 0.5F) : (k < 0.62F ? 1.0F - ease((k - 0.5F) / 0.12F) : 0.0F);
                        p.xRot[11] = -1.15F * up;
                        p.xRot[12] = 1.25F * up;
                        p.xRot[14] = 0.2F * up;
                        p.xRot[2] = p.xRot[2] + 0.12F * up;
                        p.zRot[5] = p.zRot[5] + 0.3F * up;
                        p.zRot[8] = p.zRot[8] - 0.4F * up;
                        break;
                    case LEAP:
                        if (k < 0.3F) {
                            float c = ease(k / 0.3F);
                            p.rootY += 9.0F * c;
                            p.xRot[11] = -1.0F * c;
                            p.xRot[13] = -1.0F * c;
                            p.xRot[12] = 1.7F * c;
                            p.xRot[14] = 1.7F * c;
                            p.xRot[2] = 0.6F * c;
                            p.xRot[5] = 0.8F * c;
                            p.xRot[8] = 0.8F * c;
                        } else {
                            float fly = ease(Mth.clamp((k - 0.3F) / 0.2F, 0.0F, 1.0F));
                            p.xRot[2] = 0.35F;
                            p.xRot[11] = 0.5F * fly;
                            p.xRot[13] = 0.2F * fly;
                            p.xRot[12] = 0.8F;
                            p.xRot[14] = 0.6F;
                            p.xRot[5] = -2.3F * fly;
                            p.xRot[8] = -2.2F * fly;
                            p.zRot[5] = 0.35F;
                            p.zRot[8] = -0.35F;
                            p.xRot[6] = 0.0F;
                            p.xRot[9] = 0.0F;
                            p.xRot[4] = 0.7F;
                        }
                        break;
                    case ROAR: {
                        float r = Mth.sin(Mth.clamp(k * 1.3F, 0.0F, 1.0F) * 3.1415927F);
                        p.xRot[3] = p.xRot[3] - 0.55F * r;
                        p.xRot[4] = 0.1F + 0.85F * r;
                        p.xRot[2] = p.xRot[2] - 0.15F * r;
                        p.zRot[5] = p.zRot[5] + 0.6F * r;
                        p.zRot[8] = p.zRot[8] - 0.6F * r;
                        p.xRot[6] = p.xRot[6] - 0.6F * r;
                        p.xRot[9] = p.xRot[9] - 0.6F * r;
                        break;
                    }
                    case STEAM: {
                        float r = Mth.sin(Mth.clamp(k * 1.15F, 0.0F, 1.0F) * 3.1415927F);
                        p.xRot[3] = p.xRot[3] - 0.4F * r;
                        p.xRot[4] = 0.1F + 0.5F * r;
                        p.zRot[5] = p.zRot[5] + 0.9F * r;
                        p.zRot[8] = p.zRot[8] - 0.9F * r;
                        p.xRot[2] = p.xRot[2] - 0.1F * r;
                        break;
                    }
                    case KICK:
                        float back = k < 0.4F ? ease(k / 0.4F) : 0.0F;
                        float fwd = k >= 0.4F && k < 0.55F
                            ? ease((k - 0.4F) / 0.15F)
                            : (k >= 0.55F ? 1.0F - ease(Mth.clamp((k - 0.7F) / 0.3F, 0.0F, 1.0F)) : 0.0F);
                        p.xRot[11] = 0.7F * back - 1.4F * fwd;
                        p.xRot[12] = 0.9F * back + 0.1F * fwd;
                        p.xRot[2] = p.xRot[2] + -0.15F * fwd + 0.1F * back;
                        p.zRot[5] = p.zRot[5] + 0.4F * fwd;
                        p.zRot[8] = p.zRot[8] - 0.4F * fwd;
                        break;
                    case BLINDED:
                        float cover = Math.min(1.0F, k * 5.0F) * Math.min(1.0F, (1.0F - k) * 5.0F);
                        p.xRot[2] = p.xRot[2] + 0.2F * cover;
                        p.zRot[3] = p.zRot[3] + Mth.sin(time * 0.4F) * 0.25F * cover;
                        p.xRot[4] = 0.2F + 0.4F * cover;
                        f.compute(p);
                        Vector3f eyes = f.point(3, TitanSkeleton.EYES, p.headScale, new Vector3f()).add(0.0F, 0.0F, -5.0F);
                        Vector3f restR = shoulderPoint(f, 5, -4.0F, 30.0F, -4.0F);
                        Vector3f restL = shoulderPoint(f, 8, 4.0F, 30.0F, -4.0F);
                        TitanSkeleton.reach(p, f, 5, lerp(restR, new Vector3f(eyes).add(-3.0F, 0.0F, 0.0F), cover), -1.0F, 0.9F);
                        TitanSkeleton.reach(p, f, 8, lerp(restL, new Vector3f(eyes).add(3.0F, 0.0F, 0.0F), cover), 1.0F, 0.9F);
                        break;
                    case FLINCH: {
                        float r = Mth.sin(k * 3.1415927F);
                        p.xRot[2] = p.xRot[2] - 0.12F * r;
                        p.xRot[3] = p.xRot[3] - 0.2F * r;
                        p.xRot[4] = p.xRot[4] + 0.3F * r;
                    }
                }
            } else {
                float open = a.isBite() ? (k < 0.45F ? ease(k / 0.45F) : (k < 0.55F ? 1.0F - ease((k - 0.45F) / 0.1F) : 0.1F)) : 0.35F;
                p.xRot[4] = 0.15F + 0.8F * open;
                p.yRot[3] = p.yRot[3] + Mth.sin(time * 0.9F) * 0.35F;
                p.zRot[3] = p.zRot[3] + Mth.sin(time * 0.7F) * 0.2F;
                p.xRot[3] = p.xRot[3] - 0.3F;
            }
        }
    }

    public static float grabStoop(Vector3f target) {
        return Mth.clamp((target.y + 4.0F) / 22.0F, 0.0F, 1.0F);
    }

    static void stoop(TitanSkeleton.Pose p, float s) {
        if (!(s <= 0.0F)) {
            p.xRot[2] = p.xRot[2] + 0.95F * s;
            p.xRot[3] = p.xRot[3] - 0.35F * s;
            p.xRot[11] = p.xRot[11] - 0.6F * s;
            p.xRot[13] = p.xRot[13] - 0.6F * s;
            p.xRot[12] = p.xRot[12] + 1.2F * s;
            p.xRot[14] = p.xRot[14] + 1.2F * s;
            p.rootY += 3.5F * s;
        }
    }

    public static Vector3f stoopedShoulder(int side, float s) {
        TitanSkeleton.Pose p = new TitanSkeleton.Pose();
        TitanSkeleton.Frames f = new TitanSkeleton.Frames();
        p.reset();
        stoop(p, s);
        p.xRot[2] = p.xRot[2] + 0.28F;
        p.yRot[2] = p.yRot[2] + (float)side * -0.25F;
        f.compute(p);
        return f.joint[side < 0 ? 5 : 8].transformPosition(new Vector3f());
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    private static Vector3f shoulderPoint(TitanSkeleton.Frames f, int shoulder, float dx, float dy, float dz) {
        Vector3f s = shoulderPos(f, shoulder);
        return s.add(dx, dy, dz);
    }

    private static Vector3f shoulderPos(TitanSkeleton.Frames f, int shoulder) {
        Vector3f s = new Vector3f(TitanSkeleton.PIVOT[shoulder][0], TitanSkeleton.PIVOT[shoulder][1], TitanSkeleton.PIVOT[shoulder][2]);
        return f.joint[TitanSkeleton.PARENT[shoulder]].transformPosition(s);
    }

    private static Vector3f mouthFront(TitanSkeleton.Frames f, TitanSkeleton.Pose p, float forward) {
        Vector3f m = new Vector3f(TitanSkeleton.MOUTH).add(0.0F, 0.0F, -forward);
        return f.point(3, m, p.headScale, new Vector3f());
    }

    private static Vector3f targetInModel(AbstractTitan t, float pt) {
        Entity target = t.actionTargetEntity();
        Vec3 world;
        if (target != null) {
            world = target.getPosition(pt).add(0.0, (double)target.getBbHeight() * 0.5, 0.0);
        } else {
            world = t.getPosition(pt).add(t.getLookAngle().scale((double)t.getTitanScale() * 1.5)).add(0.0, (double)t.getBbHeight() * 0.5, 0.0);
        }

        return toModel(world, t, pt);
    }

    public static Vector3f toModel(Vec3 world, AbstractTitan t, float pt) {
        Vec3 pos = t.getPosition(pt);
        float yaw = Mth.rotLerp(pt, t.yBodyRotO, t.yBodyRot);
        float s = t.getTitanScale();
        double wx = world.x - pos.x;
        double wy = world.y - pos.y;
        double wz = world.z - pos.z;
        float a = (180.0F - yaw) * 0.017453292F;
        double cos = (double)Mth.cos(a);
        double sin = (double)Mth.sin(a);
        double mx = wx * cos - wz * sin;
        double mz = wx * sin + wz * cos;
        return new Vector3f((float)(-mx / (double)s * 16.0), (float)((-wy / (double)s + 1.501) * 16.0), (float)(mz / (double)s * 16.0));
    }
}
