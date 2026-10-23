package com.takem.titans.odm;

import java.util.UUID;
import javax.annotation.Nullable;

public final class Tether {
    public int holder = -1;
    public double length;
    public boolean carried;
    public int age;
    public int thrown;
    @Nullable
    public UUID thrower;
    public float power = 1.0F;

    public boolean active() {
        return this.holder >= 0;
    }

    public void clear() {
        this.holder = -1;
        this.carried = false;
        this.age = 0;
    }

    public boolean thrownBy(int playerId, @Nullable UUID uuid) {
        return this.thrown > 0 && uuid != null && uuid.equals(this.thrower);
    }
}
