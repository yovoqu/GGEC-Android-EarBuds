package com.yovo.biz_ggec_hs.api;

import com.yovo.biz_ggec_hs.api.HsControl.Failure;
import com.yovo.biz_ggec_hs.api.HsDevice.Radios;
import com.yovo.biz_ggec_hs.api.HsDevice.Type;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 扫描结果与筛选。改筛选只重投影已识别设备，不重启扫描。
 */
public final class HsScan {

    private HsScan() {
    }

    /**
     * 三级筛选。空集表示该级不限；同一级命中任一即可，级与级之间同时满足。
     */
    public record Rule(Set<Type> deviceTypes, Set<String> projectIds, Set<String> modelIds,
                       boolean onlyConnectable, int minRssi) {

        public static final int MIN_RSSI_UNRESTRICTED = -127;

        public static Rule none() {
            return new Rule(Set.of(), Set.of(), Set.of(), false, MIN_RSSI_UNRESTRICTED);
        }
    }

    /** 一台已识别的双模设备。地址只用 {@link HsDevice.Radios}，不要在界面侧再算一次。 */
    public record Found(String modelId, Radios radios, int rssi) {
    }

    public interface Listener {
        void onUpdated(List<Found> devices);

        default void onLost(Found device) {
        }

        void onFailed(Failure failure);

        void onFinished();
    }

    /** {@code devices} 已按信号从强到弱。分面计数来自未筛选的全表。 */
    public record View(List<Found> devices, Map<Type, Integer> countsByType,
                       Map<String, Integer> countsByProject, Map<String, Integer> countsByModel,
                       int totalInRange, boolean scanning) {
    }

    public record Health(boolean scanning, boolean silent, boolean throttled, long lastFrameAtMillis) {
    }
}
