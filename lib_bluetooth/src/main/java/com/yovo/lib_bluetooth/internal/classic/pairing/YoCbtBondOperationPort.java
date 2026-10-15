package com.yovo.lib_bluetooth.internal.classic.pairing;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 系统级 Bond 操作平台端口（创建、取消、移除配对）。
 */
public interface YoCbtBondOperationPort {

    @NonNull
    YoResult<Void> createBond(@NonNull Context context, @NonNull YoCbtDeviceId deviceId);

    @NonNull
    YoResult<Void> cancelBondProcess(@NonNull YoCbtDeviceId deviceId);

    @NonNull
    YoResult<Void> removeBond(@NonNull YoCbtDeviceId deviceId);
}
