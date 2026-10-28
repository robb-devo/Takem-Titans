package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import com.takem.titans.TakemTitans;
import com.takem.titans.client.ClientOdm;
import com.takem.titans.client.RemoteHooks;
import com.takem.titans.client.Waypoints;
import com.takem.titans.client.story.StoryClient;
import com.takem.titans.entity.npc.JaegerEntity;
import com.takem.titans.odm.HookState;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class RopeRenderer {
    public static final ResourceLocation CABLE = TakemTitans.id("textures/entity/odm_cable.png");
    public static final ResourceLocation HOOK = TakemTitans.id("textures/entity/odm_hook.png");
    private static ModelPart hookModel;

    private RopeRenderer() {
    }

    public static LayerDefinition createHookLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("shaft", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, -4.5F, 1.0F, 1.0F, 5.0F), PartPose.ZERO);
        root.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(0, 6).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 1.5F), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(8, 6).addBox(-0.75F, -0.75F, 0.5F, 1.5F, 1.5F, 1.5F), PartPose.ZERO);
        root.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(12, 0).addBox(-0.35F, -0.35F, 2.0F, 0.7F, 0.7F, 1.2F), PartPose.ZERO);

        for (int i = 0; i < 4; i++) {
            root.addOrReplaceChild(
                "claw" + i,
                CubeListBuilder.create().texOffs(0, 10).addBox(-0.25F, 0.6F, -2.2F, 0.5F, 0.5F, 2.6F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 1.2F, 0.0F, 0.0F, (float)i * 1.5707964F)
            );
        }

        return LayerDefinition.create(mesh, 16, 16);
    }

    private static ModelPart hookModel() {
        if (hookModel == null) {
            hookModel = createHookLayer().bakeRoot();
        }

        return hookModel;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == Stage.AFTER_ENTITIES) {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level != null) {
                float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                Vec3 cam = event.getCamera().getPosition();
                PoseStack ps = event.getPoseStack();
                BufferSource buffers = mc.renderBuffers().bufferSource();
                boolean any = false;

                for (Player p : level.players()) {
                    HookState[] hooks = p == mc.player ? ClientOdm.INSTANCE.allHooks() : RemoteHooks.get(p.getId());
                    if (hooks != null) {
                        for (HookState h : hooks) {
                            if (h.isActive()) {
                                renderHook(ps, buffers, level, p, h, pt, cam);
                                any = true;
                            }
                        }
                    }
                }

                if (mc.player != null) {
                    for (JaegerEntity j : level.getEntitiesOfClass(JaegerEntity.class, mc.player.getBoundingBox().inflate(128.0))) {
                        if (j.isHooked()) {
                            renderJaegerRope(ps, buffers, level, j, pt, cam);
                            any = true;
                        }
                    }
                }

                if (any) {
                    buffers.endBatch(RenderType.entityCutoutNoCull(CABLE));
                    buffers.endBatch(RenderType.entityCutoutNoCull(HOOK));
                }

                BlockPos story = StoryClient.beamTarget();
                if (story != null) {
                    long time = level.getGameTime();
                    ps.pushPose();
                    ps.translate((double)story.getX() + 0.5 - 0.5 - cam.x, (double)level.getMinBuildHeight() - cam.y, (double)story.getZ() - cam.z);
                    BeaconRenderer.renderBeaconBeam(ps, buffers, BeaconRenderer.BEAM_LOCATION, pt, 1.0F, time, 0, level.getHeight(), -1056980422, 0.35F, 0.45F);
                    ps.popPose();
                    buffers.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false));
                    buffers.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
                }

                if (!Waypoints.POINTS.isEmpty()) {
                    long time = level.getGameTime();

                    for (Waypoints.Point wp : Waypoints.POINTS) {
                        float life = 1.0F - (float)wp.age / 1200.0F;
                        int alpha = (int)(Mth.clamp(life * 4.0F, 0.0F, 1.0F) * 255.0F);
                        int color = alpha << 24 | 6021994;
                        ps.pushPose();
                        ps.translate((double)wp.pos.getX() - cam.x, (double)level.getMinBuildHeight() - cam.y, (double)wp.pos.getZ() - cam.z);
                        BeaconRenderer.renderBeaconBeam(ps, buffers, BeaconRenderer.BEAM_LOCATION, pt, 1.0F, time, 0, level.getHeight(), color, 0.25F, 0.32F);
                        ps.popPose();
                    }

                    buffers.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false));
                    buffers.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
                }
            }
        }
    }

    private static void renderJaegerRope(PoseStack ps, MultiBufferSource buffers, ClientLevel level, JaegerEntity j, float pt, Vec3 cam) {
        Vec3 start = j.getPosition(pt).add(0.0, 0.95, 0.0);
        Vec3 end = j.anchorPoint();
        drawCable(ps, buffers, level, start, end, 0.0, pt, cam, j.tickCount);
    }

    private static void drawCable(PoseStack ps, MultiBufferSource buffers, ClientLevel level, Vec3 start, Vec3 end, double sag, float pt, Vec3 cam, int tick) {
        Vec3 span = end.subtract(start);
        double dist = span.length();
        if (!(dist < 0.05)) {
            int segments = Mth.clamp((int)(dist / 1.0), 6, 48);
            Vec3[] pts = new Vec3[segments + 1];
            double wobble = Math.sin((double)((float)tick + pt) * 2.7) * 0.012;
            Vec3 perp = span.cross(new Vec3(0.0, 1.0, 0.0));
            perp = perp.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : perp.normalize();

            for (int i = 0; i <= segments; i++) {
                double t = (double)i / (double)segments;
                double bow = 4.0 * t * (1.0 - t);
                pts[i] = start.add(span.scale(t)).add(0.0, -sag * bow, 0.0).add(perp.scale(wobble * bow * dist * 0.2));
            }

            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CABLE));
            Pose pose = ps.last();
            float v = 0.0F;

            for (int i = 0; i < segments; i++) {
                Vec3 a = pts[i];
                Vec3 b = pts[i + 1];
                Vec3 dir = b.subtract(a);
                double len = dir.length();
                if (!(len < 1.0E-5)) {
                    Vec3 sa = side(a, dir, cam);
                    Vec3 sb = side(b, dir, cam);
                    Vec3 mid = a.add(b).scale(0.5);
                    int light = LevelRenderer.getLightColor(level, BlockPos.containing(mid));
                    float v1 = v + (float)(len / 0.6);
                    vertex(vc, pose, a.subtract(sa), cam, 0.0F, v, light);
                    vertex(vc, pose, a.add(sa), cam, 1.0F, v, light);
                    vertex(vc, pose, b.add(sb), cam, 1.0F, v1, light);
                    vertex(vc, pose, b.subtract(sb), cam, 0.0F, v1, light);
                    v = v1;
                }
            }
        }
    }

    private static void renderHook(PoseStack ps, MultiBufferSource buffers, ClientLevel level, Player p, HookState h, float pt, Vec3 cam) {
        Vec3 start = HookState.launcherPos(p, h.side, pt);
        Vec3 end = h.renderPos(level, pt);
        Vec3 span = end.subtract(start);
        double dist = span.length();
        if (!(dist < 0.05)) {
            double target;
            if (h.isAttached()) {
                double slack = Math.max(0.0, h.ropeLength - dist);
                target = Math.min(slack * 0.45, 8.0);
            } else if (h.phase == HookState.Phase.FLYING) {
                target = Math.min(1.6, dist * 0.025);
            } else {
                target = Math.min(2.5, dist * 0.09);
            }

            // the cable has weight: it swings into its sag and out of it, and twangs when it is snapped taut
            RopeRenderer.Cable c = CABLES.computeIfAbsent(h, k -> new RopeRenderer.Cable());
            float now = (float)level.getGameTime() + pt;
            float dt = c.last < 0.0F ? 0.0F : Mth.clamp(now - c.last, 0.0F, 2.0F);
            c.last = now;
            if (dt <= 0.0F && c.sag == 0.0F) {
                c.sag = (float)target;
            }

            c.sagVel = c.sagVel + ((float)target - c.sag) * 0.45F * dt - c.sagVel * 0.75F * dt;
            c.sag = c.sag + c.sagVel * dt;
            if (c.sag < 0.0F) {
                if (c.sagVel < -0.15F && h.isAttached()) {
                    c.twang = Math.min(0.6F, c.twang - c.sagVel * 0.5F);
                }

                c.sag = 0.0F;
                c.sagVel = 0.0F;
            }

            c.twang = c.twang * (float)Math.pow(0.8, (double)dt);
            double sag = (double)c.sag;
            int segments = sag > 0.05 || c.twang > 0.01F || h.reeling ? Mth.clamp((int)(dist / 1.0), 6, 48) : 1;
            Vec3[] pts = new Vec3[segments + 1];
            double wobble = h.reeling ? Math.sin((double)now * 2.7) * 0.012 : 0.0;
            double ring = (double)c.twang * Math.sin((double)now * 2.4);
            Vec3 perp = span.cross(new Vec3(0.0, 1.0, 0.0));
            perp = perp.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : perp.normalize();

            for (int i = 0; i <= segments; i++) {
                double t = (double)i / (double)segments;
                double bow = 4.0 * t * (1.0 - t);
                double hum = Math.sin(t * Math.PI) * ring;
                pts[i] = start.add(span.scale(t)).add(0.0, -sag * bow + hum * 0.6, 0.0).add(perp.scale(wobble * bow * dist * 0.2 + hum));
            }

            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CABLE));
            Pose pose = ps.last();
            float v = 0.0F;

            for (int i = 0; i < segments; i++) {
                Vec3 a = pts[i];
                Vec3 b = pts[i + 1];
                Vec3 dir = b.subtract(a);
                double len = dir.length();
                if (!(len < 1.0E-5)) {
                    Vec3 sa = side(a, dir, cam);
                    Vec3 sb = side(b, dir, cam);
                    Vec3 mid = a.add(b).scale(0.5);
                    int light = LevelRenderer.getLightColor(level, BlockPos.containing(mid));
                    float v1 = v + (float)(len / 0.6);
                    vertex(vc, pose, a.subtract(sa), cam, 0.0F, v, light);
                    vertex(vc, pose, a.add(sa), cam, 1.0F, v, light);
                    vertex(vc, pose, b.add(sb), cam, 1.0F, v1, light);
                    vertex(vc, pose, b.subtract(sb), cam, 0.0F, v1, light);
                    v = v1;
                }
            }

            Vec3 headDir = segments > 1 ? end.subtract(pts[segments - 1]) : span;
            if (headDir.lengthSqr() < 1.0E-8) {
                headDir = span;
            }

            headDir = headDir.normalize();
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(end));
            ps.pushPose();
            ps.translate(end.x - cam.x, end.y - cam.y, end.z - cam.z);
            ps.mulPose(Axis.YP.rotation((float)Math.atan2(headDir.x, headDir.z)));
            ps.mulPose(Axis.XP.rotation((float)(-Math.asin(Mth.clamp(headDir.y, -1.0, 1.0)))));
            if (h.isAttached()) {
                ps.translate(0.0F, 0.0F, -0.12F);
            }

            ps.scale(1.6F, 1.6F, 1.6F);
            hookModel().render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(HOOK)), light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }

    /** The drawn cable's own motion, per hook: how far it sags right now, how fast that changes, and how hard it rings. */
    private static final Map<HookState, RopeRenderer.Cable> CABLES = new WeakHashMap<>();

    private static final class Cable {
        float sag;
        float sagVel;
        float twang;
        float last = -1.0F;
    }

    private static Vec3 side(Vec3 point, Vec3 dir, Vec3 cam) {
        Vec3 view = point.subtract(cam);
        double width = Math.max(0.045, view.length() * 0.0021);
        Vec3 s = dir.cross(view);
        if (s.lengthSqr() < 1.0E-10) {
            s = dir.cross(new Vec3(0.0, 1.0, 0.0));
        }

        if (s.lengthSqr() < 1.0E-10) {
            s = new Vec3(1.0, 0.0, 0.0);
        }

        return s.normalize().scale(width * 0.5);
    }

    private static void vertex(VertexConsumer vc, Pose pose, Vec3 p, Vec3 cam, float u, float v, int light) {
        vc.addVertex(pose, (float)(p.x - cam.x), (float)(p.y - cam.y), (float)(p.z - cam.z))
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
