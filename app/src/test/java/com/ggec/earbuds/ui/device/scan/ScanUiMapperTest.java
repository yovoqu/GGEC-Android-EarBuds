package com.ggec.earbuds.ui.device.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.ScanResult;

import org.junit.Test;

import java.util.List;

public class ScanUiMapperTest {

    @Test
    public void mapWithoutConnectingAddressDefaultsToFalse() {
        ScanResult result = new ScanResult("modelA", "11:22:33:44:55:66", "Earbuds",
                "AA:BB:CC:DD:EE:FF", "Earbuds Classic", -60);
        ScanItem item = ScanUiMapper.map(result);

        assertEquals("modelA", item.modelId());
        assertEquals("11:22:33:44:55:66", item.bleAddress());
        assertEquals("Earbuds", item.bleName());
        assertEquals(-60, item.rssi());
        assertFalse(item.connecting());
    }

    @Test
    public void mapWithMatchingConnectingAddressMarksConnectingTrue() {
        ScanResult result1 = new ScanResult("modelA", "11:22:33:44:55:66", "Earbuds 1",
                "AA:BB:CC:DD:EE:01", "Earbuds Classic", -60);
        ScanResult result2 = new ScanResult("modelA", "AA:BB:CC:11:22:33", "Earbuds 2",
                "AA:BB:CC:DD:EE:02", "Earbuds Classic", -75);

        List<ScanItem> items = ScanUiMapper.map(List.of(result1, result2), "11:22:33:44:55:66");

        assertEquals(2, items.size());
        assertTrue(items.get(0).connecting());
        assertFalse(items.get(1).connecting());
    }

    @Test
    public void mapWithCaseInsensitiveConnectingAddress() {
        ScanResult result = new ScanResult("modelA", "aa:bb:cc:dd:ee:ff", "Earbuds",
                "", "", -50);
        ScanItem item = ScanUiMapper.map(result, "AA:BB:CC:DD:EE:FF");

        assertTrue(item.connecting());
    }

    @Test
    public void scanUiStateCarriesConnectingAddress() {
        ScanUiState state = new ScanUiState(
                false,
                true,
                ScanFilterOption.ALL_MODELS,
                List.of(),
                List.of(),
                0,
                ScanConnectPhase.CONNECTING,
                "AA:BB:CC:DD:EE:FF");

        assertEquals(ScanConnectPhase.CONNECTING, state.connectPhase());
        assertEquals("AA:BB:CC:DD:EE:FF", state.connectingAddress());
    }

    @Test
    public void emptyStateWhenFilterHasNoMatchYieldsFilterEmptyStrings() {
        assertEquals(R.string.device_scan_filter_empty_title,
                ScanUiMapper.emptyTitleRes(false, true, 3));
        assertEquals(R.string.device_scan_filter_empty_body,
                ScanUiMapper.emptyBodyRes(false, true, 3));
    }

    @Test
    public void emptyStateWhenScanningYieldsSearchingStrings() {
        assertEquals(R.string.device_scan_searching,
                ScanUiMapper.emptyTitleRes(true, true, 0));
        assertEquals(R.string.device_scan_searching_hint,
                ScanUiMapper.emptyBodyRes(true, true, 0));
    }

    @Test
    public void emptyStateWhenIdleNotScannedYieldsIdleStrings() {
        assertEquals(R.string.device_scan_idle_title,
                ScanUiMapper.emptyTitleRes(false, false, 0));
        assertEquals(R.string.device_scan_idle_body,
                ScanUiMapper.emptyBodyRes(false, false, 0));
    }

    @Test
    public void emptyStateWhenScannedAndNothingFoundYieldsEmptyStrings() {
        assertEquals(R.string.device_scan_empty_title,
                ScanUiMapper.emptyTitleRes(false, true, 0));
        assertEquals(R.string.device_scan_empty_body,
                ScanUiMapper.emptyBodyRes(false, true, 0));
    }

    @Test
    public void statusAndActionTextReflectScanningState() {
        assertEquals(R.string.device_scan_searching, ScanUiMapper.statusTextRes(true));
        assertEquals(R.string.device_scan_stop, ScanUiMapper.actionTextRes(true));

        assertEquals(R.string.device_scan_found_count, ScanUiMapper.statusTextRes(false));
        assertEquals(R.string.device_scan_start, ScanUiMapper.actionTextRes(false));
    }

    @Test
    public void mapStatePopulatesCalculatedFields() {
        ScanResult r1 = new ScanResult("HS01", "11:22:33:44:55:66", "Buds", "", "", -55);
        ScanUiState state = ScanUiMapper.mapState(
                false,
                true,
                "HS01",
                List.of(new ScanFilterOption("HS01", "HS01")),
                List.of(r1),
                ScanConnectPhase.IDLE,
                "");

        assertFalse(state.scanning());
        assertTrue(state.hasScanned());
        assertEquals(1, state.results().size());
        assertEquals(1, state.totalDiscoveredCount());
        assertEquals(R.string.device_scan_filter_empty_title, state.emptyTitleRes());
        assertEquals(R.string.device_scan_filter_empty_body, state.emptyBodyRes());
        assertEquals(R.string.device_scan_found_count, state.statusTextRes());
        assertEquals(1, state.statusCount());
        assertEquals(R.string.device_scan_start, state.actionTextRes());
    }

    @Test
    public void scanUiStateIdleInitializesDefaultResources() {
        ScanUiState idle = ScanUiState.idle();

        assertFalse(idle.scanning());
        assertFalse(idle.hasScanned());
        assertEquals(0, idle.totalDiscoveredCount());
        assertTrue(idle.results().isEmpty());
        assertEquals(R.string.device_scan_idle_title, idle.emptyTitleRes());
        assertEquals(R.string.device_scan_idle_body, idle.emptyBodyRes());
        assertEquals(R.string.device_scan_start, idle.actionTextRes());
    }
}
