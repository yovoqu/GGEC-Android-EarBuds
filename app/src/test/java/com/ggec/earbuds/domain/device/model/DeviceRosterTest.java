package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DeviceRosterTest {

    private static DeviceRecord record(String address, long lastSeen, String alias) {
        return new DeviceRecord(new DeviceId("HS01", address), alias,
                new DeviceIdentity("HS01", "11:22:33:44:55:66", "HS01"),
                DeviceStatus.archived(lastSeen), DeviceFacts.unknown(), new DeviceState(Map.of()));
    }

    @Test
    public void upsertReplacesSameIdentityAndKeepsOrder() {
        DeviceRoster roster = DeviceRoster.empty()
                .upsert(record("AA:00:00:00:00:01", 100L, ""))
                .upsert(record("AA:00:00:00:00:02", 200L, ""))
                .upsert(record("aa:00:00:00:00:01", 300L, "别名"));

        assertEquals(2, roster.devices().size());
        DeviceRecord replaced = roster.find(new DeviceId("HS01", "AA:00:00:00:00:01"))
                .orElseThrow();
        assertEquals("别名", replaced.alias());
        assertEquals(300L, replaced.status().lastSeenMillis());
        assertEquals("AA:00:00:00:00:02", roster.devices().get(1).id().bleAddress());
    }

    @Test
    public void sortedPutsActiveFirstThenLastSeenDescending() {
        DeviceRecord older = record("AA:00:00:00:00:01", 100L, "");
        DeviceRecord newer = record("AA:00:00:00:00:02", 200L, "");
        DeviceRoster roster = new DeviceRoster(List.of(older, newer),
                Optional.of(older.id()));

        List<DeviceRecord> sorted = roster.sorted();

        assertEquals(older.id(), sorted.get(0).id());
        assertEquals(newer.id(), sorted.get(1).id());
    }

    @Test
    public void withoutRemovesRecordAndActivePointer() {
        DeviceRecord only = record("AA:00:00:00:00:01", 100L, "");
        DeviceRoster roster = new DeviceRoster(List.of(only), Optional.of(only.id()));

        DeviceRoster next = roster.without(only.id());

        assertTrue(next.devices().isEmpty());
        assertFalse(next.activeId().isPresent());
    }

    @Test
    public void displayNameFallsBackToBleNameThenModel() {
        assertEquals("HS01", record("AA:00:00:00:00:01", 1L, "").displayName());
        DeviceRecord alias = record("AA:00:00:00:00:01", 1L, "我的耳机");
        assertEquals("我的耳机", alias.displayName());
    }

    @Test
    public void factsKnowsWhetherAnythingWasReported() {
        assertFalse(DeviceFacts.unknown().known());
        assertTrue(new DeviceFacts(new BatterySnapshot(80, -1, -1, false), "",
                WearState.UNKNOWN, DeviceSnapshot.UNKNOWN_EAR_COLOR).known());
    }
}
