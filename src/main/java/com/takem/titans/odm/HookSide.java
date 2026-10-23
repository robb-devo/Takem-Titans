package com.takem.titans.odm;

public enum HookSide {
    LEFT(-1),
    RIGHT(1);

    public final int sign;

    private HookSide(int sign) {
        this.sign = sign;
    }

    public static HookSide byIndex(int i) {
        return i == 0 ? LEFT : RIGHT;
    }
}
