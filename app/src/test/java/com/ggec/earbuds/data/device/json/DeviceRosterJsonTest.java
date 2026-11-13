package com.ggec.earbuds.data.device.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DeviceRosterJsonTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final DeviceRosterJson json = new DeviceRosterJson();

    @Test
    public void roundTripKeepsIdentityFactsAndCapabilityValues() throws IOException {
        File file = new File(folder.getRoot(), "roster.json");
        DeviceRoster roster = new DeviceRoster(List.of(record()), Optional.of(record().id()));

        json.write(file, roster);

        assertEquals(roster, json.read(file));
    }

    @Test
    public void missingFileReadsAsEmptyRoster() throws IOException {
        assertEquals(DeviceRoster.empty(), json.read(new File(folder.getRoot(), "absent.json")));
    }

    @Test
    public void brokenJsonIsReportedNotSwallowed() throws IOException {
        File file = folder.newFile("broken.json");
        Files.write(file.toPath(), "not-json".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> json.read(file));
    }

    @Test
    public void unknownVersionIsReported() throws IOException {
        File file = folder.newFile("v2.json");
        Files.write(file.toPath(), "{\"version\":2,\"devices\":[]}".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> json.read(file));
    }

    @Test
    public void quarantineKeepsEvidenceNextToTheRoster() throws IOException {
        File file = folder.newFile("roster.json");
        Files.write(file.toPath(), "garbage".getBytes(StandardCharsets.UTF_8));

        assertTrue(json.quarantine(file));

        assertFalse(file.exists());
        assertTrue(new File(folder.getRoot(), "roster.json.bad").exists());
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
                        "CUSTOM_EQ", new ControlValue.Curve(List.of(1, 2, 3)))));
    }
}
