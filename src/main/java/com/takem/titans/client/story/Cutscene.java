package com.takem.titans.client.story;

import com.takem.titans.TakemTitans;
import com.takem.titans.network.StoryActionPayload;
import com.takem.titans.registry.ModSounds;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class Cutscene {
    public static final ResourceLocation LAYER = TakemTitans.id("cutscene");
    private static final Map<String, Cutscene.Scene> MORE = Map.of(
        "tunnels",
        new Cutscene.Scene(
            220,
            List.of(
                new Cutscene.Key(0, 0.0, 6.0, -10.0, 0.0, 4.0, 0.0),
                new Cutscene.Key(110, 8.0, 10.0, -18.0, 0.0, 6.0, 0.0),
                new Cutscene.Key(220, 0.0, 3.0, -5.0, 0.0, 2.0, 0.0)
            ),
            List.of(
                new Cutscene.Line(8, 90, "cutscene.takemtitans.tunnels.0"),
                new Cutscene.Line(95, 170, "cutscene.takemtitans.tunnels.1"),
                new Cutscene.Line(175, 220, "cutscene.takemtitans.tunnels.2")
            )
        ),
        "wall_titan",
        new Cutscene.Scene(
            220,
            List.of(
                new Cutscene.Key(0, 30.0, 8.0, 60.0, 0.0, 30.0, 0.0),
                new Cutscene.Key(110, -20.0, 30.0, 80.0, 0.0, 45.0, 0.0),
                new Cutscene.Key(220, 0.0, 50.0, 70.0, 0.0, 50.0, 0.0)
            ),
            List.of(
                new Cutscene.Line(10, 95, "cutscene.takemtitans.wall_titan.0"),
                new Cutscene.Line(100, 170, "cutscene.takemtitans.wall_titan.1"),
                new Cutscene.Line(175, 220, "cutscene.takemtitans.wall_titan.2")
            )
        )
    );
    private static final Map<String, Cutscene.Scene> SCENES = Map.of(
        "intro",
        new Cutscene.Scene(
            300,
            List.of(
                new Cutscene.Key(0, 0.0, 72.0, -150.0, 0.0, 20.0, 40.0),
                new Cutscene.Key(90, -70.0, 48.0, -60.0, 0.0, 8.0, 10.0),
                new Cutscene.Key(190, -30.0, 22.0, 30.0, 4.0, 4.0, 0.0),
                new Cutscene.Key(300, 1.0, 2.6, 0.5, 5.0, 1.7, 7.0)
            ),
            List.of(
                new Cutscene.Line(5, 75, "cutscene.takemtitans.intro.0"),
                new Cutscene.Line(80, 155, "cutscene.takemtitans.intro.1"),
                new Cutscene.Line(160, 225, "cutscene.takemtitans.intro.2"),
                new Cutscene.Line(230, 300, "cutscene.takemtitans.intro.3")
            )
        ),
        "trost",
        new Cutscene.Scene(
            220,
            List.of(
                new Cutscene.Key(0, 0.0, 3.0, -5.0, 0.0, 3.0, 30.0),
                new Cutscene.Key(90, 8.0, 34.0, -16.0, 0.0, 4.0, 120.0),
                new Cutscene.Key(220, -14.0, 52.0, 30.0, 0.0, 0.0, 170.0)
            ),
            List.of(
                new Cutscene.Line(10, 80, "cutscene.takemtitans.trost.0"),
                new Cutscene.Line(85, 150, "cutscene.takemtitans.trost.1"),
                new Cutscene.Line(155, 220, "cutscene.takemtitans.trost.2")
            )
        ),
        "armored",
        new Cutscene.Scene(
            200,
            List.of(
                new Cutscene.Key(0, 0.0, 3.0, 38.0, 0.0, 9.0, 0.0),
                new Cutscene.Key(110, -14.0, 9.0, 22.0, 0.0, 13.0, 0.0),
                new Cutscene.Key(200, -7.0, 15.0, 15.0, 0.0, 14.0, 0.0)
            ),
            List.of(new Cutscene.Line(15, 95, "cutscene.takemtitans.armored.0"), new Cutscene.Line(100, 200, "cutscene.takemtitans.armored.1"))
        ),
        "beast",
        new Cutscene.Scene(
            200,
            List.of(
                new Cutscene.Key(0, 6.0, 2.0, 44.0, 0.0, 12.0, 0.0),
                new Cutscene.Key(110, 18.0, 12.0, 26.0, 0.0, 15.0, 0.0),
                new Cutscene.Key(200, 6.0, 18.0, 17.0, 0.0, 16.0, 0.0)
            ),
            List.of(new Cutscene.Line(15, 95, "cutscene.takemtitans.beast.0"), new Cutscene.Line(100, 200, "cutscene.takemtitans.beast.1"))
        ),
        "finale",
        new Cutscene.Scene(
            340,
            List.of(
                new Cutscene.Key(0, 1.0, 2.2, 1.0, 5.0, 1.7, 7.0),
                new Cutscene.Key(110, -10.0, 16.0, -12.0, 0.0, 10.0, 30.0),
                new Cutscene.Key(220, -20.0, 55.0, -50.0, 60.0, 40.0, 60.0),
                new Cutscene.Key(340, 0.0, 95.0, -90.0, 220.0, 50.0, 0.0)
            ),
            List.of(
                new Cutscene.Line(10, 90, "cutscene.takemtitans.finale.0"),
                new Cutscene.Line(95, 175, "cutscene.takemtitans.finale.1"),
                new Cutscene.Line(180, 260, "cutscene.takemtitans.finale.2"),
                new Cutscene.Line(265, 340, "cutscene.takemtitans.finale.3")
            )
        ),
        "falling",
        new Cutscene.Scene(
            220,
            List.of(
                new Cutscene.Key(0, 0.0, 40.0, -80.0, 0.0, 30.0, 0.0),
                new Cutscene.Key(110, -20.0, 55.0, -40.0, 0.0, 20.0, 30.0),
                new Cutscene.Key(220, 0.0, 12.0, -8.0, 0.0, 4.0, 0.0)
            ),
            List.of(
                new Cutscene.Line(8, 90, "cutscene.takemtitans.falling.0"),
                new Cutscene.Line(95, 170, "cutscene.takemtitans.falling.1"),
                new Cutscene.Line(175, 220, "cutscene.takemtitans.falling.2")
            )
        ),
        "ravager",
        new Cutscene.Scene(
            200,
            List.of(
                new Cutscene.Key(0, 18.0, 6.0, 42.0, 0.0, 14.0, 0.0),
                new Cutscene.Key(100, -10.0, 22.0, 28.0, 0.0, 18.0, 0.0),
                new Cutscene.Key(200, 4.0, 10.0, 18.0, 0.0, 16.0, 0.0)
            ),
            List.of(new Cutscene.Line(12, 95, "cutscene.takemtitans.ravager.0"), new Cutscene.Line(100, 200, "cutscene.takemtitans.ravager.1"))
        ),
        "karanes",
        new Cutscene.Scene(
            220,
            List.of(
                new Cutscene.Key(0, 0.0, 60.0, -90.0, 0.0, 40.0, 0.0),
                new Cutscene.Key(110, 30.0, 40.0, -50.0, 0.0, 20.0, 20.0),
                new Cutscene.Key(220, 0.0, 10.0, -6.0, 0.0, 4.0, 0.0)
            ),
            List.of(
                new Cutscene.Line(8, 90, "cutscene.takemtitans.karanes.0"),
                new Cutscene.Line(95, 170, "cutscene.takemtitans.karanes.1"),
                new Cutscene.Line(175, 220, "cutscene.takemtitans.karanes.2")
            )
        ),
        "krolva",
        new Cutscene.Scene(
            220,
            List.of(
                new Cutscene.Key(0, -10.0, 50.0, -70.0, 0.0, 30.0, 0.0),
                new Cutscene.Key(110, -25.0, 60.0, -30.0, 0.0, 22.0, -30.0),
                new Cutscene.Key(220, 0.0, 12.0, -8.0, 0.0, 4.0, 0.0)
            ),
            List.of(
                new Cutscene.Line(8, 90, "cutscene.takemtitans.krolva.0"),
                new Cutscene.Line(95, 170, "cutscene.takemtitans.krolva.1"),
                new Cutscene.Line(175, 220, "cutscene.takemtitans.krolva.2")
            )
        ),
        "epilogue",
        new Cutscene.Scene(
            280,
            List.of(
                new Cutscene.Key(0, 0.0, 3.0, -6.0, 0.0, 3.0, 180.0),
                new Cutscene.Key(120, 12.0, 28.0, -24.0, 0.0, 8.0, 150.0),
                new Cutscene.Key(280, 0.0, 4.0, 2.0, 0.0, 2.0, 180.0)
            ),
            List.of(
                new Cutscene.Line(10, 100, "cutscene.takemtitans.epilogue.0"),
                new Cutscene.Line(105, 190, "cutscene.takemtitans.epilogue.1"),
                new Cutscene.Line(195, 280, "cutscene.takemtitans.epilogue.2")
            )
        )
    );
    @Nullable
    private static Cutscene.Scene scene;
    private static int age;
    private static Vec3 anchor = Vec3.ZERO;
    private static float yaw;
    @Nullable
    private static Entity cam;
    @Nullable
    private static CameraType prevType;
    private static float camYaw;

    private Cutscene() {
    }

    public static boolean active() {
        return scene != null;
    }

    static void start(String id, Vec3 at, float anchorYaw) {
        Cutscene.Scene s = SCENES.containsKey(id) ? SCENES.get(id) : MORE.get(id);
        Minecraft mc = Minecraft.getInstance();
        if (s != null && mc.level != null && mc.player != null) {
            if (mc.screen != null) {
                mc.setScreen(null);
            }

            stop();
            scene = s;
            age = 0;
            anchor = at;
            yaw = anchorYaw;
            cam = new Marker(EntityType.MARKER, mc.level);
            prevType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            place(0, true);
            mc.setCameraEntity(cam);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CUTSCENE_STING.get(), 1.0F, 1.0F));
        }
    }

    static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (scene != null) {
            scene = null;
            if (mc.player != null) {
                mc.setCameraEntity(mc.player);
            }

            if (prevType != null) {
                mc.options.setCameraType(prevType);
            }

            prevType = null;
            cam = null;
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (scene != null) {
            if (mc.player == null || mc.level == null || cam == null || cam.level() != mc.level) {
                stop();
            } else if (!mc.isPaused()) {
                age++;
                boolean skip = age > 20 && mc.options.keyJump.consumeClick();
                if (age < scene.length && !skip) {
                    place(age, false);
                    if (mc.getCameraEntity() != cam) {
                        mc.setCameraEntity(cam);
                    }
                } else {
                    if (skip) {
                        PacketDistributor.sendToServer(new StoryActionPayload(-1, 99));
                    }

                    stop();
                }
            }
        }
    }

    private static Vec3 toWorld(double x, double y, double z) {
        float r = yaw * 0.017453292F;
        double fx = (double)(-Mth.sin(r));
        double fz = (double)Mth.cos(r);
        double rx = (double)(-Mth.cos(r));
        double rz = (double)(-Mth.sin(r));
        return anchor.add(rx * x + fx * z, y, rz * x + fz * z);
    }

    private static void place(int t, boolean snap) {
        List<Cutscene.Key> k = scene.keys;
        int i = 0;

        while (i < k.size() - 2 && t > k.get(i + 1).t) {
            i++;
        }

        Cutscene.Key a = k.get(i);
        Cutscene.Key b = k.get(Math.min(i + 1, k.size() - 1));
        Cutscene.Key p0 = k.get(Math.max(i - 1, 0));
        Cutscene.Key p3 = k.get(Math.min(i + 2, k.size() - 1));
        double u = b.t == a.t ? 1.0 : Mth.clamp((double)(t - a.t) / (double)(b.t - a.t), 0.0, 1.0);
        u = u * u * (3.0 - 2.0 * u) * 0.5 + u * 0.5;
        Vec3 pos = toWorld(cr(p0.x, a.x, b.x, p3.x, u), cr(p0.y, a.y, b.y, p3.y, u), cr(p0.z, a.z, b.z, p3.z, u));
        Vec3 look = toWorld(cr(p0.lx, a.lx, b.lx, p3.lx, u), cr(p0.ly, a.ly, b.ly, p3.ly, u), cr(p0.lz, a.lz, b.lz, p3.lz, u));
        Vec3 d = look.subtract(pos);
        float ny = (float)(Mth.atan2(d.z, d.x) * 180.0 / 3.1415927410125732) - 90.0F;
        float np = (float)(-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0 / 3.1415927410125732));
        ny = camYaw + Mth.wrapDegrees(ny - camYaw);
        if (snap) {
            cam.setPos(pos);
            cam.xo = pos.x;
            cam.yo = pos.y;
            cam.zo = pos.z;
            cam.setYRot(ny);
            cam.yRotO = ny;
            cam.setXRot(np);
            cam.xRotO = np;
        } else {
            cam.xo = cam.getX();
            cam.yo = cam.getY();
            cam.zo = cam.getZ();
            cam.yRotO = cam.getYRot();
            cam.xRotO = cam.getXRot();
            cam.setPos(pos);
            cam.setYRot(ny);
            cam.setXRot(np);
        }

        camYaw = ny;
    }

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * (2.0 * p1 + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (scene != null) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            int sw = g.guiWidth();
            int sh = g.guiHeight();
            float a = (float)age + delta.getGameTimeDeltaPartialTick(false);
            float in = Mth.clamp(a / 12.0F, 0.0F, 1.0F) * Mth.clamp(((float)scene.length - a) / 12.0F, 0.0F, 1.0F);
            int bar = (int)((float)sh * 0.11F * in);
            g.fill(0, 0, sw, bar, -16777216);
            g.fill(0, sh - bar, sw, sh, -16777216);

            for (Cutscene.Line l : scene.lines) {
                if (!(a < (float)l.from) && !(a > (float)l.to)) {
                    float la = Mth.clamp(Math.min((a - (float)l.from) / 8.0F, ((float)l.to - a) / 8.0F), 0.0F, 1.0F);
                    if (!(la < 0.05F)) {
                        int col = (int)(la * 255.0F) << 24 | 16051932;
                        List<FormattedCharSequence> parts = font.split(Component.translatable(l.key), (int)((float)sw * 0.8F));
                        int y = sh - bar / 2 - parts.size() * 6;

                        for (FormattedCharSequence s : parts) {
                            g.drawString(font, s, sw / 2 - font.width(s) / 2, y, col, true);
                            y += 12;
                        }
                    }
                }
            }

            if (age > 20) {
                Component hint = Component.translatable("cutscene.takemtitans.skip", mc.options.keyJump.getTranslatedKeyMessage());
                g.drawString(font, hint, sw - 8 - font.width(hint), 6, -2130706433, false);
            }

            float black = Math.max(1.0F - a / 10.0F, 1.0F - ((float)scene.length - a) / 10.0F);
            if (black > 0.0F) {
                g.fill(0, 0, sw, sh, (int)(Mth.clamp(black, 0.0F, 1.0F) * 255.0F) << 24);
            }
        }
    }

    @SubscribeEvent(
        priority = EventPriority.HIGHEST
    )
    public static void onLayer(Pre event) {
        if (scene != null && !event.getName().equals(LAYER)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(
        priority = EventPriority.HIGHEST
    )
    public static void onHand(RenderHandEvent event) {
        if (scene != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (scene != null) {
            Input in = event.getInput();
            in.leftImpulse = 0.0F;
            in.forwardImpulse = 0.0F;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onInteract(InteractionKeyMappingTriggered event) {
        if (scene != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    static record Key(int t, double x, double y, double z, double lx, double ly, double lz) {
    }

    static record Line(int from, int to, String key) {
    }

    static record Scene(int length, List<Cutscene.Key> keys, List<Cutscene.Line> lines) {
    }
}
