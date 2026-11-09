package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleScanRecord;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 纯 Java BLE AD Type (0x01..0xFF) 广播载荷解析器，零 Android Framework 依赖。
 *
 * <p>对齐 Bluetooth Core Specification CSS &amp; Polidea/Nordic AD 解析规则，
 * 针对截断字段、异常长度与同厂商 ID 多段广播进行边界防御。</p>
 */
public final class YoBleScanRecordParser {

    public static final int DATA_TYPE_FLAGS = 0x01;
    public static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL = 0x02;
    public static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE = 0x03;
    public static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL = 0x04;
    public static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE = 0x05;
    public static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL = 0x06;
    public static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 0x07;
    public static final int DATA_TYPE_LOCAL_NAME_SHORT = 0x08;
    public static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 0x09;
    public static final int DATA_TYPE_TX_POWER_LEVEL = 0x0A;
    public static final int DATA_TYPE_SERVICE_SOLICITATION_UUIDS_16_BIT = 0x14;
    public static final int DATA_TYPE_SERVICE_SOLICITATION_UUIDS_128_BIT = 0x15;
    public static final int DATA_TYPE_SERVICE_DATA_16_BIT = 0x16;
    public static final int DATA_TYPE_SERVICE_SOLICITATION_UUIDS_32_BIT = 0x1F;
    public static final int DATA_TYPE_SERVICE_DATA_32_BIT = 0x20;
    public static final int DATA_TYPE_SERVICE_DATA_128_BIT = 0x21;
    public static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF;

    public static final UUID BASE_UUID =
            UUID.fromString("00000000-0000-1000-8000-00805F9B34FB");

    private static final int UUID_BYTES_16 = 2;
    private static final int UUID_BYTES_32 = 4;
    private static final int UUID_BYTES_128 = 16;

    private YoBleScanRecordParser() {
    }

    @NonNull
    public static BleScanRecord parse(@Nullable byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            return BleScanRecord.empty();
        }
        int advertiseFlags = -1;
        List<UUID> serviceUuids = new ArrayList<>();
        List<UUID> solicitationUuids = new ArrayList<>();
        Map<UUID, byte[]> serviceData = new LinkedHashMap<>();
        Map<Integer, byte[]> manufacturerData = new LinkedHashMap<>();
        int txPowerLevel = Integer.MIN_VALUE;
        String localName = null;
        boolean hasCompleteName = false;

        int pos = 0;
        while (pos < rawBytes.length) {
            int length = rawBytes[pos++] & 0xFF;
            if (length == 0) {
                break;
            }
            if (pos >= rawBytes.length) {
                break;
            }
            int dataLength = length - 1;
            int fieldType = rawBytes[pos++] & 0xFF;
            if (dataLength < 0 || pos + dataLength > rawBytes.length) {
                break;
            }

            switch (fieldType) {
                case DATA_TYPE_FLAGS:
                    if (dataLength >= 1) {
                        advertiseFlags = rawBytes[pos] & 0xFF;
                    }
                    break;
                case DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL:
                case DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE:
                    parseUuids(rawBytes, pos, dataLength, UUID_BYTES_16, serviceUuids);
                    break;
                case DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL:
                case DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE:
                    parseUuids(rawBytes, pos, dataLength, UUID_BYTES_32, serviceUuids);
                    break;
                case DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL:
                case DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE:
                    parseUuids(rawBytes, pos, dataLength, UUID_BYTES_128, serviceUuids);
                    break;
                case DATA_TYPE_SERVICE_SOLICITATION_UUIDS_16_BIT:
                    parseUuids(rawBytes, pos, dataLength, UUID_BYTES_16, solicitationUuids);
                    break;
                case DATA_TYPE_SERVICE_SOLICITATION_UUIDS_32_BIT:
                    parseUuids(rawBytes, pos, dataLength, UUID_BYTES_32, solicitationUuids);
                    break;
                case DATA_TYPE_SERVICE_SOLICITATION_UUIDS_128_BIT:
                    parseUuids(rawBytes, pos, dataLength, UUID_BYTES_128, solicitationUuids);
                    break;
                case DATA_TYPE_LOCAL_NAME_SHORT:
                    if (!hasCompleteName) {
                        localName = new String(rawBytes, pos, dataLength, StandardCharsets.UTF_8);
                    }
                    break;
                case DATA_TYPE_LOCAL_NAME_COMPLETE:
                    localName = new String(rawBytes, pos, dataLength, StandardCharsets.UTF_8);
                    hasCompleteName = true;
                    break;
                case DATA_TYPE_TX_POWER_LEVEL:
                    if (dataLength >= 1) {
                        txPowerLevel = rawBytes[pos]; // signed int8 dBm
                    }
                    break;
                case DATA_TYPE_SERVICE_DATA_16_BIT:
                case DATA_TYPE_SERVICE_DATA_32_BIT:
                case DATA_TYPE_SERVICE_DATA_128_BIT:
                    int uuidLen = fieldType == DATA_TYPE_SERVICE_DATA_16_BIT
                            ? UUID_BYTES_16
                            : (fieldType == DATA_TYPE_SERVICE_DATA_32_BIT ? UUID_BYTES_32 : UUID_BYTES_128);
                    if (dataLength >= uuidLen) {
                        UUID svcUuid = parseUuid(rawBytes, pos, uuidLen);
                        byte[] payload = extract(rawBytes, pos + uuidLen, dataLength - uuidLen);
                        serviceData.put(svcUuid, payload);
                    }
                    break;
                case DATA_TYPE_MANUFACTURER_SPECIFIC_DATA:
                    if (dataLength >= 2) {
                        int manufacturerId = ((rawBytes[pos + 1] & 0xFF) << 8)
                                | (rawBytes[pos] & 0xFF);
                        byte[] chunk = extract(rawBytes, pos + 2, dataLength - 2);
                        byte[] prev = manufacturerData.get(manufacturerId);
                        if (prev == null) {
                            manufacturerData.put(manufacturerId, chunk);
                        } else {
                            byte[] merged = new byte[prev.length + chunk.length];
                            System.arraycopy(prev, 0, merged, 0, prev.length);
                            System.arraycopy(chunk, 0, merged, prev.length, chunk.length);
                            manufacturerData.put(manufacturerId, merged);
                        }
                    }
                    break;
                default:
                    break;
            }
            pos += dataLength;
        }

        return new BleScanRecord(
                advertiseFlags,
                serviceUuids,
                solicitationUuids,
                serviceData,
                manufacturerData,
                txPowerLevel,
                localName,
                rawBytes);
    }

    private static void parseUuids(@NonNull byte[] bytes,
                                   int offset,
                                   int dataLength,
                                   int uuidLength,
                                   @NonNull List<UUID> dest) {
        int remaining = dataLength;
        int cur = offset;
        while (remaining >= uuidLength) {
            dest.add(parseUuid(bytes, cur, uuidLength));
            cur += uuidLength;
            remaining -= uuidLength;
        }
    }

    @NonNull
    public static UUID parseUuid(@NonNull byte[] bytes, int offset, int length) {
        if (length == UUID_BYTES_128) {
            ByteBuffer buf = ByteBuffer.wrap(bytes, offset, UUID_BYTES_128)
                    .order(ByteOrder.LITTLE_ENDIAN);
            long lsb = buf.getLong();
            long msb = buf.getLong();
            return new UUID(msb, lsb);
        }
        long shortUuid;
        if (length == UUID_BYTES_16) {
            shortUuid = (bytes[offset] & 0xFFL) | ((bytes[offset + 1] & 0xFFL) << 8);
        } else if (length == UUID_BYTES_32) {
            shortUuid = (bytes[offset] & 0xFFL)
                    | ((bytes[offset + 1] & 0xFFL) << 8)
                    | ((bytes[offset + 2] & 0xFFL) << 16)
                    | ((bytes[offset + 3] & 0xFFL) << 24);
        } else {
            throw new IllegalArgumentException("Invalid UUID byte length: " + length);
        }
        long msb = BASE_UUID.getMostSignificantBits() + (shortUuid << 32);
        long lsb = BASE_UUID.getLeastSignificantBits();
        return new UUID(msb, lsb);
    }

    @NonNull
    private static byte[] extract(@NonNull byte[] src, int offset, int length) {
        byte[] out = new byte[Math.max(0, length)];
        if (length > 0) {
            System.arraycopy(src, offset, out, 0, length);
        }
        return out;
    }
}
