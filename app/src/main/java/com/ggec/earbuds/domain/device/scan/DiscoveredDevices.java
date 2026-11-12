package com.ggec.earbuds.domain.device.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.ScanResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次扫描会话里的设备观察表。同一台设备（机型 + BLE 地址）只有一行，
 * 每一帧广播都覆盖这一行的最新信号和广播名。
 *
 * <p>表本身不做界面节流，也不按信号幅度丢样本。调用方只在自己的线程里使用它。</p>
 */
public final class DiscoveredDevices {

    private final List<ScanResult> devices = new ArrayList<>();

    public void clear() {
        devices.clear();
    }

    public int size() {
        return devices.size();
    }

    @NonNull
    public List<ScanResult> snapshot() {
        return List.copyOf(devices);
    }

    @Nullable
    public ScanResult find(@NonNull String modelId, @NonNull String bleMac) {
        for (ScanResult device : devices) {
            if (device.modelId().equals(modelId) && device.bleMac().equalsIgnoreCase(bleMac)) {
                return device;
            }
        }
        return null;
    }

    /**
     * 写入一帧观察。新设备插入；已有设备用这一帧的 RSSI，并用更新后的广播名替换占位名。
     *
     * @return 表内容是否变化
     */
    public boolean accept(@Nullable ScanResult incoming) {
        if (incoming == null) {
            return false;
        }
        int index = indexOf(incoming);
        if (index < 0) {
            devices.add(incoming);
            return true;
        }
        ScanResult merged = merge(devices.get(index), incoming);
        if (merged.equals(devices.get(index))) {
            return false;
        }
        devices.set(index, merged);
        return true;
    }

    public boolean remove(@Nullable ScanResult removed) {
        if (removed == null) {
            return false;
        }
        int index = indexOf(removed);
        if (index < 0) {
            return false;
        }
        devices.remove(index);
        return true;
    }

    private int indexOf(@NonNull ScanResult candidate) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).sameDevice(candidate)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    private static ScanResult merge(@NonNull ScanResult existing, @NonNull ScanResult incoming) {
        return new ScanResult(
                incoming.modelId(),
                incoming.variantId().isEmpty() ? existing.variantId() : incoming.variantId(),
                liveName(existing, incoming),
                incoming.bleMac().isEmpty() ? existing.bleMac() : incoming.bleMac(),
                incoming.rssi());
    }

    /**
     * 后到的广播本地名覆盖旧名。只有机型占位名（与 modelId 相同）不能把已经解析出的名字盖掉。
     */
    @NonNull
    private static String liveName(@NonNull ScanResult existing, @NonNull ScanResult incoming) {
        String next = incoming.bleName();
        if (next.isEmpty()) {
            return existing.bleName();
        }
        boolean placeholder = next.equalsIgnoreCase(incoming.modelId());
        boolean existingIsReal = !existing.bleName().isEmpty()
                && !existing.bleName().equalsIgnoreCase(existing.modelId());
        if (placeholder && existingIsReal) {
            return existing.bleName();
        }
        return next;
    }
}
