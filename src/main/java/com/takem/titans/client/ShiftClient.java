package com.takem.titans.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takem.titans.TakemTitans;
import com.takem.titans.client.fx.CombatFx;
import com.takem.titans.client.fx.PostFx;
import com.takem.titans.combat.TitanShift;
import com.takem.titans.entity.titan.ShifterTitan;
import com.takem.titans.entity.titan.TitanAction;
import com.takem.titans.network.OdmActionPayload;
import com.takem.titans.registry.ModEntities;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client side of the Titan Shift: the titan body drawn in each shifted player's place (and left behind to boil away
 * when they cut free), the transformation's camera shot, the titan's HUD and screen look, and input while in the flesh:
 * attack strikes, use roars, a hook key leaps, holding sneak cuts free.
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class ShiftClient {
    public static final ResourceLocation HUD = TakemTitans.id("titan_shift");
    private static final Int2ObjectMap<ShifterTitan> BODIES = new Int2ObjectOpenHashMap<>();
    private static final List<ShifterTitan> HUSKS = new ArrayList<>();
    private static final Set<ResourceLocation> HIDDEN = Set.of(
        VanillaGuiLayers.HOTBAR, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL,
        VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.JUMP_METER,
        VanillaGuiLayers.VEHICLE_HEALTH
    );
    /** The transformation's shot lasts this many ticks from the first spark. */
    private static final int CINE_END = TitanShift.IGNITE + 52;
    private static int cine = -1;
    private static Marker cam;
    private static CameraType camBefore;
    private static float yaw0;
    private static int title = -1;
    private static int since = -1;
    private static boolean hud;
    private static float hp = 1.0F;
    private static float hpShown = 1.0F;
    private static float hpGhost = 1.0F;
    private static int life;
    private static int roarCd;
    private static int leapCd;
    private static int sneak;
    private static int hurtFlash;

    private ShiftClient() {
    }

    /** True while the local player is shifting or in the flesh: the ODM gear stands down. */
    public static boolean local() {
        Minecraft mc = Minecraft.getInstance();
        return cine >= 0 || mc.player != null && TitanShift.isShifted(mc.player);
    }

    /** The server's word on the body: vitality (per mille, or -1 when it's over), ticks left, cooldowns and sneak. */
    public static void status(int a, int b, int c) {
        if (a < 0) {
            hud = false;
            return;
        }
        float next = (float)a / 1000.0F;
        if (next < hp - 0.001F) {
            hurtFlash = 8;
        }
        hp = next;
        if (!hud) {
            hpShown = hp;
            hpGhost = hp;
        }
        hud = true;
        life = b;
        roarCd = (c & 255) * 2;
        leapCd = (c >> 8 & 255) * 2;
        sneak = c >> 16 & 15;
    }

    /** The serum's in: the camera leaves the player's eyes for the shot. */
    public static void ignite(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && mc.player.getId() == id && cine < 0) {
            ClientOdm.INSTANCE.releaseAll(mc.player);
            cine = 0;
            since = -1;
            camBefore = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            yaw0 = mc.player.getYRot();
            cam = new Marker(EntityType.MARKER, mc.level);
            placeCamera(mc, 0.0F);
            cam.setOldPosAndRot();
            mc.setCameraEntity(cam);
        }
    }

    public static void struck(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == id) {
            since = 0;
            title = 0;
        }
    }

    public static void action(int id, int ordinal, int skip) {
        ShifterTitan b = BODIES.get(id);
        if (b != null) {
            b.play(TitanAction.byId(ordinal), skip);
        }
    }

    /** Cut free: the body stays where it stood to fall and boil away, and the player gets their own eyes back. */
    public static void burst(int id) {
        ShifterTitan b = BODIES.remove(id);
        if (b != null) {
            b.dyingTicks = 1;
            HUSKS.add(b);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == id) {
            hud = false;
            since = -1;
            endCinematic(mc);
            if (camBefore != null) {
                mc.options.setCameraType(camBefore);
                camBefore = null;
            }
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            BODIES.clear();
            HUSKS.clear();
            hud = false;
            cine = -1;
            cam = null;
            return;
        }
        for (Player p : level.players()) {
            ShifterTitan b = BODIES.get(p.getId());
            if (TitanShift.isShifted(p)) {
                if (b == null) {
                    b = ModEntities.SHIFTER_TITAN.get().create(level);
                    if (b == null) {
                        continue;
                    }
                    b.setPos(p.getX(), p.getY(), p.getZ());
                    b.yBodyRot = p.getYRot();
                    b.yBodyRotO = b.yBodyRot;
                    BODIES.put(p.getId(), b);
                }
                b.follow(p, 1.8F * p.getScale(), bodyYaw(b, p));
                b.forming = Mth.clamp(1.0F - (float)(b.tickCount - 6) / 34.0F, 0.0F, 1.0F);
                CombatFx.bodySteam(level, b);
            } else if (b != null) {
                BODIES.remove(p.getId());
                b.dyingTicks = 1;
                HUSKS.add(b);
            }
        }
        BODIES.int2ObjectEntrySet().removeIf(e -> !(level.getEntity(e.getIntKey()) instanceof Player));
        HUSKS.removeIf(h -> {
            h.dyingTicks++;
            CombatFx.huskTick(level, h);
            return h.dyingTicks >= h.evaporateTicks();
        });
        if (cine >= 0) {
            tickCinematic(mc);
        }
        if (title >= 0 && ++title > 90) {
            title = -1;
        }
        if (since >= 0) {
            since++;
        }
        if (hurtFlash > 0) {
            hurtFlash--;
        }
        hpShown += (hp - hpShown) * 0.35F;
        hpGhost = hpGhost > hpShown ? Math.max(hpShown, hpGhost - 0.012F) : hpShown;
        LocalPlayer me = mc.player;
        if (me != null && TitanShift.isShifted(me)) {
            float hot = since >= 0 && since < 40 ? 1.0F - (float)since / 40.0F : 0.0F;
            PostFx.hold(0.35F + 0.65F * hot, 0.1F + 0.25F * hot, 0.3F + 0.25F * (1.0F - hp), 1.0F, 0.72F, 0.48F);
            while (OdmKeys.HOOK_LEFT.consumeClick() | OdmKeys.HOOK_RIGHT.consumeClick()) {
                send(32);
            }
        } else if (cine >= 0) {
            PostFx.hold(0.25F + 0.75F * Math.min(1.0F, (float)cine / (float)TitanShift.IGNITE), 0.3F, 0.55F, 0.95F, 0.6F, 0.35F);
        }
    }

    /** The body turns to face where the player looks, but with a titan's weight: it lags, and only turns when it must. */
    private static float bodyYaw(ShifterTitan b, Player p) {
        double mx = p.getX() - p.xo;
        double mz = p.getZ() - p.zo;
        boolean moving = mx * mx + mz * mz > 0.003;
        float diff = Mth.wrapDegrees(p.getYHeadRot() - b.yBodyRot);
        float step = !moving && b.getAction() == TitanAction.NONE ? (Math.abs(diff) > 50.0F ? 10.0F : 0.0F) : 18.0F;
        return b.yBodyRot + Mth.clamp(diff, -step, step);
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new OdmActionPayload(action, 0));
    }

    /**
     * The shot: a close-up in front of the face while the charge builds; at the bolt the camera is thrown back and swings
     * round behind the shoulder while the body grows, ending where the titan's own third-person camera takes over.
     */
    private static void placeCamera(Minecraft mc, float t) {
        Player p = mc.player;
        if (p == null || cam == null) {
            return;
        }
        Vec3 base = p.position();
        float height = p.getBbHeight();
        double dist;
        double up;
        double orbit;
        Vec3 target;
        if (t < (float)TitanShift.IGNITE) {
            float k = t / (float)TitanShift.IGNITE;
            dist = 3.6 - 1.2 * (double)k;
            up = 1.45 + 0.1 * (double)k;
            orbit = 28.0 - 18.0 * (double)k;
            target = base.add(0.0, 1.55, 0.0);
        } else {
            float k = Mth.clamp((t - (float)TitanShift.IGNITE) / (float)(CINE_END - TitanShift.IGNITE), 0.0F, 1.0F);
            float e = 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
            dist = 14.0 + 12.0 * (double)e;
            up = 3.0 + 9.0 * (double)e;
            orbit = 10.0 + 170.0 * (double)(k * k * (3.0F - 2.0F * k));
            target = base.add(0.0, (double)height * 0.62, 0.0);
        }
        Vec3 dir = Vec3.directionFromRotation(0.0F, (float)((double)yaw0 + orbit));
        Vec3 pos = base.add(dir.scale(dist)).add(0.0, up, 0.0);
        Vec3 look = target.subtract(pos);
        float yaw = (float)(Mth.atan2(-look.x, look.z) * 57.29577951308232);
        float pitch = (float)(-(Mth.atan2(look.y, look.horizontalDistance()) * 57.29577951308232));
        cam.setPos(pos.x, pos.y, pos.z);
        cam.setYRot(cam.getYRot() + Mth.wrapDegrees(yaw - cam.getYRot()));
        cam.setXRot(pitch);
    }

    private static void tickCinematic(Minecraft mc) {
        if (cam == null || mc.player == null) {
            endCinematic(mc);
            return;
        }
        cine++;
        cam.setOldPosAndRot();
        placeCamera(mc, (float)cine);
        boolean shifted = TitanShift.isShifted(mc.player);
        if (cine >= CINE_END || cine > TitanShift.IGNITE + 12 && !shifted) {
            endCinematic(mc);
            if (shifted) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
    }

    private static void endCinematic(Minecraft mc) {
        if (cine >= 0) {
            cine = -1;
            cam = null;
            if (mc.player != null) {
                mc.setCameraEntity(mc.player);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (Minecraft.getInstance().player != null && local()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (cine < 0) {
                if (event.isAttack()) {
                    send(30);
                } else if (event.isUseItem()) {
                    send(31);
                }
            }
        }
    }

    /** A shifted player is drawn as their titan. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        ShifterTitan b = BODIES.get(event.getEntity().getId());
        if (b != null) {
            event.setCanceled(true);
            float pt = event.getPartialTick();
            Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getRenderer(b)
                .render(b, Mth.rotLerp(pt, b.yRotO, b.getYRot()), pt, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        }
    }

    /** The bodies left behind, no longer anyone's, fall and boil away where they stood. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES && !HUSKS.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Vec3 camPos = event.getCamera().getPosition();
                float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
                for (ShifterTitan h : HUSKS) {
                    PoseStack ps = new PoseStack();
                    ps.translate(h.getX() - camPos.x, h.getY() - camPos.y, h.getZ() - camPos.z);
                    int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(h.getX(), h.getY() + 2.0, h.getZ()));
                    mc.getEntityRenderDispatcher().getRenderer(h).render(h, h.getYRot(), pt, ps, buffers, light);
                }
                buffers.endBatch();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHand(RenderHandEvent event) {
        if (local()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (local()) {
            ResourceLocation name = event.getName();
            boolean ours = "takemtitans".equals(name.getNamespace())
                && !name.equals(HUD)
                && !name.getPath().contains("screen")
                && !name.getPath().contains("hit");
            if (HIDDEN.contains(name) || ours || cine >= 0 && name.equals(VanillaGuiLayers.CROSSHAIR)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && event.getCamera().getEntity() == mc.player && TitanShift.isShifted(mc.player)) {
            event.setDistance(Math.min(event.getDistance(), 6.0F + 1.15F * mc.player.getScale()));
        }
    }

    /** The body's long stride would otherwise blow the view wide open like a sprint ten times over. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFov(ComputeFovModifierEvent event) {
        if (TitanShift.isShifted(event.getPlayer())) {
            event.setNewFovModifier(event.getPlayer().isSprinting() ? 1.08F : 1.0F);
        }
    }

    /** The sky darkens while the charge builds, then burns amber after the bolt. */
    @SubscribeEvent
    public static void onFog(ViewportEvent.ComputeFogColor event) {
        float k = 0.0F;
        float r = 1.0F;
        float g = 0.62F;
        float b = 0.3F;
        if (cine >= 0 && cine < TitanShift.IGNITE) {
            k = 0.55F * (float)cine / (float)TitanShift.IGNITE;
            r = 0.32F;
            g = 0.2F;
            b = 0.12F;
        } else if (since >= 0 && since < 80) {
            float f = 1.0F - (float)since / 80.0F;
            k = 0.6F * f * f;
        }
        if (k > 0.0F) {
            event.setRed(Mth.lerp(k, event.getRed(), r));
            event.setGreen(Mth.lerp(k, event.getGreen(), g));
            event.setBlue(Mth.lerp(k, event.getBlue(), b));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BODIES.clear();
        HUSKS.clear();
        hud = false;
        title = -1;
        since = -1;
        cine = -1;
        cam = null;
        if (camBefore != null) {
            Minecraft.getInstance().options.setCameraType(camBefore);
            camBefore = null;
        }
    }

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth();
        int h = g.guiHeight();
        Font font = mc.font;
        if (cine >= 0) {
            // the shot's letterbox
            float bars = Math.min(1.0F, ((float)cine + pt) / 6.0F) * Mth.clamp(((float)(CINE_END - cine) - pt) / 8.0F, 0.0F, 1.0F);
            int bh = (int)((float)h * 0.11F * bars);
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
        if (title >= 0) {
            drawTitle(g, font, w, h, (float)title + pt);
        }
        if (!hud || cine >= 0) {
            return;
        }
        int cx = w / 2;
        int bw = 220;
        int x0 = cx - bw / 2;
        int y0 = h - 32;
        g.fill(x0 - 3, y0 - 3, x0 + bw + 3, y0 + 14, 0xA0120A08);
        g.fill(x0 - 2, y0 - 2, x0 + bw + 2, y0 - 1, 0x70C9A24A);
        // vitality, the white ghost of what was just lost trailing behind
        g.fill(x0, y0, x0 + (int)((float)bw * Mth.clamp(hpGhost, 0.0F, 1.0F)), y0 + 7, 0xFFF0E2D0);
        float pulse = hp < 0.3F ? 0.5F + 0.5F * Mth.sin(((float)mc.player.tickCount + pt) * 0.5F) : 0.0F;
        int top = hurtFlash > 0 ? 0xFFFFE6D8 : mix(0xFFE0502C, 0xFFFF9A70, pulse);
        g.fillGradient(x0, y0, x0 + (int)((float)bw * Mth.clamp(hpShown, 0.0F, 1.0F)), y0 + 7, top, 0xFF7A160E);
        for (int i = 1; i < 10; i++) {
            g.fill(x0 + bw * i / 10, y0, x0 + bw * i / 10 + 1, y0 + 7, 0x40000000);
        }
        // the time left: the body is only borrowed
        float t = Mth.clamp((float)life / (float)TitanShift.LIFE, 0.0F, 1.0F);
        int timeColor = t < 0.2F ? 0xFFFFD25A : 0xFFE8B04A;
        g.fill(x0, y0 + 9, x0 + bw, y0 + 11, 0x60000000);
        g.fill(x0, y0 + 9, x0 + (int)((float)bw * t), y0 + 11, timeColor);
        g.drawString(font, Component.translatable("hud.takemtitans.shift.name"), x0, y0 - 13, 0xFFE8DCC8, true);
        String secs = (life + 19) / 20 + "s";
        g.drawString(font, secs, x0 + bw - font.width(secs), y0 - 13, t < 0.2F ? 0xFFFFD25A : 0xFFE8DCC8, true);
        int roarW = chipWidth(font, Component.translatable("hud.takemtitans.shift.roar"), mc.options.keyUse.getTranslatedKeyMessage());
        chip(g, font, x0 - 8 - roarW, y0 - 3, roarW, Component.translatable("hud.takemtitans.shift.roar"), mc.options.keyUse.getTranslatedKeyMessage(), roarCd, TitanShift.ROAR_CD);
        int leapW = chipWidth(font, Component.translatable("hud.takemtitans.shift.leap"), OdmKeys.HOOK_LEFT.getTranslatedKeyMessage());
        chip(g, font, x0 + bw + 8, y0 - 3, leapW, Component.translatable("hud.takemtitans.shift.leap"), OdmKeys.HOOK_LEFT.getTranslatedKeyMessage(), leapCd, TitanShift.LEAP_CD);
        Component hint = Component.translatable("hud.takemtitans.shift.release", mc.options.keyShift.getTranslatedKeyMessage());
        int hw = font.width(hint);
        int hy = y0 + 17;
        if (sneak > 0) {
            g.fill(cx - hw / 2, hy + 9, cx - hw / 2 + (int)((float)hw * Math.min(1.0F, (float)sneak / 15.0F)), hy + 10, 0xFFE8B04A);
        }
        g.drawString(font, hint, cx - hw / 2, hy, 0xA0E8DCC8, true);
    }

    private static int chipWidth(Font font, Component name, Component key) {
        return font.width(name) + font.width(key) + 18;
    }

    private static void chip(GuiGraphics g, Font font, int x, int y, int cw, Component name, Component key, int cd, int max) {
        int ch = 14;
        g.fill(x, y, x + cw, y + ch, 0xA0120A08);
        int fill = cd > 0 ? (int)((float)cw * (1.0F - (float)cd / (float)max)) : cw;
        g.fill(x, y + ch - 2, x + fill, y + ch, cd > 0 ? 0xFFE8B04A : 0xFFFFE9A8);
        g.drawString(font, name, x + 4, y + 3, cd > 0 ? 0xFF8C8478 : 0xFFF4EAD8, false);
        Component k = Component.literal("[").append(key).append("]");
        g.drawString(font, k, x + cw - 4 - font.width(k), y + 3, 0xFFC9A24A, false);
    }

    /** TITAN SHIFT slams in, the colours split apart and close up, and fades. */
    private static void drawTitle(GuiGraphics g, Font font, int w, int h, float t) {
        float in = Mth.clamp(t / 6.0F, 0.0F, 1.0F);
        float a = in * Mth.clamp((90.0F - t) / 20.0F, 0.0F, 1.0F);
        if (a <= 0.03F) {
            return;
        }
        Component text = Component.translatable("hud.takemtitans.shift.title");
        float scale = 3.2F + 0.8F * (1.0F - in);
        float spread = 2.5F * (1.0F - in) + 0.6F;
        int alpha = (int)(a * 255.0F) << 24;
        int tw = font.width(text);
        g.pose().pushPose();
        g.pose().translate((float)w / 2.0F, (float)h * 0.34F, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, (int)(-(float)tw / 2.0F - spread), -4, alpha | 0xFF3B2A, false);
        g.drawString(font, text, (int)(-(float)tw / 2.0F + spread), -4, alpha | 0x2AD4FF, false);
        g.drawString(font, text, -tw / 2, -4, alpha | 0xFFF4E0, false);
        g.pose().popPose();
        Component sub = Component.translatable("hud.takemtitans.shift.subtitle");
        int sw = font.width(sub);
        int y = (int)((float)h * 0.34F) + 16;
        int line = (int)((float)sw * 0.7F * in);
        g.fill(w / 2 - line, y, w / 2 + line, y + 1, alpha | 0xC9A24A);
        g.drawString(font, sub, (w - sw) / 2, y + 6, alpha | 0xE8DCC8, false);
    }

    private static int mix(int a, int b, float t) {
        int r = (int)Mth.lerp(t, (float)(a >> 16 & 255), (float)(b >> 16 & 255));
        int gg = (int)Mth.lerp(t, (float)(a >> 8 & 255), (float)(b >> 8 & 255));
        int bb = (int)Mth.lerp(t, (float)(a & 255), (float)(b & 255));
        return 0xFF000000 | r << 16 | gg << 8 | bb;
    }
}
