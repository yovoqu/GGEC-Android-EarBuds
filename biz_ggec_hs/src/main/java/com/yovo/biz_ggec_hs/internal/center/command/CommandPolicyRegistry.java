package com.yovo.biz_ggec_hs.internal.center.command;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.batterycmd.BatteryCaseCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.batterycmd.BatteryLeftCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.batterycmd.BatteryRightCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.batterycmd.CaseChargingStatusCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.batterycmd.EarInCaseStatusCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ControlCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ControlCommandV11;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.DoublePressQueryCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.DoublePressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.LongPressQueryCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.LongPressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ResetControlCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ShortPressQueryCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ShortPressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.A2dpCodeTypeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.CaseSNCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.ClassicBtAddressCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.ClearPairingCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.DeviceConnectStatusCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.DeviceNameGetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.DeviceRenameCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.DeviceRebootCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.EarCheckCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.EarColorCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GetRecordCountCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GetRecordIdListCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GetStorageCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GitCommitVersionCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GlobalStatusCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.HomePageStatusV12FirstCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.HomePageStatusV12SecCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.OTAStartCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.ConnectPhoneCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.DeletePairedDevicesCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.DeleteParingDeviceCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.DisconnectPhoneCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.GetDualConnectionCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.GetDualConnectionPageCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.GetPairedDevicesCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.GetPriorityEquipmentCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.SetDualConnectionCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.SetPriorityEquipmentCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.EarLocationCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FallAlertCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FindAlertLeftCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FindAlertRightCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FindAlertStatusCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FindAlertStopCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.GameModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.NoiseReductionCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.BeginnerModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.VoiceAssistantQueryCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.VoiceWakeupCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.WearDetectionCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.WearDetectionPromptToneCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.CancelTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.CleanRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.DeleteRecordFileCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartCallRecordTransferCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StopCallRecordTransferCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StopLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.SyncRecordFileIdOpCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.internalcmd.RtcCalibrateCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.internalcmd.OfflineLogCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.internalcmd.RawTransportCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEQCancelRealtimeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEQDeleteCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEQQueryCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEQRealtimeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEQSaveCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.EQModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.LDACCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.LDACQueryCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.ServiceStatusCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.SoundQualityModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.UsageRemindCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.VolumeAdaptiveCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.VolumeRemindCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.ExitAiTranslateCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.SimultaneousInterpretationCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.SimultaneousInterpretationQueryCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StartAiTranslateCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StartStreamRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StopAiTranslateCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StopStreamRecordCommand;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令语义目录。
 *
 * 目录使用具体类型声明调度语义，不再依赖类名字符串推断。未登记命令会明确失败，
 * 防止新增命令因漏配而获得错误的合并和缓存语义。
 * 响应等待也在这里声明。0 表示沿用命令自己的 {@link Command#getTimeoutMs()}。
 */
public final class CommandPolicyRegistry implements CommandPolicyResolver {
    private static final long REALTIME_CACHE_MS = 0L;
    private static final long SHORT_CACHE_MS = 300L;
    private static final long DEFAULT_CACHE_MS = 1000L;
    private static final long SESSION_CACHE_MS = 3_000L;
    /** 拉录音列表、删除录音文件。设备侧处理长于命令默认 2s。 */
    private static final long RECORD_FILE_RESPONSE_TIMEOUT_MS = 5_000L;
    /** 随录随传/文件传输命令的响应窗口（设备回执偶发较慢）。 */
    private static final long STREAM_RECORD_RESPONSE_TIMEOUT_MS = 10_000L;

    private final Map<Class<? extends Command>, Definition> definitions = new HashMap<>();

    public CommandPolicyRegistry() {
        registerGet(REALTIME_CACHE_MS,
                GetDualConnectionPageCommand.class);

        registerGet(DEFAULT_CACHE_MS,
                BatteryLeftCommand.class,
                BatteryRightCommand.class,
                BatteryCaseCommand.class,
                CaseChargingStatusCommand.class,
                EarInCaseStatusCommand.class,
                GetStorageCommand.class,
                GlobalStatusCommand.class,
                HomePageStatusV12FirstCommand.class,
                HomePageStatusV12SecCommand.class,
                GetDualConnectionCommand.class,
                GetPairedDevicesCommand.class,
                GetPriorityEquipmentCommand.class);

        registerGet(SHORT_CACHE_MS,
                LongPressQueryCommand.class,
                ShortPressQueryCommand.class,
                DoublePressQueryCommand.class,
                GetRecordCountCommand.class,
                FindAlertStatusCommand.class,
                CustomEQQueryCommand.class,
                ServiceStatusCommand.class,
                DeviceConnectStatusCommand.class,
                LDACQueryCommand.class,
                VoiceAssistantQueryCommand.class,
                SimultaneousInterpretationQueryCommand.class);

        registerGet(SHORT_CACHE_MS, RECORD_FILE_RESPONSE_TIMEOUT_MS,
                GetRecordIdListCommand.class);

        registerGet(SESSION_CACHE_MS,
                A2dpCodeTypeCommand.class,
                CaseSNCommand.class,
                ClassicBtAddressCommand.class,
                DeviceNameGetCommand.class,
                EarColorCommand.class,
                GitCommitVersionCommand.class);

        registerSet(
                ControlCommand.class,
                ControlCommandV11.class,
                LongPressSetCommand.class,
                ShortPressSetCommand.class,
                DoublePressSetCommand.class,
                DeviceRenameCommand.class,
                EarCheckCommand.class,
                SetDualConnectionCommand.class,
                SetPriorityEquipmentCommand.class,
                EarLocationCommand.class,
                FallAlertCommand.class,
                FindAlertLeftCommand.class,
                FindAlertRightCommand.class,
                GameModeCommand.class,
                NoiseReductionCommand.class,
                VoiceWakeupCommand.class,
                WearDetectionCommand.class,
                WearDetectionPromptToneCommand.class,
                CustomEQCancelRealtimeCommand.class,
                CustomEQRealtimeCommand.class,
                EQModeCommand.class,
                SoundQualityModeCommand.class,
                UsageRemindCommand.class,
                VolumeAdaptiveCommand.class,
                VolumeRemindCommand.class,
                OfflineLogCommand.class,
                LDACCommand.class);

        registerAction(
                ResetControlCommand.class,
                ClearPairingCommand.class,
                DeviceRebootCommand.class,
                OTAStartCommand.class,
                ConnectPhoneCommand.class,
                DeletePairedDevicesCommand.class,
                DeleteParingDeviceCommand.class,
                DisconnectPhoneCommand.class,
                CancelTransmitRecordCommand.class,
                CleanRecordCommand.class,
                SyncRecordFileIdOpCommand.class,
                StartCallRecordTransferCommand.class,
                StartAiTranslateCommand.class,
                StopAiTranslateCommand.class,
                ExitAiTranslateCommand.class,
                StopCallRecordTransferCommand.class,
                FindAlertStopCommand.class,
                RtcCalibrateCommand.class,
                RawTransportCommand.class,
                CustomEQDeleteCommand.class,
                CustomEQSaveCommand.class,
                BeginnerModeCommand.class,
                SimultaneousInterpretationCommand.class);

        registerAction(RECORD_FILE_RESPONSE_TIMEOUT_MS,
                DeleteRecordFileCommand.class);

        // 随录随传/文件录音/文件传输命令：设备处理与回执可能较慢（HS02 实测停止/开始回执偶发 >2s），
        // 声明更长的响应窗口，避免默认 2s 误超时。属协议自描述的响应超时，非遗留补丁。
        registerAction(STREAM_RECORD_RESPONSE_TIMEOUT_MS,
                StartStreamRecordCommand.class,
                StartTransmitRecordCommand.class,
                StopStreamRecordCommand.class,
                StartLocalRecordCommand.class,
                StopLocalRecordCommand.class);
    }

    @Override
    public CommandPolicy resolve(Command command, String payload) {
        @SuppressWarnings("unchecked")
        Class<? extends Command> type =
                (Class<? extends Command>) command.getClass();
        Definition definition = definitions.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("命令未登记调度策略: " + type.getName());
        }

        String semanticKey = command.semanticKey(payload);
        String valueFingerprint = definition.kind == CommandPolicy.Kind.SET
                ? payload
                : null;
        return new CommandPolicy(
                definition.kind,
                definition.mergeMode,
                semanticKey,
                valueFingerprint,
                definition.cacheTtlMs,
                command.responseTimeoutMs(definition.responseTimeoutMs));
    }

    @SafeVarargs
    private final void registerGet(long cacheTtlMs,
                                   Class<? extends Command>... commandTypes) {
        registerGet(cacheTtlMs, 0L, commandTypes);
    }

    @SafeVarargs
    private final void registerGet(long cacheTtlMs,
                                   long responseTimeoutMs,
                                   Class<? extends Command>... commandTypes) {
        for (Class<? extends Command> type : commandTypes) {
            definitions.put(type, new Definition(
                    CommandPolicy.Kind.GET,
                    CommandPolicy.MergeMode.SINGLE_FLIGHT,
                    cacheTtlMs,
                    responseTimeoutMs));
        }
    }

    @SafeVarargs
    private final void registerSet(Class<? extends Command>... commandTypes) {
        for (Class<? extends Command> type : commandTypes) {
            definitions.put(type, new Definition(
                    CommandPolicy.Kind.SET,
                    CommandPolicy.MergeMode.LATEST_WINS,
                    0L,
                    0L));
        }
    }

    @SafeVarargs
    private final void registerAction(Class<? extends Command>... commandTypes) {
        registerAction(0L, commandTypes);
    }

    @SafeVarargs
    private final void registerAction(long responseTimeoutMs,
                                      Class<? extends Command>... commandTypes) {
        for (Class<? extends Command> type : commandTypes) {
            definitions.put(type, new Definition(
                    CommandPolicy.Kind.ACTION,
                    CommandPolicy.MergeMode.NONE,
                    0L,
                    responseTimeoutMs));
        }
    }

    private static final class Definition {
        private final CommandPolicy.Kind kind;
        private final CommandPolicy.MergeMode mergeMode;
        private final long cacheTtlMs;
        private final long responseTimeoutMs;

        private Definition(CommandPolicy.Kind kind,
                           CommandPolicy.MergeMode mergeMode,
                           long cacheTtlMs,
                           long responseTimeoutMs) {
            this.kind = kind;
            this.mergeMode = mergeMode;
            this.cacheTtlMs = cacheTtlMs;
            this.responseTimeoutMs = responseTimeoutMs;
        }
    }
}
