package com.yovo.biz_ggec_wy.api;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_wy.api.WyControl.Failure;
import com.yovo.biz_ggec_wy.api.WyDevice.Radios;
import com.yovo.biz_ggec_wy.api.WyDevice.Type;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WY 扫描结果与筛选。
 */
public final class WyScan {

    private WyScan() {
    }

    /**
     * 筛选规则。空集表示不限；minRssi 为最小信号阈值。
     */
    public record Rule(Set<Type> deviceTypes, Set<String> projectIds, Set<String> modelIds,
                       int minRssi) {

        public static final int MIN_RSSI_UNRESTRICTED = -127;

        public static Rule none() {
            return new Rule(Set.of(), Set.of(), Set.of(), MIN_RSSI_UNRESTRICTED);
        }
    }

    /** 一台已发现的 WY 设备。 */
    public record Found(String modelId, Radios radios, int rssi) {
    }

    public interface Listener {
        void onUpdated(List<Found> devices);

        default void onLost(Found device) {
        }

        void onFailed(Failure failure);

        void onFinished();
    }

    /** 视图快照：{@code devices} 已按信号从强到弱排序。分面计数来自全表。 */
    public record View(List<Found> devices, Map<Type, Integer> countsByType,
                       Map<String, Integer> countsByProject, Map<String, Integer> countsByModel,
                       int totalInRange, boolean scanning) {
    }

    public record Health(boolean scanning, boolean silent, boolean throttled, long lastFrameAtMillis) {
    }
}
