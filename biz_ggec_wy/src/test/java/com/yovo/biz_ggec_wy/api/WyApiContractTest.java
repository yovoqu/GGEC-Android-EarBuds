package com.yovo.biz_ggec_wy.api;

import com.yovo.biz_ggec_wy.internal.center.catalog.WyCatalogImpl;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WyApiContractTest {

    @Test
    public void testCatalogDeclaresWy01() {
        WyCatalogImpl catalog = new WyCatalogImpl();
        List<WyDevice.Product> models = catalog.models();
        assertEquals(1, models.size());

        WyDevice.Product product = catalog.product("WY01");
        assertEquals("WY01", product.modelId());
        assertEquals("WY", product.projectId());
        assertEquals(WyDevice.Type.EARBUDS, product.deviceType());

        assertTrue(catalog.supports("WY01", WyDevice.Capability.CONNECTION));
        assertTrue(catalog.supports("WY01", WyDevice.Capability.RECORD_STREAM));
        assertTrue(catalog.supports("WY01", WyDevice.Capability.DEVICE_INFO));
        assertTrue(catalog.supports("WY01", WyDevice.Capability.BATTERY));
    }

    @Test
    public void testFeatures() {
        WyCatalogImpl catalog = new WyCatalogImpl();
        WyDevice.Feature feature = catalog.feature("WY01", WyDevice.Capability.RECORD_STREAM);
        assertNotNull(feature);
        assertEquals(WyDevice.Group.RECORD, feature.group());
        assertEquals(WyDevice.Form.ACTION, feature.kind());
    }
}
