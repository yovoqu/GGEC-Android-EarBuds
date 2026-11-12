package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存储容量查询结果（单耳一对容量；双耳时包含左右两对）。
 */
public final class StorageCapacityResult {
    /** 左耳。 */
    public static final int EAR_LEFT = 0;
    /** 右耳。 */
    public static final int EAR_RIGHT = 1;
    /** 双耳。 */
    public static final int EAR_BOTH = 2;

    /** 左耳总容量键。 */
    public static final String KEY_LEFT_TOTAL_CAPACITY = "00";
    /** 左耳剩余容量键。 */
    public static final String KEY_LEFT_AVAILABLE_CAPACITY = "01";
    /** 右耳总容量键。 */
    public static final String KEY_RIGHT_TOTAL_CAPACITY = "02";
    /** 右耳剩余容量键。 */
    public static final String KEY_RIGHT_AVAILABLE_CAPACITY = "03";

    private final int earType;
    private final long totalCapacity;
    private final long availableCapacity;
    private final long rightTotalCapacity;
    private final long rightAvailableCapacity;
    private final boolean hasRightEar;

    private StorageCapacityResult(int earType,
                                  long totalCapacity,
                                  long availableCapacity,
                                  long rightTotalCapacity,
                                  long rightAvailableCapacity,
                                  boolean hasRightEar) {
        this.earType = earType;
        this.totalCapacity = totalCapacity;
        this.availableCapacity = availableCapacity;
        this.rightTotalCapacity = rightTotalCapacity;
        this.rightAvailableCapacity = rightAvailableCapacity;
        this.hasRightEar = hasRightEar;
    }

    static StorageCapacityResult single(int earType, long total, long available) {
        return new StorageCapacityResult(earType, total, available, 0, 0, false);
    }

    static StorageCapacityResult both(long leftTotal, long leftAvailable,
                                      long rightTotal, long rightAvailable) {
        return new StorageCapacityResult(
                EAR_BOTH, leftTotal, leftAvailable, rightTotal, rightAvailable, true);
    }

    public int getEarType() {
        return earType;
    }

    /** 单耳容量，或双耳时的左耳总容量。 */
    public long getTotalCapacity() {
        return totalCapacity;
    }

    /** 单耳剩余，或双耳时的左耳剩余容量。 */
    public long getAvailableCapacity() {
        return availableCapacity;
    }

    public long getRightTotalCapacity() {
        return rightTotalCapacity;
    }

    public long getRightAvailableCapacity() {
        return rightAvailableCapacity;
    }

    public boolean hasRightEar() {
        return hasRightEar;
    }

    /**
     * 转换为有序容量 Map。
     * <p>
     * 键：{@code "00"=左耳总容量, "01"=左耳剩余容量, "02"=右耳总容量, "03"=右耳剩余容量}；
     * 单耳查询只包含对应耳的两项；双耳查询包含左右两对，左耳在前。
     *
     * @return 使用 {@link #KEY_LEFT_TOTAL_CAPACITY} 等键的有序容量 Map
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        if (hasRightEar()) {
            map.put(KEY_LEFT_TOTAL_CAPACITY, String.valueOf(totalCapacity));
            map.put(KEY_LEFT_AVAILABLE_CAPACITY, String.valueOf(availableCapacity));
            map.put(KEY_RIGHT_TOTAL_CAPACITY, String.valueOf(rightTotalCapacity));
            map.put(KEY_RIGHT_AVAILABLE_CAPACITY, String.valueOf(rightAvailableCapacity));
        } else if (earType == EAR_LEFT) {
            map.put(KEY_LEFT_TOTAL_CAPACITY, String.valueOf(totalCapacity));
            map.put(KEY_LEFT_AVAILABLE_CAPACITY, String.valueOf(availableCapacity));
        } else {
            map.put(KEY_RIGHT_TOTAL_CAPACITY, String.valueOf(totalCapacity));
            map.put(KEY_RIGHT_AVAILABLE_CAPACITY, String.valueOf(availableCapacity));
        }
        return map;
    }
}
