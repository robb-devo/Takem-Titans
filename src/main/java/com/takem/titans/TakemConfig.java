package com.takem.titans;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class TakemConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final IntValue GAS_CAPACITY;
    public static final IntValue SPARE_BLADES;
    public static final IntValue BLADE_DURABILITY;
    public static final DoubleValue HOOK_RANGE;
    public static final BooleanValue WALL_IMPACT_DAMAGE;
    public static final DoubleValue NAPE_DAMAGE_MULTIPLIER;
    public static final BooleanValue TITAN_SPAWNING;
    public static final BooleanValue TITAN_SPAWNING_NORMAL_WORLDS;
    public static final IntValue TITAN_CAP_PER_PLAYER;
    public static final IntValue TITAN_CAP_NORMAL_WORLDS;
    public static final BooleanValue TITANS_SLEEP_AT_NIGHT;
    public static final BooleanValue TITAN_GRIEFING;
    public static final BooleanValue TITANS_EAT_PLAYERS;
    public static final BooleanValue STARTER_KIT;
    public static final BooleanValue TUTORIAL;
    public static final IntValue BREACH_DAY;
    public static final BooleanValue BUILDING_COLLAPSE;
    public static final BooleanValue SPEAR_BLASTS_BLOCKS;
    public static final IntValue DEBRIS_LIMIT;
    public static final DoubleValue RUBBLE_CHANCE;
    public static final BooleanValue KILL_SLOW_MOTION;
    public static final BooleanValue CAMERA_ROLL;
    public static final DoubleValue CAMERA_ROLL_STRENGTH;
    public static final BooleanValue SPEED_FOV;
    public static final BooleanValue SPEED_LINES;
    public static final BooleanValue SCREEN_SHAKE;
    public static final BooleanValue SHOW_CONTROLS_HINT;
    public static final BooleanValue HIJACK_VANILLA_KEYS;
    public static final DoubleValue HUD_SCALE;
    public static final BooleanValue ARRIVAL_ASSIST;
    public static final BooleanValue HIT_STOP;
    public static final BooleanValue HIT_FLASH;
    public static final BooleanValue IMPACT_FRAMES;
    public static final BooleanValue COLOR_GRADE;
    public static final DoubleValue COLOR_GRADE_STRENGTH;
    public static final DoubleValue SERUM_RADIUS;
    public static final BooleanValue SERUM_BREAKS_BLOCKS;
    public static final BooleanValue SERUM_SPAWNS_COLOSSAL;
    public static final IntValue RUMBLING_TITANS;
    public static final DoubleValue RUMBLING_DISTANCE;
    public static final BooleanValue RUMBLING_BREAKS_BLOCKS;
    public static final IntValue BOULDER_COUNT;
    public static final BooleanValue BOULDER_BREAKS_BLOCKS;
    public static final DoubleValue RIFT_RADIUS;
    public static final BooleanValue RIFT_BREAKS_BLOCKS;
    public static final BooleanValue SLASH_WAVE_BREAKS_BLOCKS;
    public static final BooleanValue SONIC_BOOM_BREAKS_GLASS;
    public static final BooleanValue DESTRUCTION_FIRES;
    public static final BooleanValue METEOR_DIVE_BREAKS_BLOCKS;
    public static final DoubleValue METEOR_POWER;
    public static final BooleanValue METEOR_BREAKS_WALLS;
    public static final BooleanValue EXPLOSIVES_BREAK_BLOCKS;
    public static final BooleanValue GRAB_DEBRIS;
    public static final BooleanValue JAEGER_PATROLS;
    public static final IntValue JAEGER_CAP;
    public static final BooleanValue TOWN_REACTIONS;
    public static final DoubleValue COLLAPSE_SCALE;

    private TakemConfig() {
    }

    static {
        Builder b = new Builder();
        b.comment("ODM gear").push("odm");
        GAS_CAPACITY = b.comment("Gas units a full ODM gear holds. Boosting burns 3 per tick, reeling 1 per tick per hook.")
            .defineInRange("gasCapacity", 2400, 100, 100000);
        SPARE_BLADES = b.comment("Spare blade sets the blade boxes hold.").defineInRange("spareBlades", 8, 0, 64);
        BLADE_DURABILITY = b.comment("Hits a blade set survives before it shatters.").defineInRange("bladeDurability", 40, 1, 10000);
        HOOK_RANGE = b.comment("Maximum anchor distance in blocks.").defineInRange("hookRange", 72.0, 8.0, 256.0);
        WALL_IMPACT_DAMAGE = b.comment("Slamming into walls at very high speed hurts.").define("wallImpactDamage", true);
        NAPE_DAMAGE_MULTIPLIER = b.comment("Multiplier applied to blade damage against titans (higher = easier kills).")
            .defineInRange("napeDamageMultiplier", 1.0, 0.05, 100.0);
        b.pop();
        b.comment("Titans").push("titans");
        TITAN_SPAWNING = b.comment("Titans spawn naturally outside the walls in the Shiganshina world type.").define("naturalSpawning", true);
        TITAN_SPAWNING_NORMAL_WORLDS = b.comment("Titans also roam the surface of normal overworlds in daylight.").define("spawnInNormalWorlds", true);
        TITAN_CAP_PER_PLAYER = b.comment("Max titans around each player (Shiganshina world).").defineInRange("capPerPlayer", 9, 0, 64);
        TITAN_CAP_NORMAL_WORLDS = b.comment("Max titans around each player (normal worlds).").defineInRange("capNormalWorlds", 3, 0, 64);
        TITANS_SLEEP_AT_NIGHT = b.comment("Titans lose their sunlight and become sluggish at night.").define("sluggishAtNight", true);
        TITAN_GRIEFING = b.comment(
                "Titans smash through buildings in their way: wood, glass, plaster and brick, never terrain, trees or the Walls (also needs the mobGriefing gamerule)."
            )
            .define("titanGriefing", true);
        TITANS_EAT_PLAYERS = b.comment("Titans can grab and eat players. If false, grabs only hurt.").define("titansEatPlayers", true);
        STARTER_KIT = b.comment("Give new players ODM gear, blades and flares when they first join a Shiganshina world.").define("starterKit", true);
        TUTORIAL = b.comment(
                "New recruits arrive without gear and are walked through the ODM drills at the training yard by Instructor Brandt,",
                "who hands out the starter kit. If false, the kit is given on first join as before."
            )
            .define("tutorial", true);
        BREACH_DAY = b.comment(
                "In-game day on which the Colossal Titan appears and kicks in Shiganshina's outer gate",
                "(0 = never; ops can start it any time with /titans breach)."
            )
            .defineInRange("colossalBreachDay", 3, 0, 100000);
        b.pop();
        b.comment("Combat").push("combat");
        KILL_SLOW_MOTION = b.comment("A nape kill slows the game down for a heartbeat (singleplayer only, it slows the whole world).")
            .define("killSlowMotion", true);
        b.pop();
        b.comment("Destruction").push("destruction");
        BUILDING_COLLAPSE = b.comment("Buildings that lose their support collapse into falling debris.").define("buildingCollapse", true);
        SPEAR_BLASTS_BLOCKS = b.comment("Thunder spears blow holes in buildings (never terrain or the Walls).").define("thunderSpearsBreakBlocks", true);
        DEBRIS_LIMIT = b.comment("Most flying debris blocks alive at once. Beyond this, broken blocks just turn to dust.")
            .defineInRange("debrisLimit", 900, 0, 5000);
        RUBBLE_CHANCE = b.comment("Chance that a debris block stays as rubble where it lands instead of shattering.")
            .defineInRange("rubbleChance", 0.35, 0.0, 1.0);
        b.pop();
        b.comment("Colossal Serum").push("colossalSerum");
        SERUM_RADIUS = b.comment("Radius of the fireball and crater in blocks. The blast wave reaches two and a half times as far.")
            .defineInRange("blastRadius", 34.0, 8.0, 64.0);
        SERUM_BREAKS_BLOCKS = b.comment("The blast carves a crater and flattens buildings.").define("breaksBlocks", true);
        SERUM_SPAWNS_COLOSSAL = b.comment("The Colossal Titan rises out of the blast for a while before it is gone in steam.")
            .define("spawnsColossalTitan", true);
        b.pop();
        b.comment("Rumbling Horn").push("rumbling");
        RUMBLING_TITANS = b.comment("How many Wall Titans rise and march in one Rumbling.").defineInRange("titans", 7, 1, 15);
        RUMBLING_DISTANCE = b.comment("How far they keep marching after they pass where the horn was blown, in blocks.")
            .defineInRange("marchDistance", 260.0, 40.0, 1000.0);
        RUMBLING_BREAKS_BLOCKS = b.comment("They flatten everything in their path, the Walls included, and leave a crater with every step.")
            .define("breaksBlocks", true);
        b.pop();
        b.comment("Beast Titan Boulder").push("beastBoulder");
        BOULDER_COUNT = b.comment("Flaming boulders in one barrage.").defineInRange("boulders", 30, 4, 80);
        BOULDER_BREAKS_BLOCKS = b.comment("Boulders punch craters and smash buildings.").define("breaksBlocks", true);
        b.pop();
        b.comment("Paths Rift").push("pathsRift");
        RIFT_RADIUS = b.comment("Radius of the hole the rift tears out when it detonates. It pulls things in from three times as far.")
            .defineInRange("radius", 14.0, 6.0, 30.0);
        RIFT_BREAKS_BLOCKS = b.comment("The rift rips buildings apart and swallows them.").define("breaksBlocks", true);
        b.pop();
        b.comment("Godspeed gear and Titanbreaker blades").push("godspeed");
        SLASH_WAVE_BREAKS_BLOCKS = b.comment("Titanbreaker slash waves cut through buildings.").define("slashWaveBreaksBlocks", true);
        SONIC_BOOM_BREAKS_GLASS = b.comment("Sonic booms from Godspeed gear blow out windows.").define("sonicBoomBreaksGlass", true);
        b.pop();
        b.comment("Destruction").push("destruction_extra");
        DESTRUCTION_FIRES = b.comment("Smashed lanterns, stoves and forges set the wreckage on fire.").define("destructionFires", true);
        METEOR_DIVE_BREAKS_BLOCKS = b.comment("The Meteor Dive landing blasts a crater.").define("meteorDiveBreaksBlocks", true);
        METEOR_POWER = b.comment(
                "Scales the Meteor Dive. Impact power grows exponentially with the height you dive from; this multiplies the height that counts."
            )
            .defineInRange("meteorPower", 1.0, 0.1, 5.0);
        METEOR_BREAKS_WALLS = b.comment("Cataclysm-level Meteor Dives (about 70+ blocks) can carve through the Walls themselves.")
            .define("meteorBreaksWalls", true);
        EXPLOSIVES_BREAK_BLOCKS = b.comment("Gas Nova, thermobaric canisters, cluster spears and demolition charges break buildings.")
            .define("explosivesBreakBlocks", true);
        GRAB_DEBRIS = b.comment("ODM hooks can catch falling debris and chunks of buildings, swing them and throw them.").define("grabDebris", true);
        COLLAPSE_SCALE = b.comment("Scales how far a collapse spreads and how big the falling sections may get.").defineInRange("collapseScale", 1.0, 0.5, 2.0);
        b.pop();
        b.comment("People").push("people");
        JAEGER_PATROLS = b.comment("Jaeger patrols (scouts on ODM gear) roam outside the walls and hunt titans on their own.").define("jaegerPatrols", true);
        JAEGER_CAP = b.comment("Most lone Jaegers around each player.").defineInRange("jaegerCap", 4, 0, 16);
        TOWN_REACTIONS = b.comment("Townsfolk react to titans, destruction and your kills: panic, flee, cheer and shout.").define("townReactions", true);
        b.pop();
        COMMON_SPEC = b.build();
        Builder c = new Builder();
        c.comment("Camera and HUD").push("client");
        CAMERA_ROLL = c.comment("Tilt the camera into swings.").define("cameraRoll", true);
        CAMERA_ROLL_STRENGTH = c.defineInRange("cameraRollStrength", 1.0, 0.0, 3.0);
        SPEED_FOV = c.comment("Widen the field of view at high speed.").define("speedFov", true);
        SPEED_LINES = c.comment("Draw speed streaks at high speed.").define("speedLines", true);
        SCREEN_SHAKE = c.comment("Shake the screen when titans stomp and the Colossal Titan appears.").define("screenShake", true);
        SHOW_CONTROLS_HINT = c.comment("Show the ODM controls hint while holding blades on the ground.").define("showControlsHint", true);
        HIJACK_VANILLA_KEYS = c.comment(
                "While blades are drawn and ODM gear is worn, hook keys that share a key with Drop/Inventory (Q/E by default) fire hooks instead."
            )
            .define("hookKeysOverrideDropAndInventory", true);
        HUD_SCALE = c.defineInRange("hudScale", 1.0, 0.5, 2.0);
        ARRIVAL_ASSIST = c.comment(
                "When reeling in to an anchor on a block, brake the last few blocks so you land on the wall instead of slamming into it.",
                "Anchors on titans never brake. Turn off for full manual control."
            )
            .define("arrivalAssist", true);
        HIT_STOP = c.comment("Freeze for a split second when a blade bites, harder on deep cuts and kills.").define("hitStop", true);
        HIT_FLASH = c.comment("Flash the screen edges on hits and kills.").define("hitFlash", true);
        IMPACT_FRAMES = c.comment(
                "Anime impact frames: the picture flashes to black-and-white ink for a split second on nape kills,",
                "huge cuts, Overdrive and the Colossal Serum. Turn off if flashing images bother you."
            )
            .define("impactFrames", true);
        COLOR_GRADE = c.comment("A subtle cinematic colour grade over the world: richer colour, soft contrast and a light vignette.").define("colorGrade", true);
        COLOR_GRADE_STRENGTH = c.defineInRange("colorGradeStrength", 1.0, 0.0, 1.0);
        c.pop();
        CLIENT_SPEC = c.build();
    }
}
