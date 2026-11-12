package com.yovo.biz_ggec_hs.model.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 产品目录：**每款产品（按市场划分）显式声明自己的识别属性**，作为扫描识别与 UI 变体的单一事实来源。
 *
 * <p>每款产品一行，不写兼容分支：</p>
 * <ul>
 *   <li>{@code hs01FeatureCode}：HS01（旧代 128-bit Service UUID）的产品特征码前缀，用于按市场识别 HS01；</li>
 *   <li>{@code marketId}：HS02（新代 0xFFFF "HC" 厂商数据）在 TLV 0x0B 中携带的市场号，用于按市场识别 HS02；</li>
 *   <li>{@code variantId}：附近设备列表使用的 UI 变体标识。</li>
 * </ul>
 *
 * <p>新增一款产品 = 在此加一行；识别逻辑不随产品数量增长而增加分支。</p>
 */
public final class HsProductCatalog {

    /** 单款产品的显式定义。 */
    public record Product(int marketId,
                          @NonNull String variantId,
                          @NonNull String hs01FeatureCode) {
    }

    private static final List<Product> PRODUCTS = List.of(
            new Product(HsMarket.CN, "v1", "86103800900000100000fffe"),
            new Product(HsMarket.CN2, "v1", "86103800900000300000fffe"),
            new Product(HsMarket.TVS, "v2", "1c7b8471e31707bcad54f4c6"),
            new Product(HsMarket.AUSTRALIA, "v3", "865099000040000009200000"));

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
}
