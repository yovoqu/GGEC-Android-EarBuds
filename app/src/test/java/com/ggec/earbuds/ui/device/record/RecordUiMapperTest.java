package com.ggec.earbuds.ui.device.record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.record.RecordState;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** 渲染状态是领域状态的纯投影：不补默认值、不带业务判定。 */
public final class RecordUiMapperTest {

    private final RecordUiMapper mapper = new RecordUiMapper();

    @Test
    public void initialProjectionCarriesTheEmptyFacts() {
        RecordUiState state = mapper.map(RecordState.initial());

        assertFalse(state.connected());
        assertTrue(state.supportedModes().isEmpty());
        assertSame(RecordState.StreamPhase.IDLE, state.streamPhase());
        assertSame(RecordState.DeviceRecordPhase.IDLE, state.deviceRecordPhase());
        assertSame(RecordState.SyncPhase.IDLE, state.syncPhase());
        assertTrue(state.syncingFileId().isEmpty());
        assertTrue(state.playingFile().isEmpty());
        assertTrue(state.localFiles().isEmpty());
        assertTrue(state.deviceFiles().isEmpty());
    }

    @Test
    public void streamAndDeviceRecordFactsArePassedThrough() {
        RecordState recorded = new RecordState(
                RecordMode.LOCAL,
                Set.of(RecordMode.STREAM, RecordMode.LOCAL),
                true,
                new RecordState.Stream(RecordState.StreamPhase.RECORDING, 7_000L, 0.42f),
                new RecordState.DeviceRecord(RecordState.DeviceRecordPhase.ACTIVE,
                        OptionalLong.of(1_000L), 4_000L),
                new RecordState.Sync(RecordState.SyncPhase.IN_FLIGHT,
                        Optional.of("dev-1"), 30, 300L, 1_000L),
                List.of(new SavedRecordItem("/tmp/a.mp3", "a.mp3", 10L, 20L, 1_000L)),
                List.of(new RecordFileView("dev-1", "录音 1", 5, 900L)),
                Optional.of("/tmp/a.mp3"));

        RecordUiState state = mapper.map(recorded);

        assertSame(RecordMode.LOCAL, state.mode());
        assertEquals(2, state.supportedModes().size());
        assertSame(RecordState.StreamPhase.RECORDING, state.streamPhase());
        assertEquals(7_000L, state.streamDurationMs());
        assertEquals(0.42f, state.streamAmplitude(), 0.001f);
        assertSame(RecordState.DeviceRecordPhase.ACTIVE, state.deviceRecordPhase());
        assertEquals(4_000L, state.deviceRecordDurationMs());
        assertTrue(state.syncing());
        assertEquals(Optional.of("dev-1"), state.syncingFileId());
        assertEquals(30, state.syncPercent());
        assertEquals(300L, state.syncTransferredBytes());
        assertEquals(1_000L, state.syncTotalBytes());
        assertEquals(1, state.localFiles().size());
        assertEquals(1, state.deviceFiles().size());
        assertTrue(state.playing("/tmp/a.mp3"));
        assertTrue(state.canRecord(RecordMode.STREAM));
        assertTrue(state.streamActive());
        assertTrue(state.deviceRecordActive());
        assertFalse(state.singleMode());
    }

    @Test
    public void emptySupportedModesIsNeverRecordable() {
        RecordState connectedWithoutModes = new RecordState(
                RecordMode.STREAM,
                Set.of(),
                true,
                RecordState.Stream.idle(),
                RecordState.DeviceRecord.idle(),
                RecordState.Sync.idle(),
                List.of(),
                List.of(),
                Optional.empty());

        RecordUiState state = mapper.map(connectedWithoutModes);

        assertFalse(state.canRecord(RecordMode.STREAM));
        assertFalse(state.canRecord(RecordMode.LOCAL));
        assertTrue(state.singleMode());
    }
}
