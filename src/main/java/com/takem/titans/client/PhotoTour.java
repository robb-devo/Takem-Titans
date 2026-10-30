package com.takem.titans.client;

import com.takem.titans.TakemTitans;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

/**
 * Dev tool, off unless the game is started with {@code -Dtakemtitans.tour=<script>} (gradle runClient -Ptour=...).
 * Plays a script once the world is loaded: server commands, waits and screenshots, so a change to towns, titans or
 * effects can be looked at without anyone at the keyboard. One step per line:
 * <pre>
 * cmd &lt;command&gt;      run as the server, op level 4 (use "execute as @p at @p run ..." for the player)
 * wait &lt;ticks&gt;
 * settle &lt;ticks&gt;     wait at least 40 ticks, then until the world has finished drawing, at most &lt;ticks&gt;
 * shot &lt;name&gt;       screenshots/&lt;name&gt;.png
 * hud on|off
 * camera first|back|front
 * panorama &lt;size&gt;  the six title-screen panorama faces
 * fx &lt;kind&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;a&gt; &lt;b&gt; &lt;c&gt; &lt;d&gt;  a client effect, as if the server sent it
 * quit
 * </pre>
 */
@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class PhotoTour {
    private static final String SCRIPT = System.getProperty("takemtitans.tour");
    private static List<String> steps;
    private static int index;
    private static int waitTicks;
    private static int settleMax;
    private static int settleTicks;
    private static int inWorld;

    /** -Dtakemtitans.tourMenu=true: no world; screenshots of the title screen and the adventure screen instead. */
    private static final boolean MENU = Boolean.getBoolean("takemtitans.tourMenu");
    private static int menuTicks;

    private PhotoTour() {
    }

    private static void menuTick(Minecraft mc) {
        menuTicks++;
        if (menuTicks == 200) {
            Screenshot.grab(mc.gameDirectory, "menu_title.png", mc.getMainRenderTarget(), msg -> {});
        } else if (menuTicks == 220 && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen title) {
            mc.setScreen(new AdventureScreen(title));
        } else if (menuTicks == 260) {
            Screenshot.grab(mc.gameDirectory, "menu_adventure.png", mc.getMainRenderTarget(), msg -> {});
        } else if (menuTicks == 280) {
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        if (SCRIPT == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (MENU) {
            menuTick(mc);
            return;
        }

        if (mc.player == null || mc.level == null) {
            inWorld = 0;
            return;
        }

        if (steps == null) {
            try {
                steps = new ArrayList<>();
                for (String line : Files.readAllLines(Path.of(SCRIPT))) {
                    line = line.strip();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        steps.add(line);
                    }
                }
            } catch (IOException e) {
                TakemTitans.LOGGER.error("photo tour: can't read {}", SCRIPT, e);
                steps = List.of();
            }
        }

        // the story opens dialogues and cutscenes on a fresh world; the tour looks past them
        if (mc.screen != null) {
            mc.setScreen(null);
        }

        if (++inWorld < 60) {
            return;
        }

        if (settleMax > 0) {
            settleTicks++;
            if (settleTicks >= settleMax || settleTicks >= 40 && mc.levelRenderer.hasRenderedAllSections()) {
                settleMax = 0;
            }

            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        while (index < steps.size()) {
            String line = steps.get(index++);
            String arg = line.indexOf(' ') < 0 ? "" : line.substring(line.indexOf(' ') + 1).strip();
            TakemTitans.LOGGER.info("photo tour: {}", line);
            switch (line.split(" ", 2)[0]) {
                case "cmd" -> {
                    MinecraftServer server = mc.getSingleplayerServer();
                    if (server != null) {
                        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), arg));
                    }
                }
                case "wait" -> {
                    waitTicks = Integer.parseInt(arg);
                    return;
                }
                case "settle" -> {
                    settleMax = Integer.parseInt(arg);
                    settleTicks = 0;
                    return;
                }
                case "shot" -> {
                    Screenshot.grab(mc.gameDirectory, arg + ".png", mc.getMainRenderTarget(), msg -> {});
                    // one picture per tick: the next one should show a fresh frame
                    return;
                }
                case "hud" -> mc.options.hideGui = arg.equals("off");
                case "camera" -> mc.options.setCameraType(switch (arg) {
                    case "front" -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
                    case "back" -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
                    default -> net.minecraft.client.CameraType.FIRST_PERSON;
                });
                case "panorama" -> {
                    // the six faces of a title-screen panorama, as screenshots/panorama_0..5.png
                    int size = Integer.parseInt(arg);
                    mc.grabPanoramixScreenshot(mc.gameDirectory, size, size);
                    return;
                }
                case "fx" -> {
                    // fx <kind> <x> <y> <z> <a> <b> <c> <d>: plays a client effect as if the server had sent it
                    String[] p = arg.split(" ");
                    com.takem.titans.client.fx.ClientFx.handle(
                        new com.takem.titans.network.FxPayload(
                            Integer.parseInt(p[0]),
                            Double.parseDouble(p[1]),
                            Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]),
                            Float.parseFloat(p[4]),
                            Float.parseFloat(p[5]),
                            Float.parseFloat(p[6]),
                            Float.parseFloat(p[7])
                        ),
                        null
                    );
                }
                case "quit" -> {
                    mc.stop();
                    return;
                }
                default -> TakemTitans.LOGGER.warn("photo tour: unknown step {}", line);
            }
        }
    }
}
