package com.ggec.earbuds.domain.shell;

import java.util.Objects;

public final class ShellTab {

    private final ShellDestination destination;

    public ShellTab(ShellDestination destination) {
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    public ShellDestination destination() {
        return destination;
    }

    public String key() {
        return destination.key();
    }
}
