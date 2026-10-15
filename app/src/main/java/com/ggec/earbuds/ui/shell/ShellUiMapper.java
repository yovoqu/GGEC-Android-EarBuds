package com.ggec.earbuds.ui.shell;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.shell.ShellTab;

import java.util.ArrayList;
import java.util.List;

public final class ShellUiMapper {

    private static final int DEVICE_TAB_ID = 1;
    private static final int AI_TAB_ID = 2;
    private static final int MINE_TAB_ID = 3;

    public ShellUiState map(List<ShellTab> tabs, ShellTab selected) {
        List<ShellTabUi> items = new ArrayList<>(tabs.size());
        for (ShellTab tab : tabs) {
            items.add(toUi(tab));
        }
        return new ShellUiState(items, selected.key());
    }

    private static ShellTabUi toUi(ShellTab tab) {
        return switch (tab.destination()) {
            case DEVICE -> item(tab, DEVICE_TAB_ID, R.string.tab_device,
                    R.drawable.youi_hds_ic_house, R.drawable.youi_hds_ic_house_fill);
            case AI -> item(tab, AI_TAB_ID, R.string.tab_ai,
                    R.drawable.youi_hds_ic_discover, R.drawable.youi_hds_ic_discover_fill);
            case MINE -> item(tab, MINE_TAB_ID, R.string.tab_mine,
                    R.drawable.youi_hds_ic_person, R.drawable.youi_hds_ic_person_fill);
        };
    }

    private static ShellTabUi item(ShellTab tab, int tabId, int titleRes, int iconRes, int selectedIconRes) {
        return new ShellTabUi(tab.key(), tabId, titleRes, iconRes, selectedIconRes);
    }
}
