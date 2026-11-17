package com.ggec.earbuds.ui.device.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.ScanResult;

import org.junit.Test;

import java.util.List;

public class ScanUiMapperTest {

    private static final List<ScanFilterOption> OPTIONS =
            List.of(new ScanFilterOption("M01", "M01"), new ScanFilterOption("M02", "M02"));

    @Test
    public void mapWithoutConnectingAddressDefaultsToFalse() {
        ScanResult result = new ScanResult("modelA", "Earbuds", "11:22:33:44:55:66", -60);
        ScanItem item = ScanUiMapper.map(result, null);

        assertEquals("modelA", item.modelId());
        assertEquals("11:22:33:44:55:66", item.address());
        assertEquals("Earbuds", item.name());
        assertEquals(-60, item.rssi());
        assertFalse(item.connecting());
    }

    @Test
    public void mapWithMatchingConnectingAddressMarksConnectingTrue() {
        ScanResult result1 = new ScanResult("modelA", "Earbuds 1", "11:22:33:44:55:66", -60);
        ScanResult result2 = new ScanResult("modelA", "Earbuds 2", "AA:BB:CC:11:22:33", -75);

        List<ScanItem> items = ScanUiMapper.map(List.of(result1, result2), "11:22:33:44:55:66");

        assertEquals(2, items.size());
        assertTrue(items.get(0).connecting());
        assertFalse(items.get(1).connecting());
    }

    @Test
    public void mapWithCaseInsensitiveConnectingAddress() {
        ScanResult result = new ScanResult("modelA", "Earbuds", "aa:bb:cc:dd:ee:ff", -50);
        ScanItem item = ScanUiMapper.map(result, "AA:BB:CC:DD:EE:FF");

        assertTrue(item.connecting());
    }

    @Test
    public void connectingPhaseCarriesAddressAndMarksItem() {
        ScanResult result = new ScanResult("M01", "Earbuds", "AA:BB:CC:DD:EE:FF", -50);
        ScanUiState state = ScanUiMapper.mapState(
                new ScanPhase.Connecting("AA:BB:CC:DD:EE:FF"), "M01", OPTIONS, List.of(result));

        assertEquals("AA:BB:CC:DD:EE:FF", state.phase().connectingAddress());
        assertTrue(state.phase().connecting());
        assertTrue(state.results().get(0).connecting());
    }

    @Test
    public void emptyStateWhenScanningYieldsSearchingStrings() {
        assertEquals(R.string.device_scan_searching,
                ScanUiMapper.emptyTitleRes(new ScanPhase.Scanning(), OPTIONS));
        assertEquals(R.string.device_scan_searching_hint,
                ScanUiMapper.emptyBodyRes(new ScanPhase.Scanning(), OPTIONS));
    }

    @Test
    public void emptyStateWhenIdleNotScannedYieldsIdleStrings() {
        assertEquals(R.string.device_scan_idle_title,
                ScanUiMapper.emptyTitleRes(new ScanPhase.Idle(), OPTIONS));
        assertEquals(R.string.device_scan_idle_body,
                ScanUiMapper.emptyBodyRes(new ScanPhase.Idle(), OPTIONS));
    }

    @Test
    public void emptyStateWhenScannedAndNothingFoundYieldsEmptyStrings() {
        assertEquals(R.string.device_scan_empty_title,
                ScanUiMapper.emptyTitleRes(new ScanPhase.Stopped(), OPTIONS));
        assertEquals(R.string.device_scan_empty_body,
                ScanUiMapper.emptyBodyRes(new ScanPhase.Stopped(), OPTIONS));
    }

    @Test
    public void emptyDirectoryYieldsNoModelStateRegardlessOfPhase() {
        assertEquals(R.string.device_scan_no_model_title,
                ScanUiMapper.emptyTitleRes(new ScanPhase.Idle(), List.of()));
        assertEquals(R.string.device_scan_no_model_body,
                ScanUiMapper.emptyBodyRes(new ScanPhase.Scanning(), List.of()));
    }

    @Test
    public void statusAndActionTextReflectScanningState() {
        assertEquals(R.string.device_scan_searching,
                ScanUiMapper.statusTextRes(new ScanPhase.Scanning()));
        assertEquals(R.string.device_scan_stop,
                ScanUiMapper.actionTextRes(new ScanPhase.Scanning()));

        assertEquals(R.string.device_scan_found_count,
                ScanUiMapper.statusTextRes(new ScanPhase.Stopped()));
        assertEquals(R.string.device_scan_start,
                ScanUiMapper.actionTextRes(new ScanPhase.Stopped()));
    }

    @Test
    public void mapStatePopulatesCalculatedFields() {
        ScanResult result = new ScanResult("M01", "Buds", "11:22:33:44:55:66", -55);
        ScanUiState state = ScanUiMapper.mapState(
                new ScanPhase.Stopped(), "M01", OPTIONS, List.of(result));

        assertFalse(state.phase().scanning());
        assertEquals("M01", state.selectedFilterModelId());
        assertEquals(1, state.results().size());
        assertEquals(R.string.device_scan_found_count, state.statusTextRes());
        assertEquals(1, state.statusCount());
        assertEquals(R.string.device_scan_start, state.actionTextRes());
    }

    @Test
    public void mapStateHidesCountWhileScanning() {
        ScanResult result = new ScanResult("M01", "Buds", "11:22:33:44:55:66", -55);
        ScanUiState state = ScanUiMapper.mapState(
                new ScanPhase.Scanning(), "M01", OPTIONS, List.of(result));

        assertEquals(1, state.results().size());
        assertEquals(0, state.statusCount());
    }

    @Test
    public void idleStateHasNoBuiltInModelTable() {
        ScanUiState idle = ScanUiState.idle();

        assertTrue(idle.filterOptions().isEmpty());
        assertEquals("", idle.selectedFilterModelId());
        assertTrue(idle.results().isEmpty());
    }

    @Test
    public void stateRejectsUnsetStringResources() {
        assertThrows(IllegalArgumentException.class, () -> new ScanUiState(
                new ScanPhase.Idle(), "", List.of(), List.of(), 0,
                R.string.device_scan_empty_body, R.string.device_scan_start,
                0, R.string.device_scan_start));
    }
}
