package com.ggec.earbuds.ui.shell;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public final class ShellTabUi {

    private final String key;
    private final int tabId;
    @StringRes private final int titleRes;
    @DrawableRes private final int iconRes;
    @DrawableRes private final int selectedIconRes;

    ShellTabUi(String key,
               int tabId,
               @StringRes int titleRes,
               @DrawableRes int iconRes,
               @DrawableRes int selectedIconRes) {
        this.key = key;
        this.tabId = tabId;
        this.titleRes = titleRes;
        this.iconRes = iconRes;
        this.selectedIconRes = selectedIconRes;
    }

    public String key() {
        return key;
    }

    public int tabId() {
        return tabId;
    }

    @StringRes
    public int titleRes() {
        return titleRes;
    }

    @DrawableRes
    public int iconRes() {
        return iconRes;
    }

    @DrawableRes
    public int selectedIconRes() {
        return selectedIconRes;
    }
}
