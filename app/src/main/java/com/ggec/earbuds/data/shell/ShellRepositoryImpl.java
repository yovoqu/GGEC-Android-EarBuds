package com.ggec.earbuds.data.shell;

import com.ggec.earbuds.domain.shell.ShellRepository;
import com.ggec.earbuds.domain.shell.ShellTab;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ShellRepositoryImpl implements ShellRepository {

    private final ShellCatalogSource source;
    private final ShellTabMapper mapper;

    public ShellRepositoryImpl(ShellCatalogSource source, ShellTabMapper mapper) {
        this.source = Objects.requireNonNull(source, "source");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<ShellTab> loadTabs() {
        List<ShellTab> tabs = new ArrayList<>();
        for (ShellTabRecord record : source.read()) {
            tabs.add(mapper.toDomain(record));
        }
        return List.copyOf(tabs);
    }
}
