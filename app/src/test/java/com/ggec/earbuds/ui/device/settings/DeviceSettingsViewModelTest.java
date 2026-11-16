package com.ggec.earbuds.ui.device.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.session.DeviceSession;
import com.ggec.earbuds.domain.device.usecase.ForgetDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDeviceRoster;
import com.ggec.earbuds.domain.device.usecase.RunDeviceAction;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.testing.device.FakeDeviceLinkPort;
import com.ggec.earbuds.testing.device.MemoryDeviceRosterRepository;
import com.ggec.earbuds.ui.device.DeviceProfileFixtures;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DeviceSettingsViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private static final DeviceCapabilityCatalog CATALOG = new DeviceCapabilityCatalog() {
        @Override
        public List<DeviceProfileView> models() {
            return List.of(new DeviceProfileView("HS01", "HS01 耳机", List.of()));
        }

        @Override
        public Optional<DeviceProfileView> model(String modelId) {
            return models().stream().filter(m -> m.modelId().equals(modelId)).findFirst();
        }
    };

    private FakeDeviceLinkPort port;
    private DeviceSession session;
    private MemoryDeviceRosterRepository roster;
    private DeviceSettingsViewModel viewModel;

    @Before
    public void setUp() {
        port = new FakeDeviceLinkPort();
        session = new DeviceSession(port);
        roster = new MemoryDeviceRosterRepository();
        ObserveDevice observeDevice = new ObserveDevice(session);
        WriteDeviceControl writeControl = new WriteDeviceControl(port);
        RunDeviceAction runAction = new RunDeviceAction(port);
        ForgetDevice forgetDevice = new ForgetDevice(roster, session);
        ObserveDeviceRoster observeRoster = new ObserveDeviceRoster(roster, session, () -> 1000L);
        DeviceSettingsUiMapper mapper = new DeviceSettingsUiMapper(new SettingsSectionFactory());

        viewModel = new DeviceSettingsViewModel(
                DeviceCapabilityGroup.FUNCTION,
                CATALOG,
                observeDevice,
                writeControl,
                runAction,
                forgetDevice,
                observeRoster,
                mapper
        );
        viewModel.start();
        port.emit(DeviceProfileFixtures.ready("HS01", new DeviceState(Map.of())));
    }

    @Test
    public void onToggleDispatchesWriteControl() {
        viewModel.onToggle("ANC", true);
        assertEquals(List.of("ANC"), port.writes);
    }

    @Test
    public void onChoiceDispatchesWriteControl() {
        viewModel.onChoice("EQ_MODE", "MUSIC");
        assertEquals(List.of("EQ_MODE"), port.writes);
    }

    @Test
    public void onLevelDispatchesWriteControl() {
        viewModel.onLevel("VOLUME", 80);
        assertEquals(List.of("VOLUME"), port.writes);
    }

    @Test
    public void onCurveDispatchesWriteControl() {
        viewModel.onCurve("CUSTOM_EQ", List.of(1, 2, 3));
        assertEquals(List.of("CUSTOM_EQ"), port.writes);
    }

    @Test
    public void onFindEarActionsDispatchPerform() {
        viewModel.onFindEarLeft();
        viewModel.onFindEarRight();
        viewModel.onStopFindAlert();

        assertEquals(3, port.actions.size());
        assertTrue(port.actions.get(0) instanceof DeviceAction.FindEar find && find.side() == DeviceAction.FindEar.Side.LEFT);
        assertTrue(port.actions.get(1) instanceof DeviceAction.FindEar find && find.side() == DeviceAction.FindEar.Side.RIGHT);
        assertTrue(port.actions.get(2) instanceof DeviceAction.StopFindAlert);
    }

    @Test
    public void onRenameDispatchesPerform() {
        viewModel.onRename("NewName");
        assertEquals(1, port.actions.size());
        assertTrue(port.actions.get(0) instanceof DeviceAction.Rename rename && rename.name().equals("NewName"));
    }

    @Test
    public void onActionDispatchesRebootClearPairingResetControl() {
        viewModel.onAction(DeviceSettingRow.ActionKind.REBOOT);
        viewModel.onAction(DeviceSettingRow.ActionKind.CLEAR_PAIRING);
        viewModel.onAction(DeviceSettingRow.ActionKind.RESET_CONTROL);

        assertEquals(3, port.actions.size());
        assertTrue(port.actions.get(0) instanceof DeviceAction.Reboot);
        assertTrue(port.actions.get(1) instanceof DeviceAction.ClearPairing);
        assertTrue(port.actions.get(2) instanceof DeviceAction.ResetControl);
    }

    @Test
    public void onRecordActionsDispatchPerform() {
        viewModel.onStartRecordLocal();
        viewModel.onStopRecordLocal();
        viewModel.onStartRecordStream();
        viewModel.onStopRecordStream();
        viewModel.onFileTransfer("file-42");

        assertEquals(5, port.actions.size());
        assertTrue(port.actions.get(0) instanceof DeviceAction.StartRecord start && start.mode() == DeviceAction.RecordMode.LOCAL);
        assertTrue(port.actions.get(1) instanceof DeviceAction.StopRecord stop && stop.mode() == DeviceAction.RecordMode.LOCAL);
        assertTrue(port.actions.get(2) instanceof DeviceAction.StartRecord start && start.mode() == DeviceAction.RecordMode.STREAM);
        assertTrue(port.actions.get(3) instanceof DeviceAction.StopRecord stop && stop.mode() == DeviceAction.RecordMode.STREAM);
        assertTrue(port.actions.get(4) instanceof DeviceAction.TransferRecord transfer && transfer.fileId().equals("file-42"));
    }

    @Test
    public void onPairedDeviceActionsDispatchPerform() {
        viewModel.onConnectPairedDevice("AA:BB:CC:DD:EE:01");
        viewModel.onDisconnectPairedDevice("AA:BB:CC:DD:EE:02");
        viewModel.onSetPriorityPairedDevice("AA:BB:CC:DD:EE:03");
        viewModel.onForgetPairedDevice("AA:BB:CC:DD:EE:04");

        assertEquals(4, port.actions.size());
        assertTrue(port.actions.get(0) instanceof DeviceAction.ConnectPairedDevice conn && conn.address().equals("AA:BB:CC:DD:EE:01"));
        assertTrue(port.actions.get(1) instanceof DeviceAction.DisconnectPairedDevice disc && disc.address().equals("AA:BB:CC:DD:EE:02"));
        assertTrue(port.actions.get(2) instanceof DeviceAction.SetPriorityPairedDevice prio && prio.address().equals("AA:BB:CC:DD:EE:03"));
        assertTrue(port.actions.get(3) instanceof DeviceAction.ForgetPairedDevice forg && forg.address().equals("AA:BB:CC:DD:EE:04"));
    }
}
