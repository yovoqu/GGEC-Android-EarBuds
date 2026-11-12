package com.yovo.biz_ggec_hs.internal.scan;





import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsScan;
import com.yovo.biz_ggec_hs.internal.center.port.HsRawAdvert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全量设备表：按 BLE 地址去重、RSSI 滑动平均、{@code lastSeenAt}、TTL 淘汰。
 *
 * <p>纯数据，不做规则求值、不排序、不发回调；lost 的唯一来源就是本表（原扫描账本已并入，
 * 模块内不再有第二套 lost）。</p>
 *
 * <p>时间一律由调用方传入，便于用固定时刻做单测。</p>
 */
final class HsNearbyRegistry {

    /** RSSI 滑动平均窗口（帧数）。扫描中的消失只认传输层 lost，本表不做超时淘汰。 */
    static final int RSSI_WINDOW = 8;

    /** 表内一条设备记录。 */
    static final class Entry {
        private final String address;
        private String modelId = "";
        private HsDevice.Radios radios = HsDevice.Radios.none();
        private HsDevice.Type deviceType;
        private String projectId;
        private boolean connectable;
        private long lastSeenAtMillis;
        private final int[] rssiSamples = new int[RSSI_WINDOW];
        private int sampleCount;
        private int sampleIndex;

        private Entry(String address, long lastSeenAtMillis) {
            this.address = address;
            this.lastSeenAtMillis = lastSeenAtMillis;
        }

        String address() {
            return address;
        }

        String modelId() {
            return modelId;
        }

        HsDevice.Type deviceType() {
            return deviceType;
        }

        String projectId() {
            return projectId;
        }

        boolean connectable() {
            return connectable;
        }

        long lastSeenAtMillis() {
            return lastSeenAtMillis;
        }

        /** 平滑后的 RSSI：窗口内样本均值（向下取整），窗口未满时按已有样本算。 */
        int rssi() {
            if (sampleCount == 0) {
                return 0;
            }
            int sum = 0;
            for (int index = 0; index < sampleCount; index++) {
                sum += rssiSamples[index];
            }
            return sum / sampleCount;
        }

        HsScan.Found toDevice() {
            return new HsScan.Found(modelId, radios, rssi());
        }

        private void fold(HsRawAdvert advert, HsScan.Found device) {
            modelId = device.modelId() == null ? "" : device.modelId();
            radios = merge(radios, device.radios());
            connectable = advert.connectable();
            lastSeenAtMillis = advert.observedAtMillis();
            rssiSamples[sampleIndex] = advert.rssi();
            sampleIndex = (sampleIndex + 1) % RSSI_WINDOW;
            if (sampleCount < RSSI_WINDOW) {
                sampleCount++;
            }
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    synchronized void clear() {
        entries.clear();
    }

    synchronized int size() {
        return entries.size();
    }

    /**
     * 记录一帧已识别设备。
     *
     * @param model 目录里解析出的产品型号；未登记型号传 {@code null}
     * @return true 表示这是新地址
     */
    synchronized boolean record(HsRawAdvert advert, HsScan.Found device, HsDevice.Product model) {
        String address = advert.address();
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("广播缺少 BLE 地址");
        }
        Entry entry = entries.get(address);
        boolean created = entry == null;
        if (created) {
            entry = new Entry(address, advert.observedAtMillis());
            entries.put(address, entry);
        }
        entry.fold(advert, device);
        entry.deviceType = model == null ? null : model.deviceType();
        entry.projectId = model == null ? null : model.projectId();
        return created;
    }

    synchronized List<Entry> entries() {
        return List.copyOf(new ArrayList<>(entries.values()));
    }

    /** 传输层报告某地址消失。扫描中的消失只走这里。 */
    synchronized Entry remove(String address) {
        return entries.remove(address);
    }

    /** 后到的空字段不覆盖已经上报的那一侧。 */
    private static HsDevice.Radios merge(HsDevice.Radios current, HsDevice.Radios incoming) {
        if (incoming == null) {
            return current;
        }
        return new HsDevice.Radios(
                keep(current.bleAddress(), incoming.bleAddress()),
                keep(current.bleName(), incoming.bleName()),
                keep(current.classicAddress(), incoming.classicAddress()),
                keep(current.classicName(), incoming.classicName()));
    }

    private static String keep(String current, String incoming) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming.trim();
        }
        return current == null ? "" : current;
    }
}
