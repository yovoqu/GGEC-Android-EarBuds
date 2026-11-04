package com.ggec.earbuds.ui.device;

import com.ggec.earbuds.R;

/** 设备二级页。标识与标题只在这里定义一次。 */
public enum DevicePage {
    MUSIC(R.string.device_entry_music),
    CONTROL(R.string.device_entry_control),
    FUNCTION(R.string.device_entry_function),
    DUAL(R.string.device_entry_dual),
    RECORD(R.string.device_entry_record),
    ABOUT(R.string.device_entry_about);

    public final int titleRes;

    DevicePage(int titleRes) {
        this.titleRes = titleRes;
    }
}
