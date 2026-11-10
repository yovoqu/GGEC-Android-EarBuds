package com.yovo.biz_ggec_hs.internal.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.model.broadcast.Hs01Identity;
import com.yovo.biz_ggec_hs.model.broadcast.HsGeneration;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.UUID;

/**
 * HS01：128-bit Service UUID 的前 9 字节特征码、第 7 字节颜色、末 6 字节经典地址。
 */
final class Hs01BroadcastDecoder implements HsBroadcastDecoder {
    @NonNull
    @Override
    public HsGeneration generation() {
        return HsGeneration.HS01;
    }

    @NonNull
    @Override
    public Claim claim(@NonNull HsScanSnapshot snapshot) {
        for (UUID uuid : snapshot.serviceUuids()) {
            Hs01Identity identity = match(snapshot.bleAddress(), uuid);
            if (identity != null) {
                return Claim.identified(identity);
            }
        }
        return Claim.unrecognized();
    }

    @Nullable
    private static Hs01Identity match(@Nullable String bleAddress, @Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        byte[] uuidBytes = ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
        byte[] prefix = new byte[9];
        for (int index = 0; index < prefix.length; index++) {
            prefix[index] = uuidBytes[15 - index];
        }
        HsFeatureCatalog.Entry entry = HsFeatureCatalog.match(prefix);
        if (entry == null) {
            return null;
        }
        StringBuilder mac = new StringBuilder();
        for (int index = 5; index >= 0; index--) {
            mac.append(String.format(Locale.US, "%02X", uuidBytes[index] & 0xFF));
            if (index > 0) {
                mac.append(':');
            }
        }
        String color = String.format(Locale.US, "%02X", uuidBytes[6] & 0xFF);
        return new Hs01Identity(
                bleAddress,
                mac.toString(),
                color,
                entry.featureCode,
                uuid,
                entry.marketId);
    }
}
