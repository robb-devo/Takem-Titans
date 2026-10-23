package com.takem.titans.entity.titan;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class TitanSkeleton {
    public static final int ROOT = 0;
    public static final int HIPS = 1;
    public static final int TORSO = 2;
    public static final int HEAD = 3;
    public static final int JAW = 4;
    public static final int R_ARM = 5;
    public static final int R_FOREARM = 6;
    public static final int R_HAND = 7;
    public static final int L_ARM = 8;
    public static final int L_FOREARM = 9;
    public static final int L_HAND = 10;
    public static final int R_LEG = 11;
    public static final int R_SHIN = 12;
    public static final int L_LEG = 13;
    public static final int L_SHIN = 14;
    public static final int COUNT = 15;
    public static final int[] PARENT = new int[]{-1, 0, 1, 2, 3, 2, 5, 6, 2, 8, 9, 1, 11, 1, 13};
    public static final float[][] PIVOT = new float[][]{
        {0.0F, 24.0F, 0.0F},
        {0.0F, -20.0F, 0.0F},
        {0.0F, 0.0F, 0.0F},
        {0.0F, -26.0F, 0.0F},
        {0.0F, -6.0F, 3.0F},
        {-13.0F, -23.0F, 0.0F},
        {0.0F, 14.0F, 0.0F},
        {0.0F, 15.0F, 0.0F},
        {13.0F, -23.0F, 0.0F},
        {0.0F, 14.0F, 0.0F},
        {0.0F, 15.0F, 0.0F},
        {-5.0F, 0.0F, 0.0F},
        {0.0F, 10.0F, 0.0F},
        {5.0F, 0.0F, 0.0F},
        {0.0F, 10.0F, 0.0F}
    };
    public static final float BASE_HEIGHT = 4.0F;
    public static final float UPPER_ARM = 14.0F;
    public static final float LOWER_ARM = 24.0F;
    public static final float HAND_CENTER = 4.5F;
    public static final Vector3f NAPE = new Vector3f(0.0F, -3.5F, 7.0F);
    public static final Vector3f HEAD_CENTER = new Vector3f(0.0F, -11.0F, 0.0F);
    public static final Vector3f MOUTH = new Vector3f(0.0F, -3.5F, -9.5F);
    public static final Vector3f EYES = new Vector3f(0.0F, -11.0F, -9.0F);
    public static final Vector3f TORSO_CENTER = new Vector3f(0.0F, -13.0F, 0.0F);
    public static final Vector3f HAND = new Vector3f(0.0F, 4.5F, 0.0F);
    public static final Vector3f FOREARM_CENTER = new Vector3f(0.0F, 7.0F, 0.0F);
    public static final Vector3f SHIN_CENTER = new Vector3f(0.0F, 5.0F, 0.0F);
    private static final ThreadLocal<float[]> TMP = ThreadLocal.withInitial(() -> new float[2]);

    private TitanSkeleton() {
    }

    public static Vec3 toWorld(Vector3f model, double x, double y, double z, float bodyYaw, float scale) {
        double mx = -((double)model.x / 16.0) * (double)scale;
        double my = -((double)model.y / 16.0 - 1.501) * (double)scale;
        double mz = (double)model.z / 16.0 * (double)scale;
        float a = (180.0F - bodyYaw) * 0.017453292F;
        double cos = (double)Mth.cos(a);
        double sin = (double)Mth.sin(a);
        double wx = mx * cos + mz * sin;
        double wz = -mx * sin + mz * cos;
        return new Vec3(x + wx, y + my, z + wz);
    }

    public static Vector3f dirToModel(Vec3 dir, float bodyYaw) {
        float a = (180.0F - bodyYaw) * 0.017453292F;
        double cos = (double)Mth.cos(a);
        double sin = (double)Mth.sin(a);
        double mx = dir.x * cos - dir.z * sin;
        double mz = dir.x * sin + dir.z * cos;
        return new Vector3f((float)(-mx), (float)(-dir.y), (float)mz);
    }

    public static void aim(Vector3f dir, float[] outXZ) {
        float len = dir.length();
        if (len < 1.0E-5F) {
            outXZ[0] = 0.0F;
            outXZ[1] = 0.0F;
        } else {
            float dx = dir.x / len;
            float dy = dir.y / len;
            float dz = dir.z / len;
            float x = (float)Math.asin((double)Mth.clamp(dz, -1.0F, 1.0F));
            float z = (float)Mth.atan2((double)(-dx), (double)dy);
            outXZ[0] = x;
            outXZ[1] = z;
        }
    }

    public static void reach(TitanSkeleton.Pose pose, TitanSkeleton.Frames frames, int shoulder, Vector3f target, float side, float elbowOut) {
        Matrix4f parent = new Matrix4f(frames.joint[PARENT[shoulder]]);
        parent.translate(PIVOT[shoulder][0], PIVOT[shoulder][1], PIVOT[shoulder][2]);
        Matrix4f inv = new Matrix4f(parent).invert();
        Vector3f local = inv.transformPosition(new Vector3f(target));
        float a = 14.0F;
        float b = 19.5F;
        float d = Mth.clamp(local.length(), Math.abs(a - b) + 0.05F, a + b - 0.05F);
        Vector3f dn = new Vector3f(local).normalize();
        float cosA = (a * a + d * d - b * b) / (2.0F * a * d);
        float alpha = (float)Math.acos((double)Mth.clamp(cosA, -1.0F, 1.0F));
        Vector3f pole = new Vector3f(side * elbowOut, 0.4F, 1.0F);
        pole.sub(new Vector3f(dn).mul(dn.dot(pole)));
        if (pole.lengthSquared() < 1.0E-5F) {
            pole.set(0.0F, 0.0F, 1.0F);
        }

        pole.normalize();
        Vector3f upper = new Vector3f(dn).mul(Mth.cos(alpha)).add(new Vector3f(pole).mul(Mth.sin(alpha)));
        float[] xz = TMP.get();
        aim(upper, xz);
        pose.set(shoulder, xz[0], 0.0F, xz[1]);
        Vector3f elbow = new Vector3f(upper).mul(a);
        Vector3f fore = new Vector3f(local).sub(elbow);
        Matrix4f upperRot = new Matrix4f().rotateZYX(xz[1], 0.0F, xz[0]);
        Vector3f foreLocal = new Matrix4f(upperRot).invert().transformDirection(fore);
        aim(foreLocal, xz);
        pose.set(shoulder + 1, xz[0], 0.0F, xz[1]);
        pose.set(shoulder + 2, 0.0F, 0.0F, 0.0F);
    }

    public static final class Frames {
        public final Matrix4f[] joint = new Matrix4f[15];

        public Frames() {
            for (int i = 0; i < 15; i++) {
                this.joint[i] = new Matrix4f();
            }
        }

        public void compute(TitanSkeleton.Pose pose) {
            for (int i = 0; i < 15; i++) {
                Matrix4f m = this.joint[i];
                if (TitanSkeleton.PARENT[i] < 0) {
                    m.identity();
                } else {
                    m.set(this.joint[TitanSkeleton.PARENT[i]]);
                }

                float px = TitanSkeleton.PIVOT[i][0];
                float py = TitanSkeleton.PIVOT[i][1];
                float pz = TitanSkeleton.PIVOT[i][2];
                if (i == 0) {
                    px += pose.rootX;
                    py += pose.rootY;
                    pz += pose.rootZ;
                }

                m.translate(px, py, pz);
                m.rotateZYX(pose.zRot[i], pose.yRot[i], pose.xRot[i]);
            }
        }

        public Vector3f point(int jointIndex, Vector3f local, float localScale, Vector3f out) {
            out.set(local).mul(localScale);
            return this.joint[jointIndex].transformPosition(out);
        }
    }

    public static final class Pose {
        public final float[] xRot = new float[15];
        public final float[] yRot = new float[15];
        public final float[] zRot = new float[15];
        public float rootX;
        public float rootY;
        public float rootZ;
        public float headScale = 1.0F;

        public void reset() {
            for (int i = 0; i < 15; i++) {
                this.xRot[i] = 0.0F;
                this.yRot[i] = 0.0F;
                this.zRot[i] = 0.0F;
            }

            this.rootX = this.rootY = this.rootZ = 0.0F;
        }

        public void set(int joint, float x, float y, float z) {
            this.xRot[joint] = x;
            this.yRot[joint] = y;
            this.zRot[joint] = z;
        }
    }
}
