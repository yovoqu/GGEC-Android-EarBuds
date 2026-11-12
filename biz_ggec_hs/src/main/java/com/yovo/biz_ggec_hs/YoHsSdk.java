package com.yovo.biz_ggec_hs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.HsRuntime;
import com.yovo.biz_ggec_hs.assembly.HsAssembly;

/**
 * HS 运行时入口。一次 {@link #open(Context)} 只创建一条 BLE 客户端，附近扫描和协议会话共用它。
 */
public final class YoHsSdk {
    private YoHsSdk() {
    }

    @NonNull
    public static HsRuntime open(@NonNull Context context) {
        return HsAssembly.open(context);
    }
}
