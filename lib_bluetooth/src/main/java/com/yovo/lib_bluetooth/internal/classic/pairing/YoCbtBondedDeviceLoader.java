package com.yovo.lib_bluetooth.internal.classic.pairing;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtBondedDevice;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 从平台 Bond 存储加载已配对设备列表。
 */
public interface YoCbtBondedDeviceLoader {

    @NonNull
    List<YoCbtBondedDevice> load(@NonNull Context context);
}
