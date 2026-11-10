package com.yovo.biz_ggec_hs.internal.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.model.broadcast.Hs02Identity;
import com.yovo.biz_ggec_hs.model.broadcast.HsGeneration;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * HS02：厂商号 0xFFFF 且 Magic 为「HC」即认领。基础帧、补充帧和完整名称齐了才是设备。
 */
final class Hs02BroadcastDecoder implements HsBroadcastDecoder {
    private static final int COMPANY_ID = 0xFFFF;
    private static final byte MAGIC_0 = 0x48;
    private static final byte MAGIC_1 = 0x43;
    private static final int PROTOCOL_VERSION = 1;
    private static final int BASE_HEADER_LENGTH = 11;
    private static final int TYPE_CLASSIC_MAC = 0x0A;
    private static final int TYPE_MARKET = 0x0B;

    @NonNull
    @Override
    public HsGeneration generation() {
        return HsGeneration.HS02;
    }

    @NonNull
    @Override
    public Claim claim(@NonNull HsScanSnapshot snapshot) {
        List<byte[]> segments = new ArrayList<>();
        String completeName = null;
        for (HsAdFields.Field field : HsAdFields.decode(snapshot.rawAdvertising())) {
            if (field.type == HsAdFields.COMPLETE_NAME) {
                completeName = HsAdFields.decodeUtf8(field.data);
            } else if (field.type == HsAdFields.MANUFACTURER && claimsSegment(field.data)) {
                segments.add(field.data);
            }
        }
        if (completeName == null || completeName.isEmpty()) {
            completeName = blankToNull(snapshot.deviceName());
        }
        if (segments.isEmpty()) {
            return Claim.unrecognized();
        }

        byte[] base = null;
        List<byte[]> supplements = new ArrayList<>();
        for (byte[] segment : segments) {
            if (looksLikeBase(segment)) {
                base = segment;
                supplements.clear();
            } else {
                supplements.add(segment);
            }
        }
        if (base == null) {
            return Claim.incomplete();
        }
        Parsed parsed = parse(base, supplements);
        if (parsed == null || completeName == null) {
            return Claim.incomplete();
        }
        return Claim.identified(new Hs02Identity(
                snapshot.bleAddress(),
                parsed.classicAddress,
                completeName,
                parsed.color,
                parsed.marketId,
                parsed.deviceType,
                parsed.productModel,
                parsed.pairable,
                parsed.reconnect));
    }

    static boolean claimsSegment(@Nullable byte[] data) {
        return hasMagic(data) && readCompanyId(data) == COMPANY_ID;
    }

    static boolean looksLikeBase(@Nullable byte[] data) {
        return hasMagic(data)
                && data.length >= BASE_HEADER_LENGTH
                && (data[4] & 0xFF) == PROTOCOL_VERSION
                && data[9] == 0
                && data[10] == 0;
    }

    @Nullable
    private static Parsed parse(byte[] base, List<byte[]> supplements) {
        if (!looksLikeBase(base)) {
            return null;
        }
        int companyId = readCompanyId(base);
        TlvCollector collector = new TlvCollector();
        if (!collector.consume(base, BASE_HEADER_LENGTH)) {
            return null;
        }
        for (byte[] supplement : supplements) {
            if (!hasMagic(supplement) || readCompanyId(supplement) != companyId) {
                return null;
            }
            if (!collector.consume(supplement, 4)) {
                return null;
            }
        }
        byte[] mac = collector.mac;
        byte[] market = collector.market;
        if (mac == null || market == null || mac.length != 6 || market.length != 1 || !isValidMac(mac)) {
            return null;
        }
        int status = base[6] & 0xFF;
        return new Parsed(
                formatMac(mac),
                market[0] & 0xFF,
                String.format(Locale.US, "%02X", base[8] & 0xFF),
                base[5] & 0xFF,
                base[7] & 0xFF,
                (status & 0x02) != 0,
                (status & 0x01) != 0);
    }

    private static boolean hasMagic(@Nullable byte[] data) {
        return data != null
                && data.length >= 4
                && data[2] == MAGIC_0
                && data[3] == MAGIC_1;
    }

    private static int readCompanyId(byte[] data) {
        return (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
    }

    private static boolean isValidMac(byte[] mac) {
        boolean allZero = true;
        boolean allOnes = true;
        for (byte value : mac) {
            allZero &= value == 0;
            allOnes &= (value & 0xFF) == 0xFF;
        }
        return !allZero && !allOnes;
    }

    private static String formatMac(byte[] mac) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < mac.length; index++) {
            if (index > 0) {
                builder.append(':');
            }
            builder.append(String.format(Locale.US, "%02X", mac[index] & 0xFF));
        }
        return builder.toString();
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static final class TlvCollector {
        private byte[] mac;
        private byte[] market;

        private boolean consume(byte[] payload, int offset) {
            int index = offset;
            while (index < payload.length) {
                if (index + 2 > payload.length) {
                    return false;
                }
                int type = payload[index] & 0xFF;
                int length = payload[index + 1] & 0xFF;
                int valueEnd = index + 2 + length;
                if (valueEnd > payload.length) {
                    return false;
                }
                byte[] value = Arrays.copyOfRange(payload, index + 2, valueEnd);
                if (type == TYPE_CLASSIC_MAC || type == TYPE_MARKET) {
                    byte[] previous = type == TYPE_CLASSIC_MAC ? mac : market;
                    if (previous != null && !Arrays.equals(previous, value)) {
                        return false;
                    }
                    if (type == TYPE_CLASSIC_MAC) {
                        mac = value;
                    } else {
                        market = value;
                    }
                }
                index = valueEnd;
            }
            return true;
        }
    }

    private static final class Parsed {
        private final String classicAddress;
        private final int marketId;
        private final String color;
        private final int deviceType;
        private final int productModel;
        private final boolean pairable;
        private final boolean reconnect;

        private Parsed(String classicAddress,
                       int marketId,
                       String color,
                       int deviceType,
                       int productModel,
                       boolean pairable,
                       boolean reconnect) {
            this.classicAddress = classicAddress;
            this.marketId = marketId;
            this.color = color;
            this.deviceType = deviceType;
            this.productModel = productModel;
            this.pairable = pairable;
            this.reconnect = reconnect;
        }
    }
}
