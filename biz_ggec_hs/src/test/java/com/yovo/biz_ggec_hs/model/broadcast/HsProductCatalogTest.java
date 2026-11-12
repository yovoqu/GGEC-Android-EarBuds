package com.yovo.biz_ggec_hs.model.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 产品目录回归：每款产品（市场）显式规则的可查性与一致性。
 */
public final class HsProductCatalogTest {

    @Test
    public void catalogHasExactlyFourProducts() {
        assertEquals(4, HsProductCatalog.all().size());
    }

    @Test
    public void byMarketReturnsExplicitProductRule() {
        HsProductCatalog.Product cn = HsProductCatalog.byMarket(HsMarket.CN);
        assertNotNull(cn);
        assertEquals("v1", cn.variantId());

        HsProductCatalog.Product cn2 = HsProductCatalog.byMarket(HsMarket.CN2);
        assertNotNull(cn2);
        assertEquals("v1", cn2.variantId());

        assertEquals("v2", HsProductCatalog.byMarket(HsMarket.TVS).variantId());
        assertEquals("v3", HsProductCatalog.byMarket(HsMarket.AUSTRALIA).variantId());
    }

    @Test
    public void byHs01FeaturePrefixMatchesProduct() {
        // 广播只能给出 9 字节(18 hex)前缀
        HsProductCatalog.Product cn = HsProductCatalog.byHs01FeatureCode("861038009000001000");
        assertNotNull(cn);
        assertEquals(HsMarket.CN, cn.marketId());

        assertNull(HsProductCatalog.byHs01FeatureCode("000000000000000000"));
    }

    @Test
    public void isKnownMarketRejectsUnsupported() {
        assertTrue(HsProductCatalog.isKnownMarket(HsMarket.CN));
        assertTrue(HsProductCatalog.isKnownMarket(HsMarket.CN2));
        assertTrue(HsProductCatalog.isKnownMarket(HsMarket.TVS));
        assertTrue(HsProductCatalog.isKnownMarket(HsMarket.AUSTRALIA));
        assertFalse(HsProductCatalog.isKnownMarket(9));
        assertFalse(HsProductCatalog.isKnownMarket(HsMarket.UNKNOWN));
    }
}
