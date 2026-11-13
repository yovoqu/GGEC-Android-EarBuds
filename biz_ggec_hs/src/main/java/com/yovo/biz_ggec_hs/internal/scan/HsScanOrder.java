package com.yovo.biz_ggec_hs.internal.scan;

import com.yovo.biz_ggec_hs.api.HsScan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描列表的排序策略。
 *
 * <p>新设备按当前 RSSI 插入。已经在列表里的设备保持相对顺序，只有某一台比紧挨着的
 * 上一台强出 {@link #REORDER_BAND_DBM} 才交换。平滑后的 RSSI 仍会一两个 dBm 地抖，
 * 不设这道带宽的话，列表会在相邻卡片之间来回挪，排序动画就不是「信号真的变了」。</p>
 */
final class HsScanOrder {

    /** 相邻名次交换所需的最小 RSSI 差（dBm）。 */
    static final int REORDER_BAND_DBM = 8;

    private HsScanOrder() {
    }

    static List<HsScan.Found> arrange(List<HsScan.Found> candidates, List<HsScan.Found> previous) {
        Map<String, HsScan.Found> remaining = new LinkedHashMap<>();
        for (HsScan.Found found : candidates) {
            remaining.put(addressOf(found), found);
        }
        List<HsScan.Found> ordered = new ArrayList<>();
        if (previous != null) {
            for (HsScan.Found old : previous) {
                HsScan.Found current = remaining.remove(addressOf(old));
                if (current != null) {
                    ordered.add(current);
                }
            }
        }
        List<HsScan.Found> newcomers = new ArrayList<>(remaining.values());
        newcomers.sort(Comparator.comparingInt(HsScan.Found::rssi).reversed());
        for (HsScan.Found newbie : newcomers) {
            int index = 0;
            while (index < ordered.size() && ordered.get(index).rssi() >= newbie.rssi()) {
                index++;
            }
            ordered.add(index, newbie);
        }
        boolean moved = true;
        while (moved) {
            moved = false;
            for (int index = 0; index < ordered.size() - 1; index++) {
                HsScan.Found upper = ordered.get(index);
                HsScan.Found lower = ordered.get(index + 1);
                if (lower.rssi() >= upper.rssi() + REORDER_BAND_DBM) {
                    ordered.set(index, lower);
                    ordered.set(index + 1, upper);
                    moved = true;
                }
            }
        }
        return List.copyOf(ordered);
    }

    private static String addressOf(HsScan.Found found) {
        String address = found.radios().bleAddress();
        return address == null ? "" : address;
    }
}
