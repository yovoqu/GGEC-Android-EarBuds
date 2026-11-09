package com.ggec.earbuds.ui.device.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.data.device.IdleDevicePort;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.ggec.earbuds.testing.device.FakeTestDevicePort;
import com.ggec.earbuds.testing.device.MemoryTestDevicePreferences;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public final class ScanViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    @Test
    public void startsIdleAndIdlePortFinishesWithoutFakeResults() {
        IdleDevicePort port = new IdleDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port, new MemoryTestDevicePreferences()));

        ScanUiState initial = viewModel.uiState().getValue();
        assertFalse(initial.scanning());
        assertFalse(initial.hasScanned());
        assertTrue(initial.results().isEmpty());

        viewModel.startScan();
        ScanUiState after = viewModel.uiState().getValue();
        assertFalse(after.scanning());
        assertTrue(after.hasScanned());
        assertTrue(after.results().isEmpty());
    }

    @Test
    public void scriptedPortTracksScanningResultsDeduplicationAndCompletion() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port, new MemoryTestDevicePreferences()));

        viewModel.startScan();
        assertTrue(viewModel.uiState().getValue().scanning());
        assertTrue(viewModel.uiState().getValue().hasScanned());

        ScanResult first = new ScanResult("HS01", "v1", "HS01", "AA:BB:CC:00:00:01", -50, true);
        ScanResult duplicate = new ScanResult("HS01", "v1", "HS01", "aa:bb:cc:00:00:01", -49, true);
        port.emitScanResults(List.of(first, duplicate));
        assertEquals(1, viewModel.uiState().getValue().results().size());

        port.finishScan();
        assertFalse(viewModel.uiState().getValue().scanning());
        assertTrue(viewModel.uiState().getValue().hasScanned());
        assertEquals(1, viewModel.uiState().getValue().results().size());
    }
}
