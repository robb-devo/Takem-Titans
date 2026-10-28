package com.takem.titans.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

/**
 * Afterimages: glowing, see-through copies of a player frozen in the pose they had a moment ago. A phase dash leaves a
 * string of them along its path; a phantom rig at full tilt sheds one now and then.
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class GhostTrail {
    private static final List<GhostTrail.Ghost> GHOSTS = new ArrayList<>();
    private static final List<GhostTrail.Burst> BURSTS = new ArrayList<>();
    private static final int MAX = 64;

    private GhostTrail() {
    }

    /** Leave an afterimage every tick for the next {@code ticks} ticks. */
    public static void burst(Player p, int ticks, int color) {
        BURSTS.removeIf(b -> b.player == p);
        BURSTS.add(new GhostTrail.Burst(p, ticks, color));
        drop(p, color, 12, 0.55F);
    }

    /** One fainter afterimage where the player is right now. */
    public static void drop(Player p, int color, int life, float alpha) {
        if (p instanceof AbstractClientPlayer cp) {
            if (GHOSTS.size() >= MAX) {
                GHOSTS.remove(0);
            }

            GHOSTS.add(
                new GhostTrail.Ghost(
                    cp,
                    p.position(),
                    p.yBodyRot,
                    p.yHeadRot,
                    p.getXRot(),
                    p.walkAnimation.position(),
                    p.walkAnimation.speed(),
                    (float)p.tickCount,
                    color,
                    life,
                    alpha
                )
            );
        }
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            GHOSTS.clear();
            BURSTS.clear();
        } else if (!mc.isPaused()) {
            GHOSTS.removeIf(g -> ++g.age >= g.life || g.player.isRemoved());
            Iterator<GhostTrail.Burst> it = BURSTS.iterator();

            while (it.hasNext()) {
                GhostTrail.Burst b = it.next();
                if (--b.ticks < 0 || b.player.isRemoved()) {
                    it.remove();
                } else {
                    drop(b.player, b.color, 12, 0.5F);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() == Stage.AFTER_ENTITIES && !GHOSTS.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 cam = event.getCamera().getPosition();
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            PoseStack ps = event.getPoseStack();
            BufferSource buffers = mc.renderBuffers().bufferSource();

            boolean firstPerson = mc.options.getCameraType().isFirstPerson();

            for (GhostTrail.Ghost g : GHOSTS) {
                // Your own afterimages only show once you've left them behind: in first person the ones next to the
                // camera would sit right inside your view. Everyone else's fade only when the camera is in them.
                boolean own = firstPerson && g.player == mc.player;
                double d = g.pos.add(0.0, 0.9, 0.0).distanceTo(cam);
                float near = own ? (float)Mth.clamp((d - 4.0) / 3.0, 0.0, 1.0) : (float)Mth.clamp((d - 1.2) / 1.5, 0.0, 1.0);
                if (near > 0.0F) {
                    EntityRenderer<? super AbstractClientPlayer> r = mc.getEntityRenderDispatcher().getRenderer(g.player);
                    if (r instanceof PlayerRenderer pr) {
                        float life = Mth.clamp(((float)g.age + pt) / (float)g.life, 0.0F, 1.0F);
                        float a = g.alpha * (1.0F - life) * (1.0F - life) * near;
                        if (!(a < 0.02F)) {
                            ResourceLocation skin = g.player.getSkin().texture();
                            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(skin));
                            PlayerModel<AbstractClientPlayer> model = pr.getModel();
                            ps.pushPose();
                            // the ghost drifts back a touch and swells as it fades, like heat shimmer
                            ps.translate(g.pos.x - cam.x, g.pos.y - cam.y + (double)(life * 0.15F), g.pos.z - cam.z);
                            ps.mulPose(Axis.YP.rotationDegrees(180.0F - g.bodyYaw));
                            float s = 0.9375F * (1.0F + life * 0.08F);
                            ps.scale(-s, -s, s);
                            ps.translate(0.0F, -1.501F, 0.0F);
                            model.attackTime = 0.0F;
                            model.riding = false;
                            model.young = false;
                            model.setupAnim(g.player, g.limbPos, g.limbSpeed, g.age0, g.headYaw - g.bodyYaw, g.pitch);
                            int argb = (int)(a * 255.0F) << 24 | g.color & 16777215;
                            model.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
                            ps.popPose();
                        }
                    }
                }
            }

            buffers.endBatch();
        }
    }

    private static final class Ghost {
        final AbstractClientPlayer player;
        final Vec3 pos;
        final float bodyYaw;
        final float headYaw;
        final float pitch;
        final float limbPos;
        final float limbSpeed;
        final float age0;
        final int color;
        final int life;
        final float alpha;
        int age;

        Ghost(
            AbstractClientPlayer player,
            Vec3 pos,
            float bodyYaw,
            float headYaw,
            float pitch,
            float limbPos,
            float limbSpeed,
            float age0,
            int color,
            int life,
            float alpha
        ) {
            this.player = player;
            this.pos = pos;
            this.bodyYaw = bodyYaw;
            this.headYaw = headYaw;
            this.pitch = pitch;
            this.limbPos = limbPos;
            this.limbSpeed = limbSpeed;
            this.age0 = age0;
            this.color = color;
            this.life = life;
            this.alpha = alpha;
        }
    }

    private static final class Burst {
        final Player player;
        final int color;
        int ticks;

        Burst(Player player, int ticks, int color) {
            this.player = player;
            this.ticks = ticks;
            this.color = color;
        }
    }
}
