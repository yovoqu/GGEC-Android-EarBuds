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

    /**
     * 旧广播（HS01：128-bit Service UUID 特征码）识别方案：由市场号定位产品型号。
     *
     * <p>CN / TVS / AU 是 HS01（V11），CN2 是 HS02（V12）。市场号只在这一代广播里有效。</p>
     */
    @NonNull
    public static String modelIdByMarket(int marketId) {
        return modelId(marketId);
    }

    /** 新广播（HS02 厂商段）Product ID / 机型代码的取值：0 为 V11，1 为 V12（即 CN2）。 */
    public static final int PRODUCT_MODEL_V11 = 0;
    public static final int PRODUCT_MODEL_V12 = 1;

    /**
     * 新广播（HS02：厂商段）识别方案：由 Product ID（机型代码）定位产品型号。
     *
     * <p>0 → HS01（V11），1 → HS02（V12）。新广播里机型代码和市场号是两套编码，
     * 同名数字含义不同（例如旧广播市场 1 是 TVS/V11，而新广播机型 1 是 V12），
     * 因此新广播必须只认 Product ID，不能拿市场号套旧表的 switch。</p>
     *
     * <p>其它机型代码未登记，返回空串，调用方必须丢弃这帧。</p>
     */
    @NonNull
    public static String modelIdByProductModel(int productModel) {
        return switch (productModel) {
            case PRODUCT_MODEL_V11 -> HS01;
            case PRODUCT_MODEL_V12 -> HS02;
            default -> "";
        };
    }
}
