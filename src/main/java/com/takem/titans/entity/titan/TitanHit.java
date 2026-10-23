package com.takem.titans.entity.titan;

public final class TitanHit {
    public static final int BODY = 0;
    public static final int NAPE = 1;
    public static final int HEAD = 2;
    public static final int ARM = 3;
    public static final int LEG = 4;
    public static final int EYES = 5;
    public static final int PURE = 0;
    public static final int ABNORMAL = 1;
    public static final int CRAWLER = 2;
    public static final int COLOSSAL = 3;

    private TitanHit() {
    }

    public static String partKey(int part) {
        return switch (part) {
            case 1 -> "hud.takemtitans.part.nape";
            case 2 -> "hud.takemtitans.part.head";
            case 3 -> "hud.takemtitans.part.arm";
            case 4 -> "hud.takemtitans.part.leg";
            case 5 -> "hud.takemtitans.part.eyes";
            default -> "hud.takemtitans.part.body";
        };
    }

    public static String kindKey(int kind) {
        return switch (kind) {
            case 1 -> "entity.takemtitans.abnormal_titan";
            case 2 -> "entity.takemtitans.crawler_titan";
            case 3 -> "entity.takemtitans.colossal_titan";
            default -> "entity.takemtitans.pure_titan";
        };
    }
}
