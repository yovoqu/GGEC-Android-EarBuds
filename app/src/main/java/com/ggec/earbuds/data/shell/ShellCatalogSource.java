package com.ggec.earbuds.data.shell;

import com.ggec.earbuds.domain.shell.ShellDestination;

import java.util.List;

public final class ShellCatalogSource {

    public List<ShellTabRecord> read() {
        return List.of(
                new ShellTabRecord(ShellDestination.DEVICE.key()),
                new ShellTabRecord(ShellDestination.AI.key()),
                new ShellTabRecord(ShellDestination.MINE.key()));
    }
}
