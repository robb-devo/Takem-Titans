package com.takem.titans.story;

import com.takem.titans.TakemTitans;
import com.takem.titans.entity.npc.JaegerEntity;
import com.takem.titans.entity.npc.TownsfolkEntity;
import com.takem.titans.entity.titan.AbnormalTitan;
import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.entity.titan.ArmoredTitan;
import com.takem.titans.entity.titan.BeastTitan;
import com.takem.titans.entity.titan.CrawlerTitan;
import com.takem.titans.entity.titan.RavagerTitan;
import com.takem.titans.entity.titan.WallTitan;
import com.takem.titans.network.FeedbackPayload;
import com.takem.titans.network.StoryActionPayload;
import com.takem.titans.network.StoryPayload;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModItems;
import com.takem.titans.registry.ModSounds;
import com.takem.titans.skill.Skill;
import com.takem.titans.skill.Skills;
import com.takem.titans.world.Rebuild;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class Story {
    public static final int MAX_LEVEL = 40;
    private static final int[] RANK_LEVELS = new int[]{1, 3, 5, 8, 12, 16, 20, 25, 30, 40};
    private static final String[] RANKS = new String[]{
        "trainee", "cadet", "private", "corporal", "sergeant", "squad_leader", "captain", "commander", "strongest", "legend"
    };
    private static final ResourceLocation HEALTH_ID = TakemTitans.id("rank_health");
    static final Vec3 FOREST = new Vec3(-560.0, 0.0, 520.0);
    static final Vec3 TROST = new Vec3(0.0, 0.0, -400.0);
    static final Vec3 ARMORED_SPAWN = new Vec3(0.5, 0.0, 150.5);
    static final Vec3 BEAST_SPAWN = new Vec3(340.5, 0.0, 430.5);
    static final Vec3 MITRAS = new Vec3(0.0, 0.0, -1060.0);
    static final Vec3 RAVAGER_SPAWN = new Vec3(180.5, 0.0, -240.5);
    static final Vec3 KARANES = new Vec3(681.0, 0.0, -1100.0);
    static final Vec3 KROLVA = new Vec3(-681.0, 0.0, -1100.0);
    static final Vec3 STOHESS = new Vec3(300.0, 0.0, -1100.0);
    static final Vec3 PIT = new Vec3(304.0, 0.0, -1103.0);
    static final Vec3 WALL_TITAN_SPAWN = new Vec3(226.9, 0.0, -1231.0);
    static final Story.Stage[] STAGES = new Story.Stage[]{
        new Story.Stage("report", Story.Obj.TALK, 1, 2, 5.5, -9.5, 0.0, 5, 50, 3, Story::none, "prologue"),
        new Story.Stage(
            "wings",
            Story.Obj.KILL,
            2,
            -1,
            0.0,
            0.0,
            0.0,
            0,
            150,
            5,
            () -> List.of(new ItemStack(ModItems.GAS_CANISTER.get(), 4), new ItemStack(ModItems.BLADE_PACK.get(), 2)),
            "ch1"
        ),
        new Story.Stage("debrief", Story.Obj.TALK, 1, 2, 5.5, -9.5, 0.0, 4, 50, 2, () -> List.of(new ItemStack(ModItems.HORSE_WHISTLE.get())), "ch1"),
        new Story.Stage("forest", Story.Obj.REACH, 1, -1, FOREST.x, FOREST.z, 170.0, 0, 100, 2, Story::none, "ch2"),
        new Story.Stage("forest_clear", Story.Obj.KILL_AREA, 4, -1, FOREST.x, FOREST.z, 360.0, 0, 250, 8, Story::none, "ch2"),
        new Story.Stage("mira", Story.Obj.TALK, 1, 4, FOREST.x, FOREST.z, 0.0, 5, 150, 5, Story::none, "ch2"),
        new Story.Stage("trost", Story.Obj.REACH, 1, -1, TROST.x, TROST.z, 160.0, 0, 100, 2, Story::none, "ch3"),
        new Story.Stage(
            "siege", Story.Obj.KILL_AREA, 8, -1, TROST.x, TROST.z, 260.0, 0, 500, 15, Story::none, "ch3"
        ),
        new Story.Stage(
            "hale",
            Story.Obj.TALK,
            1,
            5,
            5.5,
            -409.5,
            0.0,
            5,
            100,
            4,
            Story::none,
            "ch3"
        ),
        new Story.Stage(
            "armored",
            Story.Obj.BOSS,
            1,
            -1,
            ARMORED_SPAWN.x,
            ARMORED_SPAWN.z,
            0.0,
            0,
            900,
            25,
            Story::none,
            "ch4"
        ),
        new Story.Stage("debrief2", Story.Obj.TALK, 1, 2, 5.5, -9.5, 0.0, 5, 100, 4, Story::none, "ch4"),
        new Story.Stage(
            "beast",
            Story.Obj.BOSS,
            1,
            -1,
            BEAST_SPAWN.x,
            BEAST_SPAWN.z,
            0.0,
            0,
            1400,
            40,
            Story::none,
            "ch5"
        ),
        new Story.Stage("dawn", Story.Obj.TALK, 1, 2, 5.5, -9.5, 0.0, 6, 500, 20, Story::none, "ch5"),
        new Story.Stage(
            "vanguard",
            Story.Obj.TALK,
            1,
            2,
            5.5,
            -9.5,
            0.0,
            6,
            200,
            6,
            Story::none,
            "ch6"
        ),
        new Story.Stage(
            "squad_hunt", Story.Obj.KILL, 6, -1, 0.0, 0.0, 0.0, 0, 400, 10, () -> List.of(new ItemStack(ModItems.THERMOBARIC_CANISTER.get(), 3)), "ch6"
        ),
        new Story.Stage("hale2", Story.Obj.TALK, 1, 5, 5.5, -409.5, 0.0, 5, 150, 4, () -> List.of(new ItemStack(ModItems.CLUSTER_SPEAR.get(), 3)), "ch6"),
        new Story.Stage("mitras", Story.Obj.REACH, 1, -1, MITRAS.x, MITRAS.z, 190.0, 0, 120, 2, Story::none, "ch7"),
        new Story.Stage(
            "falling_sky",
            Story.Obj.CATCH,
            6,
            -1,
            MITRAS.x,
            MITRAS.z,
            320.0,
            0,
            500,
            12,
            () -> List.of(new ItemStack(ModItems.DEMOLITION_CHARGE.get(), 6)),
            "ch7"
        ),
        new Story.Stage("mitras_defend", Story.Obj.KILL_AREA, 10, -1, MITRAS.x, MITRAS.z, 320.0, 0, 700, 18, Story::none, "ch7"),
        new Story.Stage(
            "ravager",
            Story.Obj.BOSS,
            1,
            -1,
            RAVAGER_SPAWN.x,
            RAVAGER_SPAWN.z,
            0.0,
            0,
            1600,
            45,
            Story::none,
            "ch8"
        ),
        new Story.Stage("epilogue", Story.Obj.TALK, 1, 2, 5.5, -9.5, 0.0, 6, 600, 25, Story::none, "ch8"),
        new Story.Stage("outer_call", Story.Obj.TALK, 1, 2, 5.5, -9.5, 0.0, 5, 200, 5, () -> List.of(new ItemStack(ModItems.THUNDER_SPEAR.get(), 4)), "ch9"),
        new Story.Stage("karanes", Story.Obj.REACH, 1, -1, KARANES.x, KARANES.z, 150.0, 0, 150, 3, Story::none, "ch9"),
        new Story.Stage(
            "karanes_siege",
            Story.Obj.KILL_AREA,
            8,
            -1,
            KARANES.x,
            KARANES.z,
            260.0,
            0,
            700,
            16,
            () -> List.of(new ItemStack(ModItems.THERMOBARIC_CANISTER.get(), 3)),
            "ch9"
        ),
        new Story.Stage(
            "krolva_fall", Story.Obj.CATCH, 5, -1, KROLVA.x, KROLVA.z, 280.0, 0, 700, 16, () -> List.of(new ItemStack(ModItems.CLUSTER_SPEAR.get(), 3)), "ch9"
        ),
        new Story.Stage(
            "stohess_hunt",
            Story.Obj.KILL_AREA,
            6,
            -1,
            STOHESS.x,
            STOHESS.z,
            200.0,
            0,
            800,
            18,
            () -> List.of(new ItemStack(ModItems.DEMOLITION_CHARGE.get(), 8)),
            "ch9"
        ),
        new Story.Stage(
            "outer_report", Story.Obj.TALK, 1, 2, 5.5, -9.5, 0.0, 5, 900, 30, Story::none, "ch9"
        ),
        new Story.Stage(
            "tunnels_call",
            Story.Obj.TALK,
            1,
            2,
            5.5,
            -9.5,
            0.0,
            5,
            250,
            6,
            () -> List.of(
                    new ItemStack(ModItems.THUNDER_SPEAR.get(), 4),
                    new ItemStack(ModItems.DEMOLITION_CHARGE.get(), 4),
                    new ItemStack(ModItems.GAS_CANISTER.get(), 6)
                ),
            "ch10"
        ),
        new Story.Stage("tunnels", Story.Obj.REACH, 1, -1, PIT.x, PIT.z, 14.0, 0, 200, 4, Story::none, "ch10"),
        new Story.Stage(
            "tunnel_purge",
            Story.Obj.KILL_AREA,
            8,
            -1,
            PIT.x,
            PIT.z,
            250.0,
            0,
            800,
            18,
            () -> List.of(new ItemStack(ModItems.THERMOBARIC_CANISTER.get(), 2)),
            "ch10"
        ),
        new Story.Stage(
            "hearts",
            Story.Obj.HEARTS,
            3,
            -1,
            PIT.x,
            PIT.z,
            0.0,
            0,
            1200,
            30,
            Story::none,
            "ch10"
        ),
        new Story.Stage(
            "wall_titan",
            Story.Obj.BOSS,
            1,
            -1,
            WALL_TITAN_SPAWN.x,
            WALL_TITAN_SPAWN.z,
            0.0,
            0,
            2400,
            60,
            Story::none,
            "ch11"
        ),
        new Story.Stage("finale2", Story.Obj.TALK, 1, 2, 5.5, -9.5, 0.0, 6, 1000, 40, Story::none, "ch11"),
        new Story.Stage("contracts", Story.Obj.CONTRACTS, 0, 2, 5.5, -9.5, 0.0, 0, 0, 0, Story::none, "after")
    };
    public static final int SIEGE = 7;
    public static final int ARMORED = 9;
    public static final int BEAST = 11;
    public static final int DAWN = 12;
    public static final int ESCORT = 14;
    public static final int FALLING_SKY = 17;
    public static final int MITRAS_DEFEND = 18;
    public static final int RAVAGER = 19;
    public static final int EPILOGUE = 20;
    public static final int KARANES_SIEGE = 23;
    public static final int KROLVA_FALL = 24;
    public static final int STOHESS_HUNT = 25;
    public static final int OUTER_REPORT = 26;
    public static final int TUNNELS_CALL = 27;
    public static final int TUNNELS = 28;
    public static final int TUNNEL_PURGE = 29;
    public static final int HEARTS = 30;
    public static final int WALL_BOSS = 31;
    public static final int FINALE2 = 32;
    public static final int MIRA_STAGE = 5;
    public static final int LAST = STAGES.length - 1;

    private Story() {
    }

    public static int needFor(int level) {
        return 100 + 60 * (level - 1);
    }

    public static int rankIndex(int level) {
        int r = 0;

        for (int i = 0; i < RANK_LEVELS.length; i++) {
            if (level >= RANK_LEVELS[i]) {
                r = i;
            }
        }

        return r;
    }

    public static String rankKey(int level) {
        return "rank.takemtitans." + RANKS[rankIndex(level)];
    }

    /** Titan threat for a player's place in the story: 0 prologue, 1 forest and Trost, 2 the shifters, 3 the inner walls, 4 the outer districts and on. */
    /** The chapter title key of a stage, for "unlocks in ..." hints. */
    public static String chapterKey(int stage) {
        return "quest.takemtitans.chapter." + STAGES[Mth.clamp(stage, 0, STAGES.length - 1)].chapter;
    }

    public static int threatTier(ServerPlayer player) {
        int st = OdmServer.data(player).stage;
        if (st < 3) {
            return 0;
        } else if (st < 9) {
            return 1;
        } else if (st < 13) {
            return 2;
        } else {
            return st < 21 ? 3 : 4;
        }
    }

    public static float damageBonus(int level) {
        return 1.0F + 0.025F * (float)(Math.min(level, 40) - 1);
    }

    public static void applyPerks(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        AttributeInstance hp = player.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            double extra = 2.0 * (double)((Math.min(d.level, 40) - 1) / 6) + 4.0 * (double)d.skills[Skill.IRON_WILL.ordinal()];
            hp.removeModifier(HEALTH_ID);
            if (extra > 0.0) {
                hp.addTransientModifier(new AttributeModifier(HEALTH_ID, extra, Operation.ADD_VALUE));
            }
        }
    }

    public static void addXp(ServerPlayer player, int amount) {
        OdmPlayerData d = OdmServer.data(player);
        d.xp += amount;

        int before;
        for (before = d.level; d.level < 40 && d.xp >= needFor(d.level); d.level++) {
            d.xp = d.xp - needFor(d.level);
        }

        if (d.level >= 40) {
            d.xp = Math.min(d.xp, needFor(40));
        }

        banner(player, "xp", Component.literal("+" + amount + " XP"), Component.empty());
        if (d.level != before) {
            applyPerks(player);
            player.heal(4.0F);
            player.displayClientMessage(Component.translatable("skill.takemtitans.msg.point", d.level - before, Skills.available(d)), false);
            boolean newRank = rankIndex(d.level) != rankIndex(before);
            banner(
                player,
                "rank",
                newRank
                    ? Component.translatable("story.takemtitans.rank_up", Component.translatable(rankKey(d.level)))
                    : Component.translatable("story.takemtitans.level_up", d.level),
                Component.translatable("story.takemtitans.level_perks", d.level, Math.round((damageBonus(d.level) - 1.0F) * 100.0F))
            );
        }

        sync(player);
    }

    private static List<ItemStack> none() {
        return List.of();
    }

    static Story.Stage stage(OdmPlayerData d) {
        return STAGES[Math.min(Math.max(d.stage, 0), LAST)];
    }

    static void complete(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        Story.Stage s = stage(d);
        if (s.obj != Story.Obj.CONTRACTS) {
            List<ItemStack> items = new ArrayList<>(s.items.get());
            if (s.badges > 0) {
                items.add(new ItemStack(ModItems.MERIT_BADGE.get(), s.badges));
            }

            for (ItemStack st : items) {
                give(player, st.copy());
            }

            if (s.obj == Story.Obj.KILL_AREA) {
                int lost = Rebuild.pendingAt(player.serverLevel(), s.x, s.z);
                String zone = Rebuild.zoneAt(s.x, s.z);
                if (zone != null) {
                    int bonus = Math.max(0, 30 - lost / 120);
                    if (bonus > 0) {
                        Shop.addBadges(player, bonus);
                    }

                    player.sendSystemMessage(
                        Component.translatable(
                                bonus > 0 ? "message.takemtitans.district_saved" : "message.takemtitans.district_ruined", Rebuild.zoneName(zone), lost, bonus
                            )
                            .withStyle(bonus > 15 ? ChatFormatting.GREEN : (bonus > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED))
                    );
                }
            }

            int done = d.stage;
            d.stage = Math.min(d.stage + 1, LAST);
            d.stageCount = 0;
            announceGear(player, d.stage);
            Story.Stage next = stage(d);
            int bonus = done == 12
                ? 1
                : (
                    s.id.equals("hale2") || s.id.equals("mitras_defend") || s.id.equals("karanes_siege")
                        ? 1
                        : (done != 20 && done != 26 && done != 30 ? (done == 32 ? 3 : 0) : 2)
                );
            if (bonus > 0) {
                d.bonusPoints += bonus;
                banner(player, "rank", Component.translatable("skill.takemtitans.msg.bonus", bonus), Component.translatable("skill.takemtitans.msg.open"));
            }

            boolean newChapter = !next.chapter.equals(s.chapter);
            banner(
                player,
                d.stage == LAST ? "story" : "stage",
                Component.translatable(d.stage == LAST ? "story.takemtitans.story_complete" : "story.takemtitans.objective_complete"),
                rewardLine(s)
            );
            if (s.xp > 0) {
                addXp(player, s.xp);
            }

            if (s.id.equals("debrief")) {
                banner(player, "rank", Component.translatable("message.takemtitans.horse_unlocked"), Component.translatable("message.takemtitans.horse_unlocked.sub"));
                player.sendSystemMessage(Component.translatable("message.takemtitans.horse_unlocked.chat").withStyle(ChatFormatting.GOLD));
            }

            if (newChapter && next.obj != Story.Obj.CONTRACTS) {
                banner(
                    player,
                    "chapter",
                    Component.translatable("quest.takemtitans.chapter." + next.chapter),
                    Component.translatable("quest.takemtitans." + next.id + ".obj", 0, next.need)
                );
            }

            StoryWorld.onStageStarted(player, d.stage);
            sync(player);
        }
    }

    public static void skipStage(ServerPlayer player) {
        complete(player);
    }

    public static void reset(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        d.stage = 0;
        d.stageCount = 0;
        d.introSeen = false;
        d.contractKind = 0;
        sync(player);
    }

    private static Component rewardLine(Story.Stage s) {
        Component c = Component.literal("+" + s.xp + " XP");
        if (s.badges > 0) {
            c = Component.empty().append(c).append(Component.literal("  ·  ")).append(Component.translatable("story.takemtitans.badges", s.badges));
        }

        for (ItemStack st : s.items.get()) {
            c = Component.empty().append(c).append(Component.literal("  ·  ")).append(st.getHoverName());
        }

        return c;
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.is(ModItems.MERIT_BADGE.get())) {
            Shop.addBadges(player, stack.getCount());
        } else {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    public static void onTitanKill(ServerPlayer player, AbstractTitan titan) {
        OdmPlayerData d = OdmServer.data(player);
        long now = player.level().getGameTime();
        d.streak = now < d.streakUntil ? d.streak + 1 : 1;
        d.streakUntil = now + 200L;
        if (d.streak >= 2) {
            int n = Math.min(7, d.streak);
            banner(player, "streak", Component.translatable("streak.takemtitans." + n), Component.translatable("streak.takemtitans.sub", d.streak));
            player.playNotifySound(ModSounds.NAPE_THUNDER.get(), SoundSource.PLAYERS, 1.0F, 0.8F + (float)n * 0.12F);
            if (n >= 5) {
                PacketDistributor.sendToPlayer(player, FeedbackPayload.of(10, 100, 160, 0));
            }

            Shop.addBadges(player, n - 1);
        }

        boolean boss = titan instanceof ArmoredTitan || titan instanceof BeastTitan || titan instanceof RavagerTitan || titan instanceof WallTitan;
        com.takem.titans.item.FounderEdge.onKill(player, titan);
        int badges = boss ? 0 : 1 + (int)(titan.getTitanHeight() / 6.0F) + titan.threat() / 2;
        give(player, new ItemStack(ModItems.MERIT_BADGE.get(), badges > 0 ? badges : 1));
        addXp(player, boss ? 300 : Math.round((15.0F + titan.getTitanHeight() * 5.0F) * (1.0F + 0.25F * (float)titan.threat())));
        Story.Stage s = stage(d);
        switch (s.obj) {
            case KILL:
                progress(player, d, s);
                break;
            case KILL_AREA:
                double dx = titan.getX() - s.x;
                double dz = titan.getZ() - s.z;
                if (dx * dx + dz * dz < s.r * s.r) {
                    progress(player, d, s);
                }
            case REACH:
            default:
                break;
            case BOSS:
                if (d.stage == 9 && titan instanceof ArmoredTitan
                    || d.stage == 11 && titan instanceof BeastTitan
                    || d.stage == 19 && titan instanceof RavagerTitan
                    || d.stage == 31 && titan instanceof WallTitan) {
                    complete(player);
                }
                break;
            case CONTRACTS:
                Contracts.onKill(player, d, titan);
        }
    }

    private static void progress(ServerPlayer player, OdmPlayerData d, Story.Stage s) {
        d.stageCount++;
        if (d.stageCount >= s.need) {
            complete(player);
        } else {
            sync(player);
        }
    }

    public static void onHeart(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        Story.Stage s = stage(d);
        if (s.obj == Story.Obj.HEARTS) {
            progress(player, d, s);
        }
    }

    public static void onCatch(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        Story.Stage s = stage(d);
        if (s.obj == Story.Obj.CATCH) {
            double dx = player.getX() - s.x;
            double dz = player.getZ() - s.z;
            if (s.r <= 0.0 || dx * dx + dz * dz < s.r * s.r) {
                progress(player, d, s);
            }
        }
    }

    static boolean escortActive(OdmPlayerData d) {
        return d.stage == 14;
    }

    static void checkReach(ServerPlayer player, OdmPlayerData d) {
        Story.Stage s = stage(d);
        if (s.obj == Story.Obj.REACH) {
            double dx = player.getX() - s.x;
            double dz = player.getZ() - s.z;
            if (dx * dx + dz * dz < s.r * s.r) {
                complete(player);
            }
        }
    }

    public static void talk(ServerPlayer player, TownsfolkEntity npc) {
        OdmPlayerData d = OdmServer.data(player);
        Story.Stage s = stage(d);
        int role = npc.getRole();
        List<Component> lines = new ArrayList<>();
        List<Integer> optIds = new ArrayList<>();
        List<Component> optLabels = new ArrayList<>();
        if (role == 7) {
            Training.talk(player, d, npc, lines, optIds, optLabels);
        } else if (Training.active(player, d) && npc.isStoryCharacter()) {
            lines.add(Component.translatable("dialogue.takemtitans.training.first", player.getName()));
        } else if (s.obj == Story.Obj.TALK && s.role == role) {
            for (int i = 0; i < s.lines; i++) {
                lines.add(Component.translatable("dialogue.takemtitans." + s.id + "." + i, player.getName()));
            }

            optIds.add(1);
            optLabels.add(Component.translatable("dialogue.takemtitans." + s.id + ".accept"));
        } else if (s.obj == Story.Obj.CONTRACTS && role == 2) {
            Contracts.talk(player, d, lines, optIds, optLabels);
        } else {
            int n = idleCount(role);
            int pick = npc.getRandom().nextInt(n);
            lines.add(Component.translatable("dialogue.takemtitans.idle." + TownsfolkEntity.ROLE_NAMES[role] + "." + pick, player.getName()));
            if (role == 3) {
                optIds.add(2);
                optLabels.add(Component.translatable("dialogue.takemtitans.option.trade"));
                optIds.add(3);
                optLabels.add(Component.translatable("dialogue.takemtitans.option.hire", Squad.hireCost(d), d.squad.size(), Squad.capacity(player)));
                String fallen = Rebuild.nearestFallen(player);
                if (fallen != null) {
                    optIds.add(4);
                    optLabels.add(Component.translatable("dialogue.takemtitans.option.rebuild", Rebuild.zoneName(fallen), Rebuild.rebuildCost(player, fallen)));
                }

                String fort = Rebuild.fortifiable(player);
                if (fort != null) {
                    optIds.add(5);
                    optLabels.add(
                        Component.translatable(
                            "dialogue.takemtitans.option.fortify",
                            Rebuild.zoneName(fort),
                            Rebuild.fortLevel(player.serverLevel(), fort) + 1,
                            Rebuild.fortCost(player, fort)
                        )
                    );
                }
            }

            if (s.obj == Story.Obj.TALK && s.role >= 0 && role != s.role) {
                lines.add(Component.translatable("dialogue.takemtitans.hint." + TownsfolkEntity.ROLE_NAMES[s.role]));
            }
        }

        optIds.add(0);
        optLabels.add(Component.translatable("dialogue.takemtitans.option.leave"));
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "dialogue");
        tag.putInt("npc", npc.getId());
        tag.putString(
            "speaker",
            json(
                player,
                (Component)(npc.isStoryCharacter() ? npc.getDisplayName() : Component.translatable("npc.takemtitans." + TownsfolkEntity.ROLE_NAMES[role]))
            )
        );
        tag.putInt("role", role);
        ListTag ls = new ListTag();

        for (Component c : lines) {
            ls.add(StringTag.valueOf(json(player, c)));
        }

        tag.put("lines", ls);
        ListTag os = new ListTag();

        for (int i = 0; i < optIds.size(); i++) {
            CompoundTag o = new CompoundTag();
            o.putInt("id", optIds.get(i));
            o.putString("label", json(player, optLabels.get(i)));
            os.add(o);
        }

        tag.put("opts", os);
        PacketDistributor.sendToPlayer(player, new StoryPayload(tag));
    }

    static int idleCount(int role) {
        return switch (role) {
            case 0 -> 10;
            case 1 -> 8;
            case 6 -> 5;
            default -> 3;
        };
    }

    public static void handleAction(StoryActionPayload msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            if (msg.npc() == -1 && msg.option() == 99) {
                OdmServer.data(player).cutsceneUntil = 0L;
            } else {
                if (player.level().getEntity(msg.npc()) instanceof JaegerEntity jaeger && !(jaeger.distanceToSqr(player) > 256.0)) {
                    Squad.handle(player, jaeger, msg.option());
                    return;
                }

                if (player.level().getEntity(msg.npc()) instanceof TownsfolkEntity npc && !(npc.distanceToSqr(player) > 144.0)) {
                    OdmPlayerData d = OdmServer.data(player);
                    Story.Stage s = stage(d);
                    if (npc.getRole() == 7) {
                        Training.handle(player, msg.option());
                        return;
                    }

                    if (Training.active(player, d)) {
                        return;
                    }

                    switch (msg.option()) {
                        case 1:
                            if (s.obj == Story.Obj.TALK && s.role == npc.getRole()) {
                                int done = d.stage;
                                complete(player);
                                StoryWorld.afterTalk(player, npc, done);
                            } else if (s.obj == Story.Obj.CONTRACTS && npc.getRole() == 2) {
                                Contracts.accept(player, d);
                            }
                            break;
                        case 2:
                            if (npc.getRole() == 3) {
                                Shop.open(player, npc);
                            }
                            break;
                        case 3:
                            if (npc.getRole() == 3) {
                                Squad.hire(player);
                            }
                            break;
                        case 4:
                            if (npc.getRole() == 3) {
                                Rebuild.buyRebuild(player);
                            }
                            break;
                        case 5:
                            if (npc.getRole() == 3) {
                                Rebuild.buyFort(player);
                            }
                    }

                    return;
                }
            }
        }
    }

    static String json(ServerPlayer player, Component c) {
        return Serializer.toJson(c, player.registryAccess());
    }

    public static void sync(ServerPlayer player) {
        OdmPlayerData d = OdmServer.data(player);
        Story.Stage s = stage(d);
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "sync");
        tag.putInt("xp", d.xp);
        tag.putInt("level", d.level);
        tag.putInt("needXp", needFor(d.level));
        tag.putString("rank", rankKey(d.level));
        tag.putInt("stage", d.stage);
        tag.putByteArray("sk", d.skills);
        tag.putInt("wallet", d.wallet);
        tag.putInt("pts", Skills.available(d));
        tag.putInt("earned", Skills.earned(d));
        tag.putInt("squad", d.squad.size());
        tag.putInt("squadCap", Skills.squadSize(player));
        boolean training = Training.active(player, d);
        if (training) {
            Training.fill(tag, d);
        } else if (s.obj == Story.Obj.CONTRACTS) {
            tag.putString("chapter", "quest.takemtitans.chapter.after");
            if (d.contractKind > 0) {
                tag.putString("obj", "contract.takemtitans.kind." + d.contractKind);
                tag.putInt("count", d.stageCount);
                tag.putInt("need", d.contractNeed);
            } else {
                tag.putString("obj", "quest.takemtitans.contracts.obj");
            }
        } else {
            tag.putString("chapter", "quest.takemtitans.chapter." + s.chapter);
            tag.putString("obj", "quest.takemtitans." + s.id + ".obj");
            tag.putInt("count", d.stageCount);
            tag.putInt("need", s.need);
        }

        Vec3 target = training ? Training.target(player, d) : StoryWorld.target(player, d);
        if (target != null) {
            tag.putBoolean("hasT", true);
            tag.putDouble("tx", target.x);
            tag.putDouble("tz", target.z);
        }

        PacketDistributor.sendToPlayer(player, new StoryPayload(tag));
    }

    public static void bannerFor(ServerPlayer player, String kind, Component title, Component sub) {
        banner(player, kind, title, sub);
    }

    static void banner(ServerPlayer player, String kind, Component title, Component sub) {
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "banner");
        tag.putString("kind", kind);
        tag.putString("title", json(player, title));
        tag.putString("sub", json(player, sub));
        PacketDistributor.sendToPlayer(player, new StoryPayload(tag));
    }

    public static void cutscene(ServerPlayer player, String id, Vec3 anchor, float yaw, int ticks) {
        OdmPlayerData d = OdmServer.data(player);
        d.cutsceneUntil = player.level().getGameTime() + (long)ticks;
        CompoundTag tag = new CompoundTag();
        tag.putString("t", "cutscene");
        tag.putString("id", id);
        tag.putDouble("x", anchor.x);
        tag.putDouble("y", anchor.y);
        tag.putDouble("z", anchor.z);
        tag.putFloat("yaw", yaw);
        PacketDistributor.sendToPlayer(player, new StoryPayload(tag));
    }

    public static boolean inCutscene(ServerPlayer player) {
        return OdmServer.data(player).cutsceneUntil > player.level().getGameTime();
    }

    static boolean isCrawler(AbstractTitan t) {
        return t instanceof CrawlerTitan;
    }

    static boolean isAbnormal(AbstractTitan t) {
        return t instanceof AbnormalTitan;
    }

    static BlockPos ground(ServerLevel level, double x, double z) {
        int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(x), (int)Math.floor(z));
        return BlockPos.containing(x, (double)y, z);
    }

    static enum Obj {
        TALK,
        KILL,
        KILL_AREA,
        REACH,
        BOSS,
        CONTRACTS,
        CATCH,
        HEARTS;
    }

    static record Stage(
        String id,
        Story.Obj obj,
        int need,
        int role,
        double x,
        double z,
        double r,
        int lines,
        int xp,
        int badges,
        Supplier<List<ItemStack>> items,
        String chapter
    ) {
    }

    /** Gear is only ever bought, so say when the quartermaster has something new. */
    private static void announceGear(ServerPlayer player, int stage) {
        net.minecraft.network.chat.MutableComponent list = null;
        for (Shop.Offer o : Shop.OFFERS) {
            if (o.unlock() == stage && o.category() >= 2) {
                Component name = o.item().get().getHoverName();
                list = list == null ? name.copy() : list.append(", ").append(name);
            }
        }

        if (list != null) {
            player.sendSystemMessage(Component.translatable("message.takemtitans.new_gear", list.withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GOLD));
            player.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
        }
    }
}
