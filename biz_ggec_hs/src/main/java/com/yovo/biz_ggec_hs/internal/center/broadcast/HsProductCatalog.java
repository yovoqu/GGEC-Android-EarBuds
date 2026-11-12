package com.yovo.biz_ggec_hs.internal.center.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 广播识别目录。市场号只用于把一帧广播归到产品型号，不对外暴露变体。
 *
 * <p>CN / TVS / AU 都是 HS01，CN2 是 HS02。识别不出来的市场直接丢掉。</p>
 */
public final class HsProductCatalog {

    public static final String HS01 = "HS01";
    public static final String HS02 = "HS02";

    /** 单款市场的特征码。市场号同时服务 HS01 特征码和 HS02 厂商段。 */
    public record Product(int marketId, @NonNull String hs01FeatureCode) {
    }

    private static final List<Product> PRODUCTS = List.of(
            new Product(HsMarket.CN, "86103800900000100000fffe"),
            new Product(HsMarket.CN2, "86103800900000300000fffe"),
            new Product(HsMarket.TVS, "1c7b8471e31707bcad54f4c6"),
            new Product(HsMarket.AUSTRALIA, "865099000040000009200000"));

    private HsProductCatalog() {
    }

    @NonNull
    public static List<Product> all() {
        return PRODUCTS;
    }

    @Nullable
    public static Product byMarket(int marketId) {
        for (Product product : PRODUCTS) {
            if (product.marketId() == marketId) {
                return product;
            }
        }
        return null;
    }

    @Nullable
    public static Product byHs01FeatureCode(@Nullable String featureCodeHex) {
        if (featureCodeHex == null || featureCodeHex.isEmpty()) {
            return null;
        }
        for (Product product : PRODUCTS) {
            String candidate = product.hs01FeatureCode();
            // 广播里只能解出 9 字节（18 hex）特征码前缀，目录保存的是完整特征码，按前缀比较。
            if (candidate.length() >= featureCodeHex.length()
                    && candidate.regionMatches(true, 0, featureCodeHex, 0, featureCodeHex.length())) {
                return product;
            }
        }
        return null;
    }

    public static boolean isKnownMarket(int marketId) {
        return byMarket(marketId) != null;
    }

    /** 市场号到产品型号。未知市场返回空串，调用方必须丢弃这帧。 */
    @NonNull
    public static String modelId(int marketId) {
        return switch (marketId) {
            case HsMarket.CN, HsMarket.TVS, HsMarket.AUSTRALIA -> HS01;
            case HsMarket.CN2 -> HS02;
            default -> "";
        };
    }
}
