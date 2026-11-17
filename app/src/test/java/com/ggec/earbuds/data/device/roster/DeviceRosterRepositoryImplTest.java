package com.ggec.earbuds.data.device.roster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
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
import java.util.concurrent.Executor;

public final class DeviceRosterRepositoryImplTest {

    private static final String DOCUMENT = "devices/roster.json";
    private static final Executor DIRECT = Runnable::run;

    private final MemoryDocumentStore documents = new MemoryDocumentStore();

    private DeviceRosterRepositoryImpl repository() {
        return new DeviceRosterRepositoryImpl(documents, DOCUMENT, DIRECT);
    }

    @Test
    public void updatePublishesAndPersistsForNextProcess() {
        DeviceRosterRepositoryImpl repository = repository();
        assertTrue(repository.current().devices().isEmpty());

        repository.update(roster -> roster.upsert(record()).withActive(Optional.of(record().id())));

        assertEquals(1, repository.current().devices().size());
        assertTrue(documents.documents.containsKey(DOCUMENT));
        assertEquals(repository.current(), repository().current());
        assertFalse(repository().startupFailure().isPresent());
    }

    @Test
    public void brokenFileStartsEmptyButReportsFailureAndKeepsEvidence() {
        documents.documents.put(DOCUMENT, "garbage");

        DeviceRosterRepositoryImpl repository = repository();

        assertTrue(repository.current().devices().isEmpty());
        assertTrue(repository.startupFailure().isPresent());
        assertEquals("garbage", documents.documents.get(DOCUMENT + ".bad"));
    }

    @Test
    public void noChangeDoesNotRewriteTheDocument() {
        DeviceRosterRepositoryImpl repository = repository();

        repository.update(roster -> roster);

        assertFalse(documents.documents.containsKey(DOCUMENT));
    }

    @Test
    public void rosterIsSortedStableForSameTimestamps() {
        DeviceRoster roster = DeviceRoster.empty()
                .upsert(withAddress("AA:00:00:00:00:02"))
                .upsert(withAddress("AA:00:00:00:00:01"));
        List<DeviceRecord> sorted = roster.sorted();
        assertEquals("AA:00:00:00:00:01", sorted.get(0).id().address());
    }

    private static DeviceRecord record() {
        return new DeviceRecord(new DeviceId("HS01", "AA:BB:CC:DD:EE:FF"), "",
                new DeviceIdentity("HS01", "11:22:33:44:55:66", "HS01"),
                DeviceStatus.archived(1763000000000L),
                new DeviceFacts(new BatterySnapshot(80, 75, -1, false), "1.0.3",
                        WearState.UNKNOWN, 2),
                new DeviceState(Map.of()));
    }

    private static DeviceRecord withAddress(String address) {
        return new DeviceRecord(new DeviceId("HS01", address), "",
                new DeviceIdentity("HS01", "", ""), DeviceStatus.archived(100L),
                DeviceFacts.unknown(), new DeviceState(Map.of()));
    }
}
