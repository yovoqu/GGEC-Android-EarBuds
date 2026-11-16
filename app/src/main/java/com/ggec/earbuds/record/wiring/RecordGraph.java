package com.ggec.earbuds.record.wiring;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_record.YoRecordSdk;
import com.yovo.biz_ggec_record.api.RecordRuntime;
import com.yovo.biz_ggec_record.api.YoRecordClient;
import com.yovo.biz_ggec_record.api.YoRecordNearby;

import java.util.Objects;

/**
 * Record 独立入口装配根。不挂入主壳 AppContainer，由 RecordMainActivity 自行持有。
 */
public final class RecordGraph {

    private final RecordRuntime runtime;

    public RecordGraph(@NonNull Context appContext) {
        this.runtime = YoRecordSdk.open(Objects.requireNonNull(appContext, "appContext"));
    }

    @NonNull
    public YoRecordNearby nearby() {
        return runtime.nearby();
    }

    @NonNull
    public YoRecordClient client() {
        return runtime.client();
    }

    public void close() {
        runtime.close();
    }
}
