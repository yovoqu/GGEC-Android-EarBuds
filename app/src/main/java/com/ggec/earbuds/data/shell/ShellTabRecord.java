package com.ggec.earbuds.data.shell;

import java.util.Objects;

public final class ShellTabRecord {

    private final String key;

    public ShellTabRecord(String key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    public String key() {
        return key;
    }
}
