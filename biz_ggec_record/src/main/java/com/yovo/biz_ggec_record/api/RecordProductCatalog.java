package com.yovo.biz_ggec_record.api;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_record.model.RecordProduct;

import java.util.List;

/**
 * Record 产品目录真源。App 只读投影，不得另建第二套机型表。
 */
public final class RecordProductCatalog {

    /** Ambiq BLE 音视频录播设备。 */
    public static final String MODEL_AMBIQ = "AMBIQ";

    private static final List<RecordProduct> PRODUCTS = List.of(
            new RecordProduct(MODEL_AMBIQ, "AIRecord"));

    private RecordProductCatalog() {
    }

    @NonNull
    public static List<RecordProduct> products() {
        return PRODUCTS;
    }

    public static boolean isKnownModel(@NonNull String modelId) {
        for (RecordProduct product : PRODUCTS) {
            if (product.modelId().equals(modelId)) {
                return true;
            }
        }
        return false;
    }
}
