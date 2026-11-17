package com.ggec.earbuds.ui.device.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlRange;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.session.DeviceSession;
import com.ggec.earbuds.domain.device.usecase.ForgetDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDeviceRoster;
import com.ggec.earbuds.domain.device.usecase.RunDeviceAction;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.testing.device.FakeDeviceLinkPort;
import com.ggec.earbuds.testing.device.MemoryDeviceRosterRepository;
import com.ggec.earbuds.ui.device.CurveEditorSeed;
import com.ggec.earbuds.ui.device.DeviceProfileFixtures;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
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
    public void onCurveDispatchesTheDescriptorDerivedDraft() {
        // 草稿由描述符派生（段数 = 频段 options，平直值 = range 中立档/下界）：测试不写死段数与 ±dB。
        List<CapabilityOption> bands = bands(4);
        ControlRange range = ControlRange.of(2, 18, 2);
        List<Integer> draft = CurveEditorSeed
                .of(bands, Optional.of(range), Optional.empty(), Optional.empty())
                .orElseThrow();

        viewModel.onCurve("CUSTOM_EQ", draft);

        assertEquals(List.of("CUSTOM_EQ"), port.writes);

        port.settleWrite(new DeviceWriteResult.Ok());

        assertEquals("写入后界面保留本次曲线",
                Optional.of(draft), currentState().writeOf("CUSTOM_EQ").curveValue());
    }

    /** 描述符频段表：测试输入，只声明段数与标签，不是产品的频段表。 */
    private static List<CapabilityOption> bands(int count) {
        List<CapabilityOption> options = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            options.add(new CapabilityOption("BAND_" + index, "band " + index));
        }
        return List.copyOf(options);
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

    @Test
    public void writeMarksInFlightThenConfirmedInTheUiState() {
        viewModel.onToggle("ANC", true);

        DeviceSettingsUiState.WriteSlot inFlight = currentState().writeOf("ANC");
        assertSame(DeviceSettingsUiState.WriteState.IN_FLIGHT, inFlight.state());
        assertEquals(Optional.of(Boolean.TRUE), inFlight.toggleValue());

        port.settleWrite(new DeviceWriteResult.Ok());

        DeviceSettingsUiState.WriteSlot confirmed = currentState().writeOf("ANC");
        assertSame(DeviceSettingsUiState.WriteState.CONFIRMED, confirmed.state());
        assertEquals("设备不回读也必须保留用户的选择",
                Optional.of(Boolean.TRUE), confirmed.toggleValue());
    }

    @Test
    public void failedWriteDropsTheUnconfirmedValueAndReportsTheFailure() {
        List<DeviceSettingsEvent> events = new ArrayList<>();
        viewModel.events().observeForever(events::add);

        viewModel.onToggle("ANC", true);
        port.settleWrite(new DeviceWriteResult.Failed(DeviceFailure.REJECTED));

        DeviceSettingsUiState.WriteSlot failed = currentState().writeOf("ANC");
        assertSame(DeviceSettingsUiState.WriteState.FAILED, failed.state());
        assertTrue("失败必须丢弃未确认值，呈现自动回退", failed.pendingValue().isEmpty());
        assertTrue(events.stream().anyMatch(
                event -> event instanceof DeviceSettingsEvent.Failure failure
                        && failure.failure() == DeviceFailure.REJECTED));
    }

    @Test
    public void bareFindEarActionRoutesToBothSides() {
        viewModel.onAction(DeviceSettingRow.ActionKind.FIND_EAR);

        assertEquals(1, port.actions.size());
        assertTrue(port.actions.get(0) instanceof DeviceAction.FindEar find
                && find.side() == DeviceAction.FindEar.Side.BOTH);
    }

    @Test
    public void payloadActionsAreReportedInsteadOfSilentlyDropped() {
        List<DeviceSettingsEvent> events = new ArrayList<>();
        viewModel.events().observeForever(events::add);

        viewModel.onAction(DeviceSettingRow.ActionKind.RENAME);
        viewModel.onAction(DeviceSettingRow.ActionKind.RECORD_LOCAL);
        viewModel.onAction(DeviceSettingRow.ActionKind.RECORD_STREAM);

        assertTrue(port.actions.isEmpty());
        assertEquals(3, events.size());
        assertTrue(events.get(0) instanceof DeviceSettingsEvent.ActionUnsupported first
                && first.kind() == DeviceSettingRow.ActionKind.RENAME);
        assertTrue(events.get(1) instanceof DeviceSettingsEvent.ActionUnsupported second
                && second.kind() == DeviceSettingRow.ActionKind.RECORD_LOCAL);
        assertTrue(events.get(2) instanceof DeviceSettingsEvent.ActionUnsupported third
                && third.kind() == DeviceSettingRow.ActionKind.RECORD_STREAM);
    }

    @Test
    public void transferWithoutAnyRecordedFileIsReportedNotSentAsAnEmptyCommand() {
        List<DeviceSettingsEvent> events = new ArrayList<>();
        viewModel.events().observeForever(events::add);

        viewModel.onAction(DeviceSettingRow.ActionKind.RECORD_TRANSFER);

        assertTrue(port.actions.isEmpty());
        assertTrue(events.get(0) instanceof DeviceSettingsEvent.ActionUnsupported unsupported
                && unsupported.kind() == DeviceSettingRow.ActionKind.RECORD_TRANSFER);
    }

    @NonNull
    private DeviceSettingsUiState currentState() {
        DeviceSettingsUiState state = viewModel.uiState().getValue();
        if (state == null) {
            throw new IllegalStateException("uiState 尚未发布");
        }
        return state;
    }
}
