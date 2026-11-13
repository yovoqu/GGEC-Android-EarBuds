package com.ggec.earbuds.data.device.roster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.data.device.json.DeviceRosterJson;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

public final class DeviceRosterRepositoryImplTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private static final Executor DIRECT = Runnable::run;

    @Test
    public void updatePublishesAndPersistsForNextProcess() {
        File file = new File(folder.getRoot(), "devices/roster.json");
        DeviceRosterRepositoryImpl repository =
                new DeviceRosterRepositoryImpl(file, new DeviceRosterJson(), DIRECT);
        assertTrue(repository.current().devices().isEmpty());

        repository.update(roster -> roster.upsert(record()).withActive(Optional.of(record().id())));

        assertEquals(1, repository.current().devices().size());
        assertTrue(file.exists());
        DeviceRosterRepositoryImpl reloaded =
                new DeviceRosterRepositoryImpl(file, new DeviceRosterJson(), DIRECT);
        assertEquals(repository.current(), reloaded.current());
        assertFalse(reloaded.startupFailure().isPresent());
    }

    @Test
    public void brokenFileStartsEmptyButReportsFailureAndKeepsEvidence() throws Exception {
        File file = new File(folder.getRoot(), "roster.json");
        Files.write(file.toPath(), "garbage".getBytes(StandardCharsets.UTF_8));

        DeviceRosterRepositoryImpl repository =
                new DeviceRosterRepositoryImpl(file, new DeviceRosterJson(), DIRECT);

        assertTrue(repository.current().devices().isEmpty());
        assertTrue(repository.startupFailure().isPresent());
        assertTrue(new File(folder.getRoot(), "roster.json.bad").exists());
    }

    @Test
    public void noChangeDoesNotRewriteFile() {
        File file = new File(folder.getRoot(), "roster.json");
        DeviceRosterRepositoryImpl repository =
                new DeviceRosterRepositoryImpl(file, new DeviceRosterJson(), DIRECT);
        repository.update(roster -> roster);
        assertFalse(file.exists());
    }

    private static DeviceRecord record() {
        return new DeviceRecord(new DeviceId("HS01", "AA:BB:CC:DD:EE:FF"), "",
                new DeviceIdentity("HS01", "11:22:33:44:55:66", "HS01"),
                DeviceStatus.archived(1763000000000L),
                new DeviceFacts(new BatterySnapshot(80, 75, -1, false), "1.0.3",
                        WearState.UNKNOWN, 2),
                new DeviceState(Map.of()));
    }

    @Test
    public void rosterIsSortedStableForSameTimestamps() {
        DeviceRoster roster = DeviceRoster.empty()
                .upsert(withAddress("AA:00:00:00:00:02"))
                .upsert(withAddress("AA:00:00:00:00:01"));
        List<DeviceRecord> sorted = roster.sorted();
        assertEquals("AA:00:00:00:00:01", sorted.get(0).id().bleAddress());
    }

    private static DeviceRecord withAddress(String address) {
        return new DeviceRecord(new DeviceId("HS01", address), "",
                new DeviceIdentity("HS01", "", ""), DeviceStatus.archived(100L),
                DeviceFacts.unknown(), new DeviceState(Map.of()));
    }
}
