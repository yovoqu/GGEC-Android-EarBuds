package com.ggec.earbuds.data.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.Capability;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.port.DeviceProfileCatalog;

import org.junit.Test;

public final class DeviceProfileCatalogImplTest {

    private final DeviceProfileCatalog catalog = new DeviceProfileCatalogImpl();

    @Test
    public void exposesThreeProjectNames() {
        assertEquals(3, catalog.models().size());
        assertEquals("HS01", catalog.models().get(0).id());
        assertEquals("HS02", catalog.models().get(1).id());
        assertEquals("WY01", catalog.models().get(2).id());
    }

    @Test
    public void hs01HasThreeVariants() {
        assertEquals(3, catalog.variants("HS01").size());
    }

    @Test
    public void hs02HasSingleVariant() {
        assertEquals(1, catalog.variants("HS02").size());
    }

    @Test
    public void wy01HasNoDeclaredVariants() {
        assertTrue(catalog.variants("WY01").isEmpty());
    }

    @Test
    public void hs02IsCapabilitySuperset() {
        DeviceProfile hs01 = catalog.defaultProfile("HS01");
        DeviceProfile hs02 = catalog.defaultProfile("HS02");
        assertTrue(hs02.supports(Capability.DUAL_CONNECTION));
        assertTrue(hs02.supports(Capability.CUSTOM_EQ));
        assertFalse(hs01.supports(Capability.DUAL_CONNECTION));
        assertFalse(hs01.supports(Capability.CUSTOM_EQ));
        assertTrue(hs02.capabilities().size() > hs01.capabilities().size());
    }

    @Test
    public void profileLookupReturnsVariant() {
        DeviceProfile profile = catalog.profile("HS01", "v2").orElseThrow();
        assertEquals("v2", profile.variant().id());
        assertEquals("HS01", profile.model().id());
    }

    @Test
    public void unknownVariantIsEmpty() {
        assertTrue(catalog.profile("HS01", "nope").isEmpty());
    }
}
