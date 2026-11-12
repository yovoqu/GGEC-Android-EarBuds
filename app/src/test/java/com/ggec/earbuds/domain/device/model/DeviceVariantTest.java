package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** 变种的构造契约：没有调试标签写空串，null 在构造点抛出。 */
public final class DeviceVariantTest {

    @Test
    public void nullRequiredFieldsAreRejectedAtConstruction() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> new DeviceVariant(null, "HS01", "")).getMessage());
        assertEquals("modelId", assertThrows(NullPointerException.class,
                () -> new DeviceVariant("v1", null, "")).getMessage());
        assertEquals("label", assertThrows(NullPointerException.class,
                () -> new DeviceVariant("v1", "HS01", null)).getMessage());
    }

    @Test
    public void factoryWritesEmptyLabelExplicitly() {
        DeviceVariant variant = DeviceVariant.of("HS01", "v1");

        assertEquals("v1", variant.id());
        assertEquals("HS01", variant.modelId());
        assertEquals("", variant.label());
    }
}
