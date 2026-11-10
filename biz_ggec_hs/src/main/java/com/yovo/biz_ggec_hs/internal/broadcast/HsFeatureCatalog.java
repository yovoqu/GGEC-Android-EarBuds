package com.yovo.biz_ggec_hs.internal.broadcast;

import com.yovo.biz_ggec_hs.model.broadcast.HsMarket;

import java.util.Arrays;
import java.util.List;

/**
 * HS01 特征码目录。新增市场只加一条目录，不改匹配过程。
 */
final class HsFeatureCatalog {
    private static final List<Entry> ENTRIES = List.of(
            entry("86103800900000100000fffe", HsMarket.CN),
            entry("86103800900000300000fffe", HsMarket.CN2),
            entry("1c7b8471e31707bcad54f4c6", HsMarket.TVS),
            entry("865099000040000009200000", HsMarket.AUSTRALIA));

    private HsFeatureCatalog() {
    }

    static Entry match(byte[] prefix) {
        for (Entry entry : ENTRIES) {
            if (Arrays.equals(prefix, entry.prefix)) {
                return entry;
            }
        }
        return null;
    }

    private static Entry entry(String featureCode, int marketId) {
        return new Entry(featureCode, marketId, hexToBytes(featureCode.substring(0, 18)));
    }

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }

    static final class Entry {
        final String featureCode;
        final int marketId;
        final byte[] prefix;

        Entry(String featureCode, int marketId, byte[] prefix) {
            this.featureCode = featureCode;
            this.marketId = marketId;
            this.prefix = prefix;
        }
    }
}
