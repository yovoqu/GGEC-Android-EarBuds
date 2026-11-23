package com.ggec.earbuds.provider.biz_ggec_record;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_record.YoRecordSdk;
import com.yovo.biz_ggec_record.api.RecordRuntime;

/**
 * biz_ggec_record 生命周期落点：模块内唯一 import {@code com.yovo.biz_ggec_record} 的装配入口。
 * 只负责打开运行时；关闭由 {@link RecordPortAdapter#close()} 经 runtime 完成。
 */
public final class RecordProviders {

    private RecordProviders() {
    }

    @NonNull
    public static RecordRuntime open(@NonNull Context context) {
        return YoRecordSdk.open(context);
    }
}
