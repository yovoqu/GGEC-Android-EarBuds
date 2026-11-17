package com.yovo.lib_bluetooth.internal.ble.platform;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.ble.scan.BleScanConfig;
import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanFilter;
import com.yovo.lib_bluetooth.model.ble.BleScanMatchMode;
import com.yovo.lib_bluetooth.model.ble.BleScanMode;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link BleScanConfig} → Android {@link ScanSettings} / {@link ScanFilter} 的唯一转换点。
 *
 * <p>只把“能在原生层判定”的过滤条件下推；纯软件条件（minRssi / deviceNameContains /
 * requireConnectable）留在 {@code YoBleScanFilterMatcher}。</p>
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidScanSettingsMapper {

    private YoBleAndroidScanSettingsMapper() {
    }

    @NonNull
    public static ScanSettings toScanSettings(@NonNull BleScanConfig config) {
        ScanSettings.Builder builder = new ScanSettings.Builder()
                .setScanMode(toScanMode(config.scanMode()))
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(toMatchMode(config.matchMode()))
                .setNumOfMatches(config.numOfMatches())
                .setReportDelay(config.reportDelayMillis());

        if (AndroidPlatform.supportsLePhy()) {
            builder.setLegacy(config.legacyOnly());
            if (config.phy() == BlePhy.ALL_SUPPORTED) {
                builder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED);
            } else if (config.phy() == BlePhy.LE_CODED) {
                builder.setPhy(BluetoothDevice.PHY_LE_CODED);
            } else {
                builder.setPhy(BluetoothDevice.PHY_LE_1M);
            }
        }
        return builder.build();
    }

    /**
     * 原生层拒绝主设置（PHY / numOfMatches 等机型不支持）时使用的软件过滤扫描设置：
     * 不开批量上报、不指定 match 模式，结果由软件层合并。
     */
    @NonNull
    public static ScanSettings toSoftwareFilterScanSettings(@NonNull BleScanConfig config) {
        return new ScanSettings.Builder()
                .setScanMode(toScanMode(config.scanMode()))
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(0L)
                .build();
    }

    @NonNull
    public static List<ScanFilter> toScanFilters(@NonNull List<BleScanFilter> filters) {
        List<ScanFilter> out = new ArrayList<>(filters.size());
        for (BleScanFilter filter : filters) {
            if (filter.isEmpty()) {
                continue;
            }
            ScanFilter.Builder builder = new ScanFilter.Builder();
            // 只有至少一个字段真正下推到原生 ScanFilter 时才构建。纯软件过滤（minRssi /
            // deviceNameContains / requireConnectable）不产生任何原生字段，若仍 build() 会得到一个
            // “空 ScanFilter”，在 AOSP 中匹配所有设备并废掉同组其它原生过滤。
            boolean anyNativeCriterion = false;
            if (filter.deviceAddress() != null) {
                builder.setDeviceAddress(filter.deviceAddress());
                anyNativeCriterion = true;
            }
            // 原生 ScanFilter 只做 deviceName 精确匹配；有 deviceNameContains 时交由软件匹配器。
            if (filter.deviceName() != null && filter.deviceNameContains() == null) {
                builder.setDeviceName(filter.deviceName());
                anyNativeCriterion = true;
            }
            if (filter.serviceUuid() != null) {
                if (filter.serviceUuidMask() != null) {
                    builder.setServiceUuid(new ParcelUuid(filter.serviceUuid()),
                            new ParcelUuid(filter.serviceUuidMask()));
                } else {
                    builder.setServiceUuid(new ParcelUuid(filter.serviceUuid()));
                }
                anyNativeCriterion = true;
            }
            if (filter.solicitationUuid() != null
                    && AndroidPlatform.supportsScanSolicitationUuid()) {
                if (filter.solicitationUuidMask() != null) {
                    builder.setServiceSolicitationUuid(
                            new ParcelUuid(filter.solicitationUuid()),
                            new ParcelUuid(filter.solicitationUuidMask()));
                } else {
                    builder.setServiceSolicitationUuid(new ParcelUuid(filter.solicitationUuid()));
                }
                anyNativeCriterion = true;
            }
            if (filter.serviceDataUuid() != null && filter.serviceData() != null) {
                byte[] serviceData = filter.serviceData();
                byte[] serviceDataMask = filter.serviceDataMask();
                if (serviceDataMask != null && serviceDataMask.length == serviceData.length) {
                    builder.setServiceData(
                            new ParcelUuid(filter.serviceDataUuid()), serviceData, serviceDataMask);
                } else if (serviceDataMask == null) {
                    builder.setServiceData(new ParcelUuid(filter.serviceDataUuid()), serviceData);
                }
                anyNativeCriterion = true;
            }
            if (filter.manufacturerId() != null && filter.manufacturerData() != null) {
                byte[] manufacturerData = filter.manufacturerData();
                byte[] manufacturerDataMask = filter.manufacturerDataMask();
                if (manufacturerDataMask != null
                        && manufacturerDataMask.length == manufacturerData.length) {
                    builder.setManufacturerData(
                            filter.manufacturerId(), manufacturerData, manufacturerDataMask);
                } else if (manufacturerDataMask == null) {
                    builder.setManufacturerData(filter.manufacturerId(), manufacturerData);
                }
                anyNativeCriterion = true;
            }
            if (!anyNativeCriterion) {
                continue;
            }
            out.add(builder.build());
        }
        return out;
    }

    private static int toScanMode(@NonNull BleScanMode mode) {
        switch (mode) {
            case OPPORTUNISTIC:
                return ScanSettings.SCAN_MODE_OPPORTUNISTIC;
            case LOW_POWER:
                return ScanSettings.SCAN_MODE_LOW_POWER;
            case LOW_LATENCY:
                return ScanSettings.SCAN_MODE_LOW_LATENCY;
            case BALANCED:
                return ScanSettings.SCAN_MODE_BALANCED;
            default:
                throw new IllegalStateException("unhandled BleScanMode: " + mode);
        }
    }

    private static int toMatchMode(@NonNull BleScanMatchMode mode) {
        switch (mode) {
            case STICKY:
                return ScanSettings.MATCH_MODE_STICKY;
            case AGGRESSIVE:
                return ScanSettings.MATCH_MODE_AGGRESSIVE;
            default:
                throw new IllegalStateException("unhandled BleScanMatchMode: " + mode);
        }
    }
}
