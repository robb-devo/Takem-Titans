package com.takem.titans.odm;

import com.takem.titans.item.GearTier;
import com.takem.titans.skill.Skills;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class OdmPhysics {
    private OdmPhysics() {
    }

    public static void travel(Player p, Vec3 input, OdmPhysics.Flight f) {
        GearTier tier = OdmGear.tier(p);
        Vec3 v = p.getDeltaMovement();
        HookState[] hooks = f.hooks();
        boolean hooked = false;
        int reelingCount = 0;

        for (HookState h : hooks) {
            if (h.isAttached()) {
                hooked = true;
                if (f.isReeling(h)) {
                    reelingCount++;
                }
            }
        }

        v = v.add(0.0, -p.getGravity() * 0.85, 0.0);
        Vec3 wish = inputDirection(input, p.getYRot());
        v = v.add(wish.scale(hooked ? 0.016 : 0.022));
        if (f.isBoosting()) {
            Vec3 dir = p.getLookAngle().add(wish.scale(0.85));
            if (dir.lengthSqr() > 1.0E-6) {
                v = v.add(dir.normalize().scale(0.092 * tier.boost * f.boostScale()));
            }
        }

        v = v.add(f.consumeImpulse());
        Vec3 origin = p.position().add(0.0, 0.95, 0.0);
        double pullScale = reelingCount >= 2 ? 0.72 : 1.0;

        for (HookState hx : hooks) {
            if (hx.isAttached()) {
                Vec3 d = f.anchorOf(hx).subtract(origin);
                double dist = d.length();
                if (!(dist < 0.001)) {
                    Vec3 dir = d.scale(1.0 / dist);
                    if (f.isReeling(hx)) {
                        double reel = tier.reel * f.reelMul(p);
                        hx.ropeLength = Math.max(1.6, Math.min(hx.ropeLength, dist) - 0.95 * reel);
                        double soft = Mth.clamp(dist / 5.0, 0.2, 1.0);
                        v = v.add(dir.scale(0.125 * reel * soft * pullScale));
                        if (p.onGround() && dir.y > -0.25) {
                            double lift = 0.42 * Mth.clamp(dir.y + 0.6, 0.45, 1.0);
                            if (v.y < lift) {
                                v = new Vec3(v.x, lift, v.z);
                            }
                        }

                        if (hx.entityId < 0 && f.arrivalAssist()) {
                            double approach = v.dot(dir);
                            double allowed = Math.max(0.32, Math.sqrt(0.22 * Math.max(0.0, dist - 1.6)));
                            if (approach > allowed) {
                                v = v.subtract(dir.scale(approach - allowed));
                            }
                        }
                    } else {
                        hx.ropeLength = Math.max(1.6, hx.ropeLength);
                    }
                }
            }
        }

        Vec3 correction = Vec3.ZERO;

        for (int pass = 0; pass < 2; pass++) {
            for (HookState hxx : hooks) {
                if (hxx.isAttached()) {
                    Vec3 anchor = f.anchorOf(hxx);
                    Vec3 next = origin.add(v).add(correction);
                    Vec3 toAnchor = anchor.subtract(next);
                    double nd = toAnchor.length();
                    if (nd > hxx.ropeLength && nd > 0.001) {
                        Vec3 n = toAnchor.scale(1.0 / nd);
                        double radial = v.dot(n);
                        if (radial < 0.0) {
                            v = v.subtract(n.scale(radial));
                        }

                        double excess = nd - hxx.ropeLength;
                        if (pass == 0) {
                            correction = correction.add(n.scale(Math.min(excess, 0.7)));
                        }
                    }
                }
            }
        }

        double speed = v.length();
        double drag = 0.004 + 0.019 * speed / tier.maxSpeed;
        v = v.scale(Math.max(0.0, 1.0 - drag));
        speed = v.length();
        double cap = 3.4 * tier.maxSpeed * f.boostScale();
        if (speed > cap) {
            v = v.scale(cap / speed);
        }

        p.setDeltaMovement(v);
        p.move(MoverType.SELF, v.add(correction));
        f.afterMove(p, v, p.getDeltaMovement(), p.horizontalCollision);
        p.calculateEntityAnimation(false);
    }

    public static Vec3 inputDirection(Vec3 input, float yRot) {
        double x = input.x;
        double z = input.z;
        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0E-4) {
            return Vec3.ZERO;
        } else {
            if (len > 1.0) {
                x /= len;
                z /= len;
            }

            float sin = Mth.sin(yRot * 0.017453292F);
            float cos = Mth.cos(yRot * 0.017453292F);
            return new Vec3(x * (double)cos - z * (double)sin, 0.0, z * (double)cos + x * (double)sin);
        }
    }

    public static double equivalentFallHeight(double downSpeed, double gravity) {
        return downSpeed <= 0.0 ? 0.0 : downSpeed * downSpeed / (2.0 * gravity * 0.85);
    }

    public interface Flight {
        HookState[] hooks();

        Vec3 anchorOf(HookState var1);

        boolean isReeling(HookState var1);

        boolean isBoosting();

        boolean arrivalAssist();

        Vec3 consumeImpulse();

        void afterMove(Player var1, Vec3 var2, Vec3 var3, boolean var4);

        default double boostScale() {
            return 1.0;
        }

        default double reelMul(Player p) {
            return Skills.reelMul(p);
        }
    }
}
