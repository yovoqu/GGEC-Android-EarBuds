package com.yovo.biz_ggec_record;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_record.api.RecordRuntime;
import com.yovo.biz_ggec_record.assembly.RecordAssembly;

/**
 * Record 运行时入口。一次 {@link #open(Context)} 创建附近扫描与音视频会话共用的运行时。
 * 调用方只接触公开 API，不接触 GATT、UUID 或 lib_bluetooth。
 */
public final class YoRecordSdk {
    private YoRecordSdk() {
    }

    @NonNull
    public static RecordRuntime open(@NonNull Context context) {
        return RecordAssembly.open(context);
    }
}
