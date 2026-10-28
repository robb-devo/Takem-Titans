package com.takem.titans.client.story;

import com.mojang.math.Axis;
import com.takem.titans.TakemTitans;
import com.takem.titans.client.gui.TTGui;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.ArmoredTitan;
import com.takem.titans.entity.titan.BeastTitan;
import com.takem.titans.network.StoryPayload;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.ClientSkills;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(
    modid = "takemtitans",
    value = {Dist.CLIENT}
)
public final class StoryClient {
    public static final ResourceLocation HUD = TakemTitans.id("story_hud");
    static boolean has;
    public static int wallet;
    static final List<CompoundTag> SQUAD = new ArrayList<>();
    static boolean squadHold;
    static int walletGain;
    static long walletGainAt;
    static String town;
    static int townPending;
    static int townInitial;
    static int townState;
    static long townEta;
    static int townPct;
    static int townCost;
    static int townFort;
    static String raidZone;
    static int raidWave;
    static int raidWaves;
    static int raidLeft;
    static int raidPhase;
    static int raidTimer;
    static int raidPct;
    static int xp;
    static int level = 1;
    static int needXp = 100;
    static int stage;
    static int count;
    static int need;
    static String rank = "rank.takemtitans.trainee";
    static String chapter = "";
    static String obj = "";
    static boolean hasTarget;
    static double tx;
    static double tz;
    private static float shownXp = -1.0F;
    private static final Deque<StoryClient.Banner> BANNERS = new ArrayDeque<>();
    @Nullable
    private static StoryClient.Banner banner;
    private static int bannerAge;
    private static final List<StoryClient.Pop> POPS = new ArrayList<>();
    @Nullable
    private static StoryClient.BossMusic music;

    private StoryClient() {
    }

    public static void handle(StoryPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> handleTag(msg.tag()));
    }

    static Component parse(String json) {
        Minecraft mc = Minecraft.getInstance();

        try {
            Component c = mc.level != null ? Serializer.fromJson(json, mc.level.registryAccess()) : null;
            return (Component)(c != null ? c : Component.empty());
        } catch (Exception var31) {
            return Component.literal(json);
        }
    }

    private static void handleTag(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        String var2 = tag.getString("t");
        switch (var2) {
            case "waystones":
                mc.setScreen(new com.takem.titans.client.gui.WaystoneScreen(tag));
                break;
            case "fog":
                com.takem.titans.client.fx.NightFog.handle(tag);
                break;
            case "tunnelfog":
                com.takem.titans.client.fx.NightFog.tunnel(tag.getBoolean("on"));
                break;
            case "sync":
                has = true;
                xp = tag.getInt("xp");
                level = tag.getInt("level");
                needXp = Math.max(1, tag.getInt("needXp"));
                rank = tag.getString("rank");
                stage = tag.getInt("stage");
                chapter = tag.getString("chapter");
                obj = tag.getString("obj");
                count = tag.getInt("count");
                need = tag.getInt("need");
                hasTarget = tag.getBoolean("hasT");
                tx = tag.getDouble("tx");
                tz = tag.getDouble("tz");
                if (tag.contains("sk")) {
                    ClientSkills.set(tag.getByteArray("sk"), tag.getInt("pts"), tag.getInt("earned"), level);
                }

                if (tag.contains("wallet")) {
                    wallet = tag.getInt("wallet");
                }

                if (tag.contains("squad")) {
                    ClientSkills.squad = tag.getInt("squad");
                }
                break;
            case "raid":
                raidZone = tag.contains("zone") ? tag.getString("zone") : null;
                raidWave = tag.getInt("wave");
                raidWaves = tag.getInt("waves");
                raidLeft = tag.getInt("left");
                raidPhase = tag.getInt("phase");
                raidTimer = tag.getInt("timer");
                raidPct = tag.getInt("pct");
                break;
            case "squad":
                SQUAD.clear();

                for (Tag t : tag.getList("m", 10)) {
                    SQUAD.add((CompoundTag)t);
                }

                squadHold = tag.getBoolean("hold");
                break;
            case "wallet":
                wallet = tag.getInt("wallet");
                if (tag.getInt("gain") > 0) {
                    walletGain = (Util.getMillis() - walletGainAt < 2500L ? walletGain : 0) + tag.getInt("gain");
                    walletGainAt = Util.getMillis();
                }
                break;
            case "town":
                town = tag.contains("zone") ? tag.getString("zone") : null;
                townPending = tag.getInt("pending");
                townInitial = Math.max(1, tag.getInt("initial"));
                townState = tag.getInt("state");
                townEta = tag.getLong("eta");
                townPct = tag.getInt("pct");
                townCost = tag.getInt("cost");
                townFort = tag.getInt("fort");
                break;
            case "shop":
                wallet = tag.getInt("wallet");
                mc.setScreen(new ShopScreen(tag.getInt("npc"), parse(tag.getString("name"))));
                break;
            case "banner":
                String kind = tag.getString("kind");
                Component title = parse(tag.getString("title"));
                if (kind.equals("xp")) {
                    POPS.add(new StoryClient.Pop(title));
                    if (POPS.size() > 5) {
                        POPS.remove(0);
                    }
                } else if (kind.equals("streak")) {
                    BANNERS.removeIf(b -> b.kind.equals("streak"));
                    BANNERS.addFirst(new StoryClient.Banner(kind, title, parse(tag.getString("sub"))));
                } else {
                    BANNERS.add(new StoryClient.Banner(kind, title, parse(tag.getString("sub"))));
                }
                break;
            case "dialogue":
                List<Component> lines = new ArrayList<>();

                for (Tag t : tag.getList("lines", 8)) {
                    lines.add(parse(t.getAsString()));
                }

                List<Integer> ids = new ArrayList<>();
                List<Component> labels = new ArrayList<>();
                ListTag os = tag.getList("opts", 10);

                for (int i = 0; i < os.size(); i++) {
                    ids.add(os.getCompound(i).getInt("id"));
                    labels.add(parse(os.getCompound(i).getString("label")));
                }

                if (!Cutscene.active()) {
                    mc.setScreen(new DialogueScreen(tag.getInt("npc"), parse(tag.getString("speaker")), tag.getInt("role"), lines, ids, labels));
                }
                break;
            case "cutscene":
                Cutscene.start(tag.getString("id"), new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")), tag.getFloat("yaw"));
        }
    }

    @SubscribeEvent
    public static void onTick(Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            Cutscene.tick();
            if (!mc.isPaused()) {
                POPS.removeIf(p -> ++p.age > 50);
                if (!Cutscene.active()) {
                    if (banner == null && !BANNERS.isEmpty()) {
                        banner = BANNERS.poll();
                        bannerAge = 0;
                        String var3 = banner.kind;

                        SoundEvent sound = switch (var3) {
                            case "rank" -> (SoundEvent)ModSounds.LEVEL_UP.get();
                            case "chapter" -> (SoundEvent)ModSounds.CUTSCENE_STING.get();
                            default -> (SoundEvent)ModSounds.QUEST_COMPLETE.get();
                        };
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 0.8F));
                    }

                    if (banner != null && ++bannerAge > bannerLength(banner)) {
                        banner = null;
                    }
                }

                tickMusic(mc);
            }
        } else {
            has = false;
            BANNERS.clear();
            banner = null;
            POPS.clear();
            Cutscene.stop();
        }
    }

    private static int bannerLength(StoryClient.Banner b) {
        return !b.kind.equals("chapter") && !b.kind.equals("story") ? 80 : 110;
    }

    @Nullable
    public static BlockPos beamTarget() {
        Player p = Minecraft.getInstance().player;
        if (has && hasTarget && p != null && !Cutscene.active()) {
            double dx = tx - p.getX();
            double dz = tz - p.getZ();
            return dx * dx + dz * dz < 400.0 ? null : BlockPos.containing(tx, 0.0, tz);
        } else {
            return null;
        }
    }

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.hideGui && mc.player != null && !Cutscene.active() && !mc.getDebugOverlay().showDebugScreen()) {
            Font font = mc.font;
            float pt = delta.getGameTimeDeltaPartialTick(false);
            int sw = g.guiWidth();
            if (has) {
                renderTracker(g, font, mc.player, pt);
            }

            renderWallet(g, font, has ? 54 : 6);
            renderSquad(g, font, sw);
            if (raidZone != null) {
                renderRaid(g, font, sw);
            }

            if (town != null) {
                renderTown(g, font, has ? 76 : 28);
            }

            renderPops(g, font, pt);
            if (banner != null) {
                renderBanner(g, font, sw, g.guiHeight(), pt);
            }
        }
    }

    public static boolean questTargetNear(double x, double z, double r) {
        return has && hasTarget && (tx - x) * (tx - x) + (tz - z) * (tz - z) < r * r;
    }

    private static void renderRaid(GuiGraphics g, Font font, int sw) {
        Component top = Component.translatable("raid.takemtitans.hud", Component.translatable("zone.takemtitans." + raidZone), raidWave, raidWaves);
        Component sub = raidPhase == 0
            ? Component.translatable("raid.takemtitans.hud_warning", (raidTimer + 19) / 20)
            : (
                raidPhase == 2
                    ? Component.translatable("raid.takemtitans.hud_between", (raidTimer + 19) / 20)
                    : Component.translatable("raid.takemtitans.hud_left", raidLeft, raidPct)
            );
        int w = Math.max(font.width(top), font.width(sub)) + 24;
        int x = (sw - w) / 2;
        int y = 46;
        TTGui.hudPanel(g, x, y, w, 30);
        long ms = Util.getMillis();
        int pulse = (int)(128.0 + 127.0 * Math.sin((double)ms / 180.0));
        g.drawCenteredString(font, top, sw / 2, y + 5, -65536 | pulse / 3 << 8 | pulse / 4);
        g.drawCenteredString(font, sub, sw / 2, y + 17, -1517392);
    }

    private static void renderSquad(GuiGraphics g, Font font, int sw) {
        if (!SQUAD.isEmpty()) {
            int w = 104;
            int x = sw - w - 4;
            int y = 40;
            TTGui.hudPanel(g, x - 2, y - 14, w + 4, 14);
            g.drawString(font, Component.translatable(squadHold ? "hud.takemtitans.squad_hold" : "hud.takemtitans.squad_follow"), x + 3, y - 10, -997256, false);

            for (CompoundTag m : SQUAD) {
                float hp = m.getFloat("h");
                TTGui.hudPanel(g, x - 2, y, w + 4, 22);
                g.drawString(font, m.getString("n"), x + 3, y + 3, hp < 0.0F ? -7305096 : -6492048, false);
                String lv = "Lv " + m.getInt("l");
                g.drawString(font, lv, x + w - 3 - font.width(lv), y + 3, -8080, false);
                g.fill(x + 3, y + 13, x + w - 3, y + 16, -1609560044);
                if (hp >= 0.0F) {
                    int col = hp > 0.5F ? -10432432 : (hp > 0.25F ? -2047936 : -2080720);
                    g.fill(x + 3, y + 13, x + 3 + (int)((float)(w - 6) * hp), y + 16, col);
                }

                g.fill(x + 3, y + 17, x + 3 + (int)((float)(w - 6) * m.getFloat("x")), y + 18, -10444560);
                y += 24;
            }
        }
    }

    private static void renderWallet(GuiGraphics g, Font font, int y) {
        String n = String.valueOf(wallet);
        int w = 30 + font.width(n);
        TTGui.hudPanel(g, 1, y - 3, w, 20);
        g.pose().pushPose();
        g.pose().translate(4.0F, (float)(y - 1), 0.0F);
        g.pose().scale(0.9F, 0.9F, 1.0F);
        g.renderItem(new ItemStack(ModItems.MERIT_BADGE.get()), 0, 0);
        g.pose().popPose();
        g.drawString(font, n, 22, y + 3, -8080, true);
        long age = Util.getMillis() - walletGainAt;
        if (walletGain > 0 && age < 2500L) {
            int a = (int)(255.0F * Math.min(1.0F, (2500.0F - (float)age) / 600.0F));
            int rise = (int)(age / 120L);
            g.drawString(font, "+" + walletGain, w + 4, y + 3 - Math.min(6, rise), Math.max(4, a) << 24 | 10285168, true);
        }
    }

    private static void renderTown(GuiGraphics g, Font font, int y) {
        Component name = Component.translatable("zone.takemtitans." + town);

        Component line = switch (townState) {
            case 0 -> Component.translatable("hud.takemtitans.town_damaged", townPct);
            case 1 -> Component.translatable("hud.takemtitans.town_paused");
            default -> Component.translatable(
            "hud.takemtitans.town_rebuild", Math.round(100.0F * (1.0F - (float)townPending / (float)townInitial)), eta(townEta)
        );
            case 3 -> Component.translatable("hud.takemtitans.town_fallen", townCost);
        };
        int w = Math.max(font.width(name), font.width(line)) + 12;
        TTGui.hudPanel(g, 1, y - 3, w, 32);
        g.drawString(
            font, (Component)(townFort > 0 ? name.copy().append(" " + "★".repeat(townFort)) : name), 6, y + 1, townState == 3 ? -2080720 : -997256, true
        );
        g.drawString(font, line, 6, y + 11, townState == 1 ? -1023920 : -2568000, false);
        float k = townState == 2 ? 1.0F - (float)townPending / (float)townInitial : (townState == 3 ? 1.0F : (float)townPct / 100.0F);
        g.fill(6, y + 21, w - 4, y + 24, -1609560044);
        g.fill(6, y + 21, 6 + (int)((float)(w - 10) * k), y + 24, townState == 2 ? -8597408 : -3121088);
    }

    private static String eta(long ticks) {
        long h = Math.max(0L, ticks) / 1000L;
        long m = Math.max(0L, ticks) % 1000L * 60L / 1000L;
        return h >= 24L ? h / 24L + "d " + h % 24L + "h" : (h > 0L ? h + "h " + m + "m" : m + "m");
    }

    /** Count, need and then the player's own keys, in the order the training lines use them. */
    private static Object[] trainingArgs() {
        String[] keys = new String[]{
            "key.takemtitans.hook_left",
            "key.takemtitans.hook_right",
            "key.takemtitans.boost",
            "key.takemtitans.dash",
            "key.attack",
            "key.takemtitans.reload",
            "key.use",
            "key.sneak"
        };
        Object[] args = new Object[keys.length + 2];
        args[0] = count;
        args[1] = need;

        for (int i = 0; i < keys.length; i++) {
            args[i + 2] = Component.keybind(keys[i]).withStyle(net.minecraft.ChatFormatting.YELLOW);
        }

        return args;
    }

    private static void renderTracker(GuiGraphics g, Font font, Player p, float pt) {
        int x = 6;
        int y = 6;
        Component ch = Component.translatable(chapter);
        Component ob = obj.startsWith("training.") ? Component.translatable(obj, trainingArgs()) : Component.translatable(obj, count, need);
        String dist = "";
        float rel = 0.0F;
        if (hasTarget) {
            double dx = tx - p.getX();
            double dz = tz - p.getZ();
            dist = Math.round(Math.sqrt(dx * dx + dz * dz)) + " m";
            float yaw = (float)(Mth.atan2(dz, dx) * 180.0 / 3.1415927410125732) - 90.0F;
            rel = Mth.wrapDegrees(yaw - p.getViewYRot(pt));
        }

        int w = Math.max(Math.max((int)((float)font.width(ch) * 0.75F), font.width(ob) + (hasTarget ? 16 : 0)), 120);
        int h = 41;
        TTGui.hudPanel(g, x - 5, y - 5, w + 12, h + 7);
        g.pose().pushPose();
        g.pose().translate((float)(x + 2), (float)y, 0.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, ch, 0, 0, -1523614, false);
        g.pose().popPose();
        g.drawString(font, ob, x + 2, y + 9, -1, true);
        if (hasTarget) {
            int ax = x + w - 4;
            int ay = y + 13;
            g.pose().pushPose();
            g.pose().translate((float)ax, (float)ay, 0.0F);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
            g.drawString(font, "▲", -font.width("▲") / 2, -4, -11686, false);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate((float)(x + 2), (float)(y + 19), 0.0F);
            g.pose().scale(0.7F, 0.7F, 1.0F);
            g.drawString(font, dist, 0, 0, -4668212, false);
            g.pose().popPose();
        }

        int ry = y + 27;
        g.pose().pushPose();
        g.pose().translate((float)(x + 2), (float)ry, 0.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable("story.takemtitans.hud_rank", level, Component.translatable(rank)), 0, 0, -3152912, false);
        g.pose().popPose();
        float target = (float)xp / (float)needXp;
        if (shownXp < 0.0F || target < shownXp - 0.01F) {
            shownXp = target;
        }

        shownXp = shownXp + (target - shownXp) * 0.15F;
        g.fill(x + 2, ry + 9, x + 2 + w, ry + 11, -14670292);
        g.fill(x + 2, ry + 9, x + 2 + (int)((float)w * Mth.clamp(shownXp, 0.0F, 1.0F)), ry + 11, -9448449);
    }

    private static void renderPops(GuiGraphics g, Font font, float pt) {
        int i = 0;

        for (StoryClient.Pop p : POPS) {
            float a = (float)p.age + pt;
            float alpha = Mth.clamp(a < 6.0F ? a / 6.0F : (50.0F - a) / 15.0F, 0.0F, 1.0F);
            int col = (int)(alpha * 255.0F) << 24 | 9430015;
            if (!(alpha < 0.05F)) {
                g.drawString(font, p.text, 140 + i * 2, (int)(40.0F - a * 0.4F), col, true);
                i++;
            }
        }
    }

    private static void renderBanner(GuiGraphics g, Font font, int sw, int sh, float pt) {
        StoryClient.Banner b = banner;
        float a = (float)bannerAge + pt;
        int len = bannerLength(b);
        float alpha = Mth.clamp(Math.min(a / 8.0F, ((float)len - a) / 12.0F), 0.0F, 1.0F);
        if (!(alpha <= 0.02F)) {
            int cy = (int)((float)sh * 0.24F);
            float grow = Mth.clamp(a / 10.0F, 0.0F, 1.0F);
            int half = (int)((float)sw * 0.32F * (0.4F + 0.6F * (1.0F - (1.0F - grow) * (1.0F - grow))));
            int bgA = (int)(alpha * 160.0F);
            int lineCol = (int)(alpha * 255.0F) << 24 | (b.kind.equals("rank") ? 7328767 : 14725184);
            g.fill(sw / 2 - half, cy - 14, sw / 2 + half, cy + 22, bgA << 24 | 658706);
            g.fill(sw / 2 - half, cy - 14, sw / 2 + half, cy - 13, lineCol);
            g.fill(sw / 2 - half, cy + 21, sw / 2 + half, cy + 22, lineCol);
            float scale = !b.kind.equals("chapter") && !b.kind.equals("story") ? 1.6F : 2.0F;
            int col = (int)(alpha * 255.0F) << 24 | (b.kind.equals("rank") ? 12578815 : 16770728);
            g.pose().pushPose();
            g.pose().translate((float)sw / 2.0F, (float)(cy - 9), 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, b.title, -font.width(b.title) / 2, 0, col, true);
            g.pose().popPose();
            if (b.sub != null && !b.sub.getString().isEmpty()) {
                int sc = (int)(alpha * 255.0F) << 24 | 15265522;
                g.drawString(font, b.sub, sw / 2 - font.width(b.sub) / 2, cy + 10, sc, true);
            }
        }
    }

    private static void tickMusic(Minecraft mc) {
        boolean boss = bossNear(mc, 120.0);
        if (boss && (music == null || music.isStopped()) && !mc.getSoundManager().isActive(music)) {
            music = new StoryClient.BossMusic();
            mc.getMusicManager().stopPlaying();
            mc.getSoundManager().play(music);
        }
    }

    static boolean bossNear(Minecraft mc, double r) {
        if (mc.player != null && mc.level != null) {
            for (AbstractTitan t : mc.level.getEntitiesOfClass(AbstractTitan.class, mc.player.getBoundingBox().inflate(r))) {
                if ((t instanceof ArmoredTitan || t instanceof BeastTitan) && t.isAlive()) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private static record Banner(String kind, Component title, Component sub) {
    }

    private static final class BossMusic extends AbstractTickableSoundInstance {
        private float fade;
        private boolean out;

        BossMusic() {
            super(ModSounds.BOSS_MUSIC.get(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (!this.out && !StoryClient.bossNear(mc, 150.0)) {
                this.out = true;
            }

            this.fade = Mth.clamp(this.fade + (this.out ? -0.01F : 0.02F), 0.0F, 1.0F);
            this.volume = Math.max(0.001F, this.fade * 0.9F);
            if (this.out && this.fade <= 0.0F) {
                this.stop();
            }
        }
    }

    private static final class Pop {
        final Component text;
        int age;

        Pop(Component text) {
            this.text = text;
        }
    }
}
