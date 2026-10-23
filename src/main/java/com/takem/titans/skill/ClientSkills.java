package com.takem.titans.skill;

public final class ClientSkills {
    public static final byte[] RANKS = new byte[Skill.COUNT];
    public static int points;
    public static int earned;
    public static int level = 1;
    public static int squad;
    public static boolean vanguard;

    private ClientSkills() {
    }

    public static void set(byte[] ranks, int pts, int earn, int lvl) {
        for (int i = 0; i < RANKS.length; i++) {
            RANKS[i] = i < ranks.length ? ranks[i] : 0;
        }

        points = pts;
        earned = earn;
        level = lvl;
    }

    public static int rank(Skill s) {
        return RANKS[s.ordinal()];
    }
}
