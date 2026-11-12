package com.yovo.biz_ggec_hs.internal.protocol;

import com.yovo.biz_ggec_hs.internal.protocol.batterycmd.BatteryCaseCommand;
import com.yovo.biz_ggec_hs.internal.protocol.batterycmd.BatteryLeftCommand;
import com.yovo.biz_ggec_hs.internal.protocol.batterycmd.BatteryRightCommand;
import com.yovo.biz_ggec_hs.internal.protocol.batterycmd.CaseChargingStatusCommand;
import com.yovo.biz_ggec_hs.internal.protocol.batterycmd.EarInCaseStatusCommand;
import com.yovo.biz_ggec_hs.internal.protocol.controlcmd.ControlCommand;
import com.yovo.biz_ggec_hs.internal.protocol.controlcmd.ControlCommandV11;
import com.yovo.biz_ggec_hs.internal.protocol.controlcmd.DoublePressQueryCommand;
import com.yovo.biz_ggec_hs.internal.protocol.controlcmd.DoublePressSetCommand;
import com.yovo.biz_ggec_hs.internal.protocol.controlcmd.LongPressQueryCommand;
import com.yovo.biz_ggec_hs.internal.protocol.controlcmd.LongPressSetCommand;
import com.yovo.biz_ggec_hs.internal.protocol.controlcmd.ResetControlCommand;
import com.yovo.biz_ggec_hs.internal.protocol.controlcmd.ShortPressQueryCommand;
import com.yovo.biz_ggec_hs.internal.protocol.controlcmd.ShortPressSetCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.A2dpCodeTypeCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.CaseSNCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.ClassicBtAddressCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.ClearPairingCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.DeviceConnectStatusCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.DeviceNameGetCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.DeviceRenameCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.DeviceRebootCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.EarCheckCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.EarColorCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.GetRecordCountCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.GetRecordIdListCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.GetStorageCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.GitCommitVersionCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.GlobalStatusCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.HomePageStatusV12FirstCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.HomePageStatusV12SecCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.OTAStartCommand;
import com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd.ConnectPhoneCommand;
import com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd.DeletePairedDevicesCommand;
import com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd.DeleteParingDeviceCommand;
import com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd.DisconnectPhoneCommand;
import com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd.GetDualConnectionCommand;
import com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd.GetDualConnectionPageCommand;
import com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd.GetPairedDevicesCommand;
import com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd.GetPriorityEquipmentCommand;
import com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd.SetDualConnectionCommand;
import com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd.SetPriorityEquipmentCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.EarLocationCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.FallAlertCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.FindAlertLeftCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.FindAlertRightCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.FindAlertStatusCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.FindAlertStopCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.GameModeCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.NoiseReductionCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.BeginnerModeCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.VoiceAssistantQueryCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.VoiceWakeupCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.WearDetectionCommand;
import com.yovo.biz_ggec_hs.internal.protocol.functioncmd.WearDetectionPromptToneCommand;
import com.yovo.biz_ggec_hs.internal.protocol.filerecordcmd.CancelTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.protocol.filerecordcmd.CleanRecordCommand;
import com.yovo.biz_ggec_hs.internal.protocol.filerecordcmd.DeleteRecordFileCommand;
import com.yovo.biz_ggec_hs.internal.protocol.filerecordcmd.StartCallRecordTransferCommand;
import com.yovo.biz_ggec_hs.internal.protocol.filerecordcmd.StartLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.protocol.filerecordcmd.StartTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.protocol.filerecordcmd.StopCallRecordTransferCommand;
import com.yovo.biz_ggec_hs.internal.protocol.filerecordcmd.StopLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.protocol.filerecordcmd.SyncRecordFileIdOpCommand;
import com.yovo.biz_ggec_hs.internal.protocol.internalcmd.RtcCalibrateCommand;
import com.yovo.biz_ggec_hs.internal.protocol.internalcmd.OfflineLogCommand;
import com.yovo.biz_ggec_hs.internal.protocol.internalcmd.RawTransportCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.CustomEQCancelRealtimeCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.CustomEQDeleteCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.CustomEQQueryCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.CustomEQRealtimeCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.CustomEQSaveCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.EQModeCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.LDACCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.LDACQueryCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.ServiceStatusCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.SoundQualityModeCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.UsageRemindCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.VolumeAdaptiveCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.VolumeRemindCommand;
import com.yovo.biz_ggec_hs.internal.protocol.streamrecordcmd.ExitAiTranslateCommand;
import com.yovo.biz_ggec_hs.internal.protocol.streamrecordcmd.SimultaneousInterpretationCommand;
import com.yovo.biz_ggec_hs.internal.protocol.streamrecordcmd.SimultaneousInterpretationQueryCommand;
import com.yovo.biz_ggec_hs.internal.protocol.streamrecordcmd.StartAiTranslateCommand;
import com.yovo.biz_ggec_hs.internal.protocol.streamrecordcmd.StartStreamRecordCommand;
import com.yovo.biz_ggec_hs.internal.protocol.streamrecordcmd.StopAiTranslateCommand;
import com.yovo.biz_ggec_hs.internal.protocol.streamrecordcmd.StopStreamRecordCommand;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令语义目录。
 *
 * 目录使用具体类型声明调度语义，不再依赖类名字符串推断。未登记命令会明确失败，
 * 防止新增命令因漏配而获得错误的合并和缓存语义。
 * 响应等待也在这里声明。0 表示沿用命令自己的 {@link Command#getTimeoutMs()}。
 */
final class CommandPolicyRegistry implements CommandPolicyResolver {
    private static final long REALTIME_CACHE_MS = 0L;
    private static final long SHORT_CACHE_MS = 300L;
    private static final long DEFAULT_CACHE_MS = 1000L;
    private static final long SESSION_CACHE_MS = 3_000L;
    /** 拉录音列表、删除录音文件。设备侧处理长于命令默认 2s。 */
    private static final long RECORD_FILE_RESPONSE_TIMEOUT_MS = 5_000L;

    private final Map<Class<? extends Command>, Definition> definitions = new HashMap<>();

    CommandPolicyRegistry() {
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
                StartLocalRecordCommand.class,
                StartAiTranslateCommand.class,
                StartStreamRecordCommand.class,
                StartTransmitRecordCommand.class,
                StopAiTranslateCommand.class,
                ExitAiTranslateCommand.class,
                StopCallRecordTransferCommand.class,
                StopLocalRecordCommand.class,
                StopStreamRecordCommand.class,
                FindAlertStopCommand.class,
                RtcCalibrateCommand.class,
                RawTransportCommand.class,
                CustomEQDeleteCommand.class,
                CustomEQSaveCommand.class,
                BeginnerModeCommand.class,
                SimultaneousInterpretationCommand.class);

        registerAction(RECORD_FILE_RESPONSE_TIMEOUT_MS,
                DeleteRecordFileCommand.class);
    }

    @Override
    public CommandPolicy resolve(Command command, String payload) {
        @SuppressWarnings("unchecked")
        Class<? extends Command> type =
                (Class<? extends Command>) command.getClass();
        return resolve(type, payload);
    }

    CommandPolicy resolve(Class<? extends Command> type, String payload) {
        Definition definition = definitions.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("命令未登记调度策略: " + type.getName());
        }

        String semanticKey = semanticKey(type, payload);
        String valueFingerprint = definition.kind == CommandPolicy.Kind.SET
                ? payload
                : null;
        return new CommandPolicy(
                definition.kind,
                definition.mergeMode,
                semanticKey,
                valueFingerprint,
                definition.cacheTtlMs,
                responseTimeoutMs(type, payload, definition));
    }

    /**
     * EC D4 同一命令类承载删除和导出。删除按录音文件操作等待，导出开始仍用命令默认超时。
     */
    private long responseTimeoutMs(Class<? extends Command> type,
                                   String payload,
                                   Definition definition) {
        if (type == SyncRecordFileIdOpCommand.class && isRecordIdDelete(payload)) {
            return RECORD_FILE_RESPONSE_TIMEOUT_MS;
        }
        return definition.responseTimeoutMs;
    }

    private static boolean isRecordIdDelete(String payload) {
        if (payload == null || payload.length() < 12) {
            return false;
        }
        int flags;
        try {
            flags = Integer.parseInt(payload.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            return false;
        }
        return flags == SyncRecordFileIdOpCommand.FLAGS_DELETE_LEFT
                || flags == SyncRecordFileIdOpCommand.FLAGS_DELETE_RIGHT;
    }

    private String semanticKey(Class<? extends Command> type, String payload) {
        String base = type.getName();
        if (type == BeginnerModeCommand.class
                && payload != null && payload.length() >= 6) {
            return base + ":" + payload.substring(4, 6);
        }
        if (type == SimultaneousInterpretationCommand.class
                && payload != null && payload.length() >= 12) {
            return base + ":" + payload.substring(10, 12);
        }
        // 佩戴检测提示音：开关共用一枚语义键（可被后一次开关覆盖），查询独立成键，避免顶掉待发的开关
        if (type == WearDetectionPromptToneCommand.class
                && payload != null && payload.length() >= 12) {
            boolean query = "02".equals(payload.substring(10, 12));
            return base + (query ? ":query" : ":set");
        }
        if (type == GetStorageCommand.class
                && payload != null && payload.length() >= 12) {
            return base + ":" + payload.substring(10, 12);
        }
        if (type == GetRecordIdListCommand.class) {
            return base + ":" + payload;
        }
        if ((type == ControlCommand.class || type == ControlCommandV11.class)
                && payload != null && payload.length() >= 12) {
            return base + ":" + payload.substring(10, 12);
        }
        if ((type == LongPressSetCommand.class
                || type == ShortPressSetCommand.class
                || type == DoublePressSetCommand.class)
                && payload != null && payload.length() >= 12) {
            return base + ":" + payload.substring(10, 12);
        }
        if (type == CustomEQRealtimeCommand.class
                || type == CustomEQCancelRealtimeCommand.class) {
            return "custom-eq-realtime";
        }
        return base;
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
