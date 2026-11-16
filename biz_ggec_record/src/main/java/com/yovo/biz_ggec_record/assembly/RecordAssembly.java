package com.yovo.biz_ggec_record.assembly;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_record.api.RecordRuntime;
import com.yovo.biz_ggec_record.internal.session.YoRecordClientImpl;
import com.yovo.biz_ggec_record.provider.lib_bluetooth.RecordNearbyScanner;
import com.yovo.biz_ggec_record.provider.lib_bluetooth.YoBleRecordLink;

/**
 * Record 运行时组合根：把同一条 BLE 链路装配成附近扫描与会话共用的 {@link RecordRuntime}。
 */
public final class RecordAssembly {
    private RecordAssembly() {
    }

    @NonNull
    public static RecordRuntime open(@NonNull Context context) {
        YoBleRecordLink link = YoBleRecordLink.open(context);
        return new RecordRuntime(
                new RecordNearbyScanner(link.bleClient()),
                new YoRecordClientImpl(context, link));
    }
}
