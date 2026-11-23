package com.ggec.earbuds.data.device.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.data.store.DocumentFormatException;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceFacts;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceIdentity;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceStatus;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.testing.ai.MemoryDocumentStore;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DeviceRosterJsonTest {

    private static final String DOCUMENT = "devices/roster.json";
    private static final String QUARANTINED = DOCUMENT + ".bad";

    private final MemoryDocumentStore documents = new MemoryDocumentStore();
    private final DeviceRosterJson json = new DeviceRosterJson(documents, DOCUMENT);

    @Test
    public void roundTripKeepsIdentityFactsAndCapabilityValues() {
        DeviceRoster roster = new DeviceRoster(List.of(record()), Optional.of(record().id()));

        json.write(roster);

        assertEquals(roster, json.read().orElseThrow());
    }

    @Test
    public void missingDocumentReadsAsNothing() {
        assertTrue(json.read().isEmpty());
    }

    @Test
    public void brokenJsonIsReportedAndQuarantinedWithItsBytes() {
        documents.documents.put(DOCUMENT, "not-json");

        try {
            json.read();
            fail("a broken roster must not be read as an empty one");
        } catch (DocumentFormatException expected) {
            assertEquals("not-json", documents.documents.get(QUARANTINED));
            assertFalse("原文挪到 .bad 后原文件不再占位", documents.documents.containsKey(DOCUMENT));
        }
    }

    @Test
    public void unknownVersionIsReportedAsFormatFailure() {
        documents.documents.put(DOCUMENT, "{\"version\":2,\"devices\":[]}");

        try {
            json.read();
            fail("an unsupported roster version must not be accepted");
        } catch (DocumentFormatException expected) {
            assertEquals("version", expected.field());
        }
    }

    @Test
    public void theActivePointerUsesTheDeviceKey() {
        json.write(new DeviceRoster(List.of(record()), Optional.of(record().id())));

        assertTrue(documents.documents.get(DOCUMENT).contains("\"active\":\"HS01@AA:BB:CC:DD:EE:FF\""));
        assertEquals(Optional.of(record().id()), json.read().orElseThrow().activeId());
    }

    @Test
    public void aMissingRequiredFactIsCorrupt() {
        json.write(DeviceRoster.empty().upsert(record()));
        String text = documents.documents.get(DOCUMENT).replace("\"wear\":\"NOT_WORN\",", "");

        documents.documents.put(DOCUMENT, text);
        try {
            json.read();
            fail("a roster without the wear state must be rejected");
        } catch (DocumentFormatException expected) {
            assertEquals("wear", expected.field());
        }
    }

    private static DeviceRecord record() {
        return new DeviceRecord(new DeviceId("HS01", "AA:BB:CC:DD:EE:FF"), "我的耳机",
                new DeviceIdentity("HS01", "11:22:33:44:55:66", "HS01"),
                DeviceStatus.archived(1763000000000L),
                new DeviceFacts(new BatterySnapshot(80, 75, 60, true), "1.0.3",
                        WearState.NOT_WORN, 2),
                new DeviceState(Map.of(
                        "EQ_MODE", new ControlValue.Choice("POP"),
                        "WEAR_DETECT", new ControlValue.Toggle(true),
                        "VOLUME", new ControlValue.Level(50),
                        "CUSTOM_EQ", new ControlValue.Curve("", List.of(1, 2, 3)))));
    }
}
