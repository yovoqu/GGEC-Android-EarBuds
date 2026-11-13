package com.ggec.earbuds.domain.device.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceEntry;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

/** 能力入口按 biz 目录聚合；GATE 不生成入口；未知机型返回空。 */
public final class ListDeviceEntriesTest {

    private static final DeviceId HS02 = new DeviceId("HS02", "AA:BB:CC:DD:EE:02");

    @Test
    public void entriesFollowTheCatalogGroupOrderAndSkipGates() {
        ListDeviceEntries usecase = new ListDeviceEntries(catalog());

        List<DeviceEntry> entries = usecase.execute(HS02);

        assertEquals(3, entries.size());
        assertEquals(DeviceCapabilityGroup.MUSIC, entries.get(0).group());
        assertEquals(2, entries.get(0).capabilityCount());
        assertEquals(DeviceCapabilityGroup.FUNCTION, entries.get(1).group());
        assertEquals(1, entries.get(1).capabilityCount());
        assertEquals(DeviceCapabilityGroup.ABOUT, entries.get(2).group());
    }

    @Test
    public void unknownModelOrNoDeviceYieldsNoEntries() {
        ListDeviceEntries usecase = new ListDeviceEntries(catalog());

        assertTrue(usecase.execute(null).isEmpty());
        assertTrue(usecase.execute(new DeviceId("HS99", "AA:BB:CC:DD:EE:99")).isEmpty());
    }

    private static DeviceCapabilityCatalog catalog() {
        List<DeviceCapabilityView> capabilities = List.of(
                capability("EQ", DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.LEVEL),
                capability("ANC", DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.TOGGLE),
                capability("FIND", DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.ACTION),
                capability("GATE_DUAL", DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.GATE),
                capability("FW", DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.ACTION));
        DeviceProfileView profile = new DeviceProfileView("HS02", "HS 系列耳机", capabilities);
        return new DeviceCapabilityCatalog() {
            @Override
            public List<DeviceProfileView> models() {
                return List.of(profile);
            }

            @Override
            public Optional<DeviceProfileView> model(String modelId) {
                return profile.modelId().equals(modelId) ? Optional.of(profile) : Optional.empty();
            }
        };
    }

    private static DeviceCapabilityView capability(String id, DeviceCapabilityGroup group,
                                                   DeviceCapabilityKind kind) {
        return new DeviceCapabilityView(id, id, group, kind, List.<CapabilityOption>of(),
                Optional.empty());
    }
}
