package com.ggec.earbuds.domain.shell;

import java.util.List;
import java.util.Objects;

public final class ShellSelectionPolicy {

    public ShellTab first(List<ShellTab> tabs) {
        return Objects.requireNonNull(tabs, "tabs").get(0);
    }

    public ShellTab select(List<ShellTab> tabs, String key) {
        String required = Objects.requireNonNull(key, "key");
        for (ShellTab tab : Objects.requireNonNull(tabs, "tabs")) {
            if (tab.key().equals(required)) {
                return tab;
            }
        }
        throw new IllegalArgumentException("Unknown tab: " + required);
    }
}
