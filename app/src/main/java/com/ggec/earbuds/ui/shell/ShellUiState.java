package com.ggec.earbuds.ui.shell;

import java.util.List;

public final class ShellUiState {

    private final List<ShellTabUi> tabs;
    private final String selectedKey;

    ShellUiState(List<ShellTabUi> tabs, String selectedKey) {
        this.tabs = List.copyOf(tabs);
        this.selectedKey = selectedKey;
    }

    public List<ShellTabUi> tabs() {
        return tabs;
    }

    public String selectedKey() {
        return selectedKey;
    }

    public int selectedTabId() {
        return selected().tabId();
    }

    public ShellTabUi selected() {
        for (ShellTabUi tab : tabs) {
            if (tab.key().equals(selectedKey)) {
                return tab;
            }
        }
        throw new IllegalStateException("Selected tab missing: " + selectedKey);
    }

    public String keyForTab(int tabId) {
        for (ShellTabUi tab : tabs) {
            if (tab.tabId() == tabId) {
                return tab.key();
            }
        }
        throw new IllegalArgumentException("Unknown tab: " + tabId);
    }
}
