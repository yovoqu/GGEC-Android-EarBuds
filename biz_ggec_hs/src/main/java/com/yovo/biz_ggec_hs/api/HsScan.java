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

    /**
     * 一台已识别的双模设备。地址只用 {@link HsDevice.Radios}，不要在界面侧再算一次。
     *
     * <p>{@code productId} 是新广播（HS02 厂商段）的 Product ID（机型代码）：{@code 0} 为 V11、
     * {@code 1} 为 V12。旧广播没有这个字段，恒为空串——调用方按空串与否区分即可，不要用假值填充。</p>
     */
    public record Found(String modelId, Radios radios, int rssi, String productId) {

        public Found {
            productId = productId != null ? productId : "";
        }

        /** 兼容旧构造：不带 Product ID 的来源（旧广播）用空串。 */
        public Found(String modelId, Radios radios, int rssi) {
            this(modelId, radios, rssi, "");
        }
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
