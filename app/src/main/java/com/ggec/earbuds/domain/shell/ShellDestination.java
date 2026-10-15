package com.ggec.earbuds.domain.shell;

import java.util.Objects;

public enum ShellDestination {
    DEVICE("device"),
    AI("ai"),
    MINE("mine");

    private final String key;

    ShellDestination(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ShellDestination fromKey(String key) {
        String required = Objects.requireNonNull(key, "key");
        for (ShellDestination destination : values()) {
            if (destination.key.equals(required)) {
                return destination;
            }
        }
        throw new IllegalArgumentException("Unknown destination: " + required);
    }
}
