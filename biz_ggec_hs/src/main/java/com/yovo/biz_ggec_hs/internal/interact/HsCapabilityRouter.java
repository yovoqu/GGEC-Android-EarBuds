package com.yovo.biz_ggec_hs.internal.interact;





import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsProductCatalog;
import com.yovo.biz_ggec_hs.internal.center.catalog.HsCatalogImpl;
import com.yovo.biz_ggec_hs.internal.center.catalog.HsOptionIds;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolDual;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolRecording;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.CommandCompletion;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ControlCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ControlCommandV11;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.DoublePressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.LongPressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ResetControlCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ShortPressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.ClearPairingCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.DeviceRebootCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.DeviceRenameCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GlobalStatusCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.HomePageStatusV12FirstCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.HomePageStatusV12SecCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GetRecordIdListCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.ConnectPhoneCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.DeleteParingDeviceCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.DisconnectPhoneCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.GetDualConnectionPageCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.GetPairedDevicesCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.SetDualConnectionCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.SetPriorityEquipmentCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.CancelTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StopLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.BeginnerModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FallAlertCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FindAlertLeftCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FindAlertRightCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FindAlertStopCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.GameModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.VoiceWakeupCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.WearDetectionCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.inbound.DeviceDomainStore;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEQRealtimeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.EQModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.LDACCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.LDACQueryCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.SoundQualityModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.VolumeAdaptiveCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.VolumeRemindCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.SimultaneousInterpretationCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StartStreamRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StopStreamRecordCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 能力/动作 ↔ 协议命令与状态的唯一映射点。
 *
 * <p>能力判断分支只允许出现在这里：{@code api/} 与 {@code provider/} 里没有能力语义，
 * 会话实现只问「可写吗 / 用哪条命令 / 当前值是什么」。</p>
 *
 * <p>三个维度各自单源：</p>
 * <ul>
 *   <li>写入：{@link #write(HsDevice.Capability, HsControl.Value)}，由 {@link #supportsWrite(HsDevice.Capability)} 判定可达。</li>
 *   <li>动作：{@link #commands(HsControl.Action)}，动作归属的能力由 {@link #capabilityOf(HsControl.Action)} 给出。</li>
 *   <li>状态：{@link #value(HsDevice.Capability, DeviceDomainStore, HsControl.Dual)}。</li>
 * </ul>
 *
 * <p>选择项 id 的编码也只在这里定义：{@link #pressOptionId(int, int)} 与
 * {@link #tapOptionId(int, int, int)} 出码，{@link #write} 解码。目录只负责给出
 * 「可选哪些组合 + 标题」，不再自己拼协议字段。</p>
 */
public final class HsCapabilityRouter {

    public static final String SOUND_QUALITY_HIGH = HsOptionIds.SOUND_QUALITY_HIGH;
    public static final String SOUND_QUALITY_ENDURANCE = HsOptionIds.SOUND_QUALITY_ENDURANCE;

    /** 录音列表一次取页的起始页。 */
    private static final int RECORD_LIST_PAGE = 0;
    /** 录音列表一次取页的文件数（协议允许 1~12）。 */
    private static final int RECORD_LIST_LIMIT = 12;
    /** 录音文件索引的协议上界。 */
    private static final int RECORD_FILE_INDEX_MAX = 0xFF;

    /** 触控选择项 id：{@code <耳类型>:<操作类型>}。 */
    public static String pressOptionId(int earType, int actionType) {
        return HsOptionIds.press(earType, actionType);
    }

    /** 轻触选择项 id：{@code <耳侧>:<触击类型>:<功能码>}。 */
    public static String tapOptionId(int earSide, int tapType, int functionType) {
        return HsOptionIds.tap(earSide, tapType, functionType);
    }

    /** 目录构造完后核对：门控不可写，动作必须有命令。 */
    public static void verify(HsCatalogImpl catalog) {
        for (HsDevice.Product product : catalog.models()) {
            for (HsDevice.Capability capability : product.capabilities()) {
                HsDevice.Feature feature = catalog.feature(product.modelId(), capability);
                if (feature.kind() == HsDevice.Form.GATE && supportsWrite(capability)) {
                    throw new IllegalStateException("GATE 能力不得有写入命令: " + capability);
                }
                if (feature.kind() == HsDevice.Form.ACTION
                        && !supportsWrite(capability)
                        && !supportsAction(capability)) {
                    throw new IllegalStateException("ACTION 能力缺少可达命令: " + capability);
                }
            }
        }
    }

    /** 该能力是否有写入命令（目录不变量与门控共用同一判定）。 */
    public static boolean supportsWrite(HsDevice.Capability capability) {
        return switch (capability) {
            case EQ_MODE, CUSTOM_EQ, SOUND_QUALITY, LDAC, VOLUME_ADAPTIVE, GAME_MODE,
                 WEAR_DETECT, VOICE_WAKEUP, FALL_ALERT, VOLUME_REMIND, BEGINNER_MODE,
                 INTERPRET, DUAL_CONNECTION,
                 TOUCH_LONG, TOUCH_SHORT, TOUCH_DOUBLE, TOUCH_TAP -> true;
            case CONNECTION, DUAL_CONNECTION_DEVICES, BATTERY, FIND_EAR, DEVICE_INFO,
                 RENAME, REBOOT, CLEAR_PAIRING,
                 RESET_CONTROL, RECORD_LOCAL, RECORD_STREAM, RECORD_LIST, RECORD_TRANSFER,
                 AI_ASSISTANT, SERVICE_STATUS, OTA, OFFLINE_LOG, CLASSIC_BT -> false;
        };
    }

    /** 该能力是否有一可达动作（{@link HsControl.Action} 映射到它）。 */
    public static boolean supportsAction(HsDevice.Capability capability) {
        return switch (capability) {
            case FIND_EAR, RESET_CONTROL, REBOOT, RENAME, CLEAR_PAIRING,
                 RECORD_LOCAL, RECORD_STREAM, RECORD_LIST, RECORD_TRANSFER,
                 DUAL_CONNECTION_DEVICES -> true;
            case CONNECTION, DUAL_CONNECTION, BATTERY, SOUND_QUALITY, EQ_MODE, CUSTOM_EQ,
                 LDAC, VOLUME_ADAPTIVE, TOUCH_LONG, TOUCH_SHORT, TOUCH_DOUBLE, TOUCH_TAP,
                 WEAR_DETECT, VOICE_WAKEUP, GAME_MODE, FALL_ALERT, VOLUME_REMIND,
                 DEVICE_INFO, AI_ASSISTANT, SERVICE_STATUS, BEGINNER_MODE, INTERPRET,
                 OTA, OFFLINE_LOG, CLASSIC_BT -> false;
        };
    }

    /**
     * 写入命令。
     *
     * @throws IllegalArgumentException 能力不可写，或值与能力形态不符
     */
    public Command write(String modelId, HsDevice.Capability capability, HsControl.Value value) {
        return switch (capability) {
            case EQ_MODE -> new EQModeCommand(eqMode(choice(capability, value)));
            case CUSTOM_EQ -> new CustomEQRealtimeCommand(gains(capability, value));
            case SOUND_QUALITY -> new SoundQualityModeCommand(
                    highQuality(choice(capability, value)));
            case LDAC -> new LDACCommand(toggle(capability, value));
            case VOLUME_ADAPTIVE -> new VolumeAdaptiveCommand(toggle(capability, value));
            case GAME_MODE -> new GameModeCommand(toggle(capability, value));
            case WEAR_DETECT -> new WearDetectionCommand(toggle(capability, value));
            case VOICE_WAKEUP -> new VoiceWakeupCommand(toggle(capability, value));
            case FALL_ALERT -> new FallAlertCommand(toggle(capability, value));
            case VOLUME_REMIND -> new VolumeRemindCommand(toggle(capability, value));
            case BEGINNER_MODE -> new BeginnerModeCommand(toggle(capability, value));
            case INTERPRET -> new SimultaneousInterpretationCommand(toggle(capability, value));
            case DUAL_CONNECTION -> new SetDualConnectionCommand(toggle(capability, value));
            case TOUCH_LONG -> press(capability, choice(capability, value), PressKind.LONG);
            case TOUCH_SHORT -> press(capability, choice(capability, value), PressKind.SHORT);
            case TOUCH_DOUBLE -> press(capability, choice(capability, value), PressKind.DOUBLE);
            case TOUCH_TAP -> tap(modelId, choice(capability, value));
            case CONNECTION, DUAL_CONNECTION_DEVICES, BATTERY, FIND_EAR, DEVICE_INFO,
                 RENAME, REBOOT, CLEAR_PAIRING,
                 RESET_CONTROL, RECORD_LOCAL, RECORD_STREAM, RECORD_LIST, RECORD_TRANSFER,
                 AI_ASSISTANT, SERVICE_STATUS, OTA, OFFLINE_LOG, CLASSIC_BT ->
                    throw new IllegalArgumentException("能力不可写: " + capability);
        };
    }

    /** 动作归属的能力（用于能力目录判定）。 */
    public HsDevice.Capability capabilityOf(HsControl.Action action) {
        return switch (action) {
            case HsControl.Action.FindEar findEar -> HsDevice.Capability.FIND_EAR;
            case HsControl.Action.StopFindAlert stopFindAlert -> HsDevice.Capability.FIND_EAR;
            case HsControl.Action.Reboot reboot -> HsDevice.Capability.REBOOT;
            case HsControl.Action.ResetControl resetControl -> HsDevice.Capability.RESET_CONTROL;
            case HsControl.Action.Rename rename -> HsDevice.Capability.RENAME;
            case HsControl.Action.ClearPairing clearPairing -> HsDevice.Capability.CLEAR_PAIRING;
            case HsControl.Action.StartRecord startRecord -> startRecordCapability(startRecord.mode());
            case HsControl.Action.StopRecord stopRecord -> stopRecordCapability(stopRecord.mode());
            case HsControl.Action.ReadRecordList readRecordList -> HsDevice.Capability.RECORD_LIST;
            case HsControl.Action.TransferRecord transferRecord -> HsDevice.Capability.RECORD_TRANSFER;
            case HsControl.Action.ReadPairedDevices readPairedDevices -> HsDevice.Capability.DUAL_CONNECTION_DEVICES;
            case HsControl.Action.ForgetPairedDevice forgetPairedDevice -> HsDevice.Capability.DUAL_CONNECTION_DEVICES;
            case HsControl.Action.SetPriorityPairedDevice setPriority -> HsDevice.Capability.DUAL_CONNECTION_DEVICES;
            case HsControl.Action.ConnectPairedDevice connectPaired -> HsDevice.Capability.DUAL_CONNECTION_DEVICES;
            case HsControl.Action.DisconnectPairedDevice disconnectPaired -> HsDevice.Capability.DUAL_CONNECTION_DEVICES;
        };
    }

    /**
     * 动作对应的命令列表。一次动作可能下发多条命令（例如查找双耳），
     * 由会话实现在全部完成后给出唯一结果。
     *
     * @throws IllegalArgumentException 动作参数不合法
     */
    public List<Command> commands(HsControl.Action action) {
        return switch (action) {
            case HsControl.Action.FindEar findEar -> switch (findEar.side()) {
                case LEFT -> List.of(new FindAlertLeftCommand(true));
                case RIGHT -> List.of(new FindAlertRightCommand(true));
                case BOTH -> List.of(new FindAlertLeftCommand(true), new FindAlertRightCommand(true));
            };
            case HsControl.Action.StopFindAlert stopFindAlert -> List.of(new FindAlertStopCommand());
            case HsControl.Action.Reboot reboot -> List.of(new DeviceRebootCommand());
            case HsControl.Action.ResetControl resetControl -> List.of(new ResetControlCommand());
            case HsControl.Action.Rename rename ->
                    List.of(new DeviceRenameCommand(text(rename.name(), "设备名")));
            case HsControl.Action.ClearPairing clearPairing -> List.of(new ClearPairingCommand());
            case HsControl.Action.StartRecord startRecord -> List.of(switch (startRecord.mode()) {
                case LOCAL -> new StartLocalRecordCommand();
                case STREAM -> new StartStreamRecordCommand();
            });
            case HsControl.Action.StopRecord stopRecord -> List.of(switch (stopRecord.mode()) {
                case LOCAL -> new StopLocalRecordCommand();
                case STREAM -> new StopStreamRecordCommand();
            });
            case HsControl.Action.ReadRecordList readRecordList ->
                    List.of(new GetRecordIdListCommand(RECORD_LIST_PAGE, RECORD_LIST_LIMIT));
            case HsControl.Action.TransferRecord transferRecord -> List.of(transferRecord.start()
                    ? new StartTransmitRecordCommand(fileIndex(transferRecord.fileId()))
                    : new CancelTransmitRecordCommand());
            case HsControl.Action.ReadPairedDevices readPairedDevices ->
                    List.of(new GetPairedDevicesCommand(), new GetDualConnectionPageCommand());
            case HsControl.Action.ForgetPairedDevice forget ->
                    List.of(new DeleteParingDeviceCommand(address(forget.address())));
            case HsControl.Action.SetPriorityPairedDevice priority ->
                    List.of(new SetPriorityEquipmentCommand(address(priority.address())));
            case HsControl.Action.ConnectPairedDevice connect ->
                    List.of(new ConnectPhoneCommand(address(connect.address())));
            case HsControl.Action.DisconnectPairedDevice disconnect ->
                    List.of(new DisconnectPhoneCommand(address(disconnect.address())));
        };
    }

    /**
     * 能力当前已知值。设备还没上报过的能力返回 {@code null}——快照里不出现该键，
     * 不用伪造的默认值占位。
     *
     * <p>switch 穷尽 {@link HsDevice.Capability}，没有 {@code default}：新增能力时必须在这里明确
     * 「有状态来源」还是「没有状态来源」，否则编译期报错，不允许静默丢失状态。</p>
     */
    public HsControl.Value value(HsDevice.Capability capability,
                         DeviceDomainStore domain,
                         HsControl.Dual dual) {
        return switch (capability) {
            // 有状态来源
            case EQ_MODE -> domain.getEqMode() == null
                    ? null
                    : new HsControl.Value.Choice(String.valueOf(domain.getEqMode()));
            case GAME_MODE -> domain.getGameMode() == null
                    ? null
                    : new HsControl.Value.Toggle(domain.getGameMode());
            case DUAL_CONNECTION -> dual == null
                    ? null
                    : new HsControl.Value.Toggle(dual.dualEnabled());
            case LDAC -> domain.getLdacEnabled() == null
                    ? null
                    : new HsControl.Value.Toggle(domain.getLdacEnabled());
            case BEGINNER_MODE -> domain.getBeginnerModeEnabled() == null
                    ? null
                    : new HsControl.Value.Toggle(domain.getBeginnerModeEnabled());
            // 无状态来源：声明态 / 纯动作 / 只走事件 / 协议无可查询状态
            case CONNECTION, DUAL_CONNECTION_DEVICES, BATTERY, SOUND_QUALITY, CUSTOM_EQ,
                 VOLUME_ADAPTIVE,
                 TOUCH_LONG, TOUCH_SHORT, TOUCH_DOUBLE, TOUCH_TAP, RESET_CONTROL,
                 WEAR_DETECT, VOICE_WAKEUP, FALL_ALERT, FIND_EAR, VOLUME_REMIND,
                 DEVICE_INFO, RENAME, REBOOT, CLEAR_PAIRING,
                 RECORD_LOCAL, RECORD_STREAM, RECORD_LIST, RECORD_TRANSFER,
                 AI_ASSISTANT, SERVICE_STATUS, INTERPRET, OTA, OFFLINE_LOG,
                 CLASSIC_BT -> null;
        };
    }

    /**
     * 命令回包到设备事件的唯一映射：双连接页聚合与录音列表是这类回包的仅有两个来源。
     *
     * @return {@code empty} 表示该回包不产出事件
     */
    public Optional<HsControl.Event> deviceEvent(Command command, CommandCompletion completion) {
        if (command == null
                || completion == null
                || completion.getCode() != Command.ResultCode.SUCCESS
                || completion.getShape() != CommandCompletion.Shape.OBJECT) {
            return Optional.empty();
        }
        Object data = completion.getData();
        if (command instanceof GetDualConnectionPageCommand
                && data instanceof ProtocolDual dual) {
            return Optional.of(new HsControl.Event.DualConnectionChanged(HsModels.dual(dual)));
        }
        if (command instanceof GetRecordIdListCommand && data instanceof List<?> items) {
            List<ProtocolRecording> files = new ArrayList<>(items.size());
            for (Object item : items) {
                if (!(item instanceof ProtocolRecording file)) {
                    throw new IllegalArgumentException(
                            "录音列表回包混入了非录音文件项: " + item);
                }
                files.add(file);
            }
            return Optional.of(new HsControl.Event.RecordFilesChanged(HsModels.recordings(files)));
        }
        return Optional.empty();
    }

    /**
     * READY 后按型号补查一次。HS01 走 V11 全局状态，HS02 走 V12 的两帧首页。
     * 未知型号不发查询，也不回落到 V11。
     */
    public List<Command> sessionBootstrapQueries(String modelId) {
        if (HsProductCatalog.HS02.equals(modelId)) {
            return List.of(
                    new HomePageStatusV12FirstCommand(),
                    new HomePageStatusV12SecCommand(),
                    new LDACQueryCommand());
        }
        if (HsProductCatalog.HS01.equals(modelId)) {
            return List.of(new GlobalStatusCommand(), new LDACQueryCommand());
        }
        return List.of();
    }

    private enum PressKind { LONG, SHORT, DOUBLE }

    private static HsDevice.Capability startRecordCapability(HsControl.Action.RecordMode mode) {
        return mode == HsControl.Action.RecordMode.LOCAL
                ? HsDevice.Capability.RECORD_LOCAL
                : HsDevice.Capability.RECORD_STREAM;
    }

    private static HsDevice.Capability stopRecordCapability(HsControl.Action.RecordMode mode) {
        return mode == HsControl.Action.RecordMode.LOCAL
                ? HsDevice.Capability.RECORD_LOCAL
                : HsDevice.Capability.RECORD_STREAM;
    }

    private Command press(HsDevice.Capability capability, String optionId, PressKind kind) {
        int[] selection = decode(optionId, 2, capability);
        int earType = selection[0];
        int actionType = selection[1];
        return switch (kind) {
            case LONG -> new LongPressSetCommand(actionType, earType);
            case SHORT -> new ShortPressSetCommand(actionType, earType);
            case DOUBLE -> new DoublePressSetCommand(actionType, earType);
        };
    }

    private Command tap(String modelId, String optionId) {
        if (HsProductCatalog.HS01.equals(modelId)) {
            int[] selection = decode(optionId, 2, HsDevice.Capability.TOUCH_TAP);
            return new ControlCommandV11(selection[0], selection[1]);
        }
        int[] selection = decode(optionId, 3, HsDevice.Capability.TOUCH_TAP);
        return new ControlCommand(selection[0], selection[1], selection[2]);
    }

    private static int[] decode(String optionId, int fieldCount, HsDevice.Capability capability) {
        if (optionId == null) {
            throw new IllegalArgumentException(capability + " 缺少选项 id");
        }
        String[] fields = optionId.split(":", -1);
        if (fields.length != fieldCount) {
            throw new IllegalArgumentException(
                    capability + " 选项 id 形态错误（需要 " + fieldCount + " 段）: " + optionId);
        }
        int[] values = new int[fieldCount];
        for (int index = 0; index < fieldCount; index++) {
            try {
                values[index] = Integer.parseInt(fields[index]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        capability + " 选项 id 不是数字: " + optionId, e);
            }
        }
        return values;
    }

    private static int eqMode(String optionId) {
        try {
            return Integer.parseInt(optionId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("EQ_MODE 选项 id 不是数字: " + optionId, e);
        }
    }

    private static boolean highQuality(String optionId) {
        if (SOUND_QUALITY_HIGH.equals(optionId)) {
            return true;
        }
        if (SOUND_QUALITY_ENDURANCE.equals(optionId)) {
            return false;
        }
        throw new IllegalArgumentException("SOUND_QUALITY 选项 id 未知: " + optionId);
    }

    private static int[] gains(HsDevice.Capability capability, HsControl.Value value) {
        if (!(value instanceof HsControl.Value.Curve curve)) {
            throw kindMismatch(capability, "Curve", value);
        }
        List<Integer> points = curve.points();
        if (points == null || points.size() != CustomEQRealtimeCommand.BAND_COUNT) {
            throw new IllegalArgumentException(capability + " 需要 "
                    + CustomEQRealtimeCommand.BAND_COUNT + " 个频段值");
        }
        int[] gains = new int[points.size()];
        for (int index = 0; index < gains.length; index++) {
            Integer point = points.get(index);
            if (point == null) {
                throw new IllegalArgumentException(capability + " 第 " + index + " 个频段值为 null");
            }
            gains[index] = point;
        }
        return gains;
    }

    private static String choice(HsDevice.Capability capability, HsControl.Value value) {
        if (!(value instanceof HsControl.Value.Choice selected)) {
            throw kindMismatch(capability, "Choice", value);
        }
        return selected.optionId();
    }

    private static boolean toggle(HsDevice.Capability capability, HsControl.Value value) {
        if (!(value instanceof HsControl.Value.Toggle toggle)) {
            throw kindMismatch(capability, "Toggle", value);
        }
        return toggle.enabled();
    }

    private static IllegalArgumentException kindMismatch(HsDevice.Capability capability,
                                                         String expected,
                                                         HsControl.Value actual) {
        return new IllegalArgumentException(capability + " 需要 " + expected + " 值，收到 "
                + (actual == null ? "null" : actual.getClass().getSimpleName()));
    }

    private static String text(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value;
    }

    private static String address(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("设备地址不能为空");
        }
        return value;
    }

    private static int fileIndex(String fileId) {
        try {
            int index = Integer.parseInt(fileId);
            if (index < 0 || index > RECORD_FILE_INDEX_MAX) {
                throw new IllegalArgumentException("录音文件索引超出协议范围: " + fileId);
            }
            return index;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("录音文件 id 不是协议索引: " + fileId, e);
        }
    }
}
