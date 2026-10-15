package com.ggec.earbuds.domain.shell;

import java.util.List;
import java.util.Objects;

public final class LoadShellTabs {

    private final ShellRepository repository;

    public LoadShellTabs(ShellRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<ShellTab> execute() {
        List<ShellTab> tabs = List.copyOf(repository.loadTabs());
        if (tabs.isEmpty()) {
            throw new IllegalStateException("Shell catalog is empty");
        }
        return tabs;
    }
}
