package com.sfgame.data;

public enum PointActivationStrategy {
    ASYNC,
    SYNC;

    public static PointActivationStrategy parse(String value) {
        return valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }
}
