package com.takem.titans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takem.titans.entity.FallingSectionEntity;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

/**
 * Draws a falling chunk of building as one piece. Faces pressed against another solid block of the same section are
 * never drawn, so a big wall section costs its outer skin rather than every face of every block inside it.
 */
public class FallingSectionRenderer extends EntityRenderer<FallingSectionEntity> {
    private static final Direction[] DIRS = Direction.values();
    /** Per section: for each block, a bit per face that is hidden behind a neighbour. */
    private final Map<BlockState[], byte[]> hidden = new WeakHashMap<>();
    private final RandomSource rand = RandomSource.create();

    public FallingSectionRenderer(Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    private static long key(float x, float y, float z) {
        return ((long)Math.round(x * 2.0F) & 0x1FFFFFL) << 42 | ((long)Math.round(y * 2.0F) & 0x1FFFFFL) << 21 | (long)Math.round(z * 2.0F) & 0x1FFFFFL;
    }

    private static byte[] cull(FallingSectionEntity e) {
        BlockState[] states = e.states;
        int n = states.length;
        byte[] mask = new byte[n];
        if (n < 8 || e.ox.length != n) {
            return mask;
        }

        Long2IntOpenHashMap at = new Long2IntOpenHashMap(n * 2);
        at.defaultReturnValue(-1);
        for (int i = 0; i < n; i++) {
            if (states[i] != null) {
                at.put(key(e.ox[i], e.oy[i], e.oz[i]), i);
            }
        }

        for (int i = 0; i < n; i++) {
            if (states[i] == null) {
                continue;
            }

            for (Direction d : DIRS) {
                int j = at.get(key(e.ox[i] + d.getStepX(), e.oy[i] + d.getStepY(), e.oz[i] + d.getStepZ()));
                if (j >= 0 && states[j].isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                    mask[i] |= (byte)(1 << d.ordinal());
                }
            }
        }

        return mask;
    }

    public void render(FallingSectionEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        BlockState[] states = e.states;
        if (states.length != 0) {
            Level level = e.level();
            int bl = 0;
            int sky = 0;
            double r = (double)e.radius + 1.0;
            double[][] probes = new double[][]{{0.0, r, 0.0}, {r, 0.0, 0.0}, {-r, 0.0, 0.0}, {0.0, 0.0, r}, {0.0, 0.0, -r}, {0.0, 0.0, 0.0}};

            for (double[] o : probes) {
                int c = LevelRenderer.getLightColor(level, BlockPos.containing(e.getX() + o[0], e.getY() + o[1], e.getZ() + o[2]));
                bl = Math.max(bl, LightTexture.block(c));
                sky = Math.max(sky, LightTexture.sky(c));
            }

            int packed = LightTexture.pack(bl, sky);
            byte[] mask = this.hidden.computeIfAbsent(states, k -> cull(e));
            Quaternionf q = new Quaternionf(e.rotO).slerp(e.rotR, partial);
            Minecraft mc = Minecraft.getInstance();
            BlockRenderDispatcher br = mc.getBlockRenderer();
            BlockColors colors = mc.getBlockColors();
            pose.pushPose();
            pose.mulPose(q);

            for (int i = 0; i < states.length; i++) {
                BlockState s = states[i];
                if (s == null || s.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }

                pose.pushPose();
                pose.translate(e.ox[i] - 0.5F, e.oy[i] - 0.5F, e.oz[i] - 0.5F);
                if (mask.length != states.length || mask[i] == 0) {
                    br.renderSingleBlock(s, pose, buffers, packed, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                } else if (mask[i] != 0x3F) {
                    BakedModel model = br.getBlockModel(s);
                    this.rand.setSeed(42L);
                    for (RenderType chunkType : model.getRenderTypes(s, this.rand, ModelData.EMPTY)) {
                        VertexConsumer vc = buffers.getBuffer(RenderTypeHelper.getEntityRenderType(chunkType, false));
                        for (Direction d : DIRS) {
                            if ((mask[i] & 1 << d.ordinal()) == 0) {
                                this.rand.setSeed(42L);
                                this.quads(vc, pose, colors, s, model.getQuads(s, d, this.rand, ModelData.EMPTY, chunkType), packed);
                            }
                        }

                        this.rand.setSeed(42L);
                        this.quads(vc, pose, colors, s, model.getQuads(s, null, this.rand, ModelData.EMPTY, chunkType), packed);
                    }
                }

                pose.popPose();
            }

            pose.popPose();
            super.render(e, yaw, partial, pose, buffers, light);
        }
    }

    private void quads(VertexConsumer vc, PoseStack pose, BlockColors colors, BlockState s, List<BakedQuad> quads, int light) {
        for (BakedQuad quad : quads) {
            float r = 1.0F;
            float g = 1.0F;
            float b = 1.0F;
            if (quad.isTinted()) {
                int c = colors.getColor(s, null, null, quad.getTintIndex());
                r = (float)(c >> 16 & 0xFF) / 255.0F;
                g = (float)(c >> 8 & 0xFF) / 255.0F;
                b = (float)(c & 0xFF) / 255.0F;
            }

            vc.putBulkData(pose.last(), quad, r, g, b, 1.0F, light, OverlayTexture.NO_OVERLAY);
        }
    }

    public ResourceLocation getTextureLocation(FallingSectionEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
