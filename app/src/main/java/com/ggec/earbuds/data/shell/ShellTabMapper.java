package com.ggec.earbuds.data.shell;

import com.ggec.earbuds.domain.shell.ShellDestination;
import com.ggec.earbuds.domain.shell.ShellTab;

import java.util.Objects;

public final class ShellTabMapper {

    public ShellTab toDomain(ShellTabRecord record) {
        return new ShellTab(ShellDestination.fromKey(Objects.requireNonNull(record, "record").key()));
    }
}
