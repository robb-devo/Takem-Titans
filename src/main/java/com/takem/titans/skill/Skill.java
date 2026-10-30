package com.takem.titans.skill;

import java.util.Locale;

public enum Skill {
    SHARPENED_STEEL(0, 0, 0, 3, 1),
    NAPE_SPECIALIST(0, 1, -1, 3, 3, SHARPENED_STEEL),
    FLOWING_COMBO(0, 1, 1, 2, 3, SHARPENED_STEEL),
    THUNDER_EXECUTION(0, 2, -1, 1, 10, NAPE_SPECIALIST),
    CRESCENT_CYCLONE(0, 2, 1, 1, 10, FLOWING_COMBO),
    ACKERMAN_AWAKENING(0, 3, 0, 1, 20, THUNDER_EXECUTION, CRESCENT_CYCLONE),
    GAS_EFFICIENCY(1, 0, 0, 3, 1),
    REINFORCED_WINCH(1, 1, -1, 3, 3, GAS_EFFICIENCY),
    AFTERBURN_DASH(1, 1, 1, 2, 3, GAS_EFFICIENCY),
    WRECKING_BALL(1, 2, -1, 1, 8, REINFORCED_WINCH),
    PHANTOM_STEP(1, 2, 1, 1, 8, AFTERBURN_DASH),
    TITAN_WINCH(1, 3, 0, 1, 18, WRECKING_BALL, PHANTOM_STEP),
    HEAVY_LANDING(2, 0, 0, 3, 1),
    SHOCKWAVE(2, 1, -1, 2, 4, HEAVY_LANDING),
    DEMOLISHER(2, 1, 1, 2, 4, HEAVY_LANDING),
    METEOR_REBOUND(2, 2, -1, 1, 10, SHOCKWAVE),
    COMET_TRAIL(2, 2, 1, 1, 10, DEMOLISHER),
    EXTINCTION_EVENT(2, 3, 0, 1, 22, METEOR_REBOUND, COMET_TRAIL),
    GAS_NOVA(3, 0, 0, 1, 2),
    VOLATILE_MIX(3, 1, -1, 3, 4, GAS_NOVA),
    SPEAR_SPECIALIST(3, 1, 1, 2, 4, GAS_NOVA),
    CHAIN_REACTION(3, 2, -1, 1, 12, VOLATILE_MIX),
    INCENDIARY_EDGE(3, 2, 1, 1, 12, SPEAR_SPECIALIST),
    SUPERNOVA(3, 3, 0, 1, 25, CHAIN_REACTION, INCENDIARY_EDGE),
    IRON_WILL(4, 0, 0, 3, 1),
    SQUAD_LEADER(4, 1, -1, 2, 5, IRON_WILL),
    FIELD_MEDIC(4, 1, 1, 2, 5, IRON_WILL),
    RALLY_CRY(4, 2, -1, 1, 12, SQUAD_LEADER),
    VETERAN_INSTINCTS(4, 2, 1, 1, 12, FIELD_MEDIC),
    WINGS_OF_FREEDOM(4, 3, 0, 1, 28, RALLY_CRY, VETERAN_INSTINCTS),
    SKYFALL_STRIKE(5, 0, 0, 3, 4),
    KILL_CHAIN(5, 1, -1, 2, 8, SKYFALL_STRIKE),
    AFTERSHOCK(5, 1, 1, 2, 8, SKYFALL_STRIKE),
    EXECUTIONER(5, 2, -1, 1, 14, KILL_CHAIN),
    STORM_DASH(5, 2, 1, 1, 14, AFTERSHOCK),
    SKYBREAKER(5, 3, 0, 1, 24, EXECUTIONER, STORM_DASH),
    TITAN_BLOOD(6, 0, 0, 3, 6),
    HARDENED_SKIN(6, 1, -1, 3, 8, TITAN_BLOOD),
    CRUSHING_FISTS(6, 1, 1, 3, 8, TITAN_BLOOD),
    WAR_CRY(6, 2, -1, 2, 14, HARDENED_SKIN),
    EARTHSHAKER(6, 2, 1, 2, 14, CRUSHING_FISTS),
    STEAM_REGEN(6, 3, 0, 1, 22, WAR_CRY, EARTHSHAKER),
    BLADE_DANCER(0, 4, 0, 1, 32, ACKERMAN_AWAKENING),
    SKY_LORD(1, 4, 0, 1, 32, TITAN_WINCH),
    CATACLYSM(2, 4, 0, 1, 32, EXTINCTION_EVENT),
    INFERNO(3, 4, 0, 1, 32, SUPERNOVA),
    LEGION(4, 4, 0, 1, 32, WINGS_OF_FREEDOM),
    GODSLAYER(5, 4, 0, 1, 32, SKYBREAKER),
    COORDINATE(6, 4, 0, 1, 32, STEAM_REGEN);

    public static final Skill[] ALL = values();
    public static final int COUNT = ALL.length;
    public static final String[] BRANCHES = new String[]{"blade", "odm", "demolition", "firebrand", "command", "combo", "titan"};
    public static final int[] BRANCH_COLORS = new int[]{14698556, 5223400, 15768106, 16738847, 7129951, 13073919, 14262374};
    public final int branch;
    public final int row;
    public final int lane;
    public final int max;
    public final int minLevel;
    public final Skill[] requires;
    public final String id;

    private Skill(int branch, int row, int lane, int max, int minLevel, Skill... requires) {
        this.branch = branch;
        this.row = row;
        this.lane = lane;
        this.max = max;
        this.minLevel = minLevel;
        this.requires = requires;
        this.id = this.name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return "skill.takemtitans." + this.id;
    }

    public String descKey() {
        return "skill.takemtitans." + this.id + ".desc";
    }

    public boolean capstone() {
        return this.row >= 3;
    }

    public boolean mastery() {
        return this.row == 4;
    }

    public static int totalRanks() {
        int n = 0;

        for (Skill s : ALL) {
            n += s.max;
        }

        return n;
    }
}
