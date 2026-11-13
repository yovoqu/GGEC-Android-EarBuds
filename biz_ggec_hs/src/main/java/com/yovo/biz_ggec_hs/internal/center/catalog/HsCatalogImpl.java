package com.yovo.biz_ggec_hs.internal.center.catalog;





import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ControlCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ControlCommandV11;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.PressActionConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 设备能力目录：哪个项目下的哪个产品型号有哪些变种、支持哪些能力，以及每个能力怎么呈现。
 *
 * <p>能力知识只在这里：调用方不写机型分支，也不推导值域。</p>
 *
 * <p>目录不变量（构造期校验，违反即抛）：</p>
 * <ul>
 *   <li>{@code ACTION} 能力必须有可达命令（写入或动作映射至少一个），否则 UI 只能渲染出
 *       永远失败的按钮；</li>
 *   <li>{@code LEVEL} 能力必须带 {@link HsDevice.Range}；</li>
 *   <li>{@code GATE} 能力不得带值域、不得有写入命令，也不得进入快照控制值——它的展示由调用方
 *       既有界面承担，biz 不渲染信息行；</li>
 *   <li>其余形态（TOGGLE / CHOICE / CURVE / LIST）不得带值域，不塞占位值；</li>
 *   <li>标题非空、每个能力恰好一条描述。</li>
 * </ul>
 *
 * <p>{@link HsDevice.Form#LEVEL} 目前没有能力使用：电量改为门控态 {@code GATE}，避免渲染出
 * 写不进去的滑杆；类型与「必须带值域」的不变量保留，供后续真正可写的等级型能力使用。</p>
 */
public final class HsCatalogImpl {

    /** 项目 id：产品线标识，不从型号字符串推导。 */
    private static final String PROJECT_HS = "HS";

    /** 项目显示名（可本地化）：项目的唯一名称来源，项目集合本身由 models 聚合。 */
    private static final Map<String, String> PROJECT_NAMES = Map.of(PROJECT_HS, "HS 系列");

    /** HS01 机型标识（与扫描结果的 modelId 同源）。 */
    private static final String HS01 = "HS01";
    /** HS02 机型标识。 */
    private static final String HS02 = "HS02";

    /** HS01 的 28 项能力。 */
    private static final Set<HsDevice.Capability> HS01_CAPABILITIES = Collections.unmodifiableSet(EnumSet.of(
            HsDevice.Capability.CONNECTION, HsDevice.Capability.BATTERY, HsDevice.Capability.SOUND_QUALITY,
            HsDevice.Capability.EQ_MODE, HsDevice.Capability.LDAC, HsDevice.Capability.VOLUME_ADAPTIVE,
            HsDevice.Capability.TOUCH_LONG, HsDevice.Capability.TOUCH_SHORT, HsDevice.Capability.TOUCH_TAP,
            HsDevice.Capability.RESET_CONTROL, HsDevice.Capability.WEAR_DETECT, HsDevice.Capability.VOICE_WAKEUP,
            HsDevice.Capability.GAME_MODE, HsDevice.Capability.FALL_ALERT, HsDevice.Capability.FIND_EAR,
            HsDevice.Capability.VOLUME_REMIND, HsDevice.Capability.DEVICE_INFO, HsDevice.Capability.RENAME,
            HsDevice.Capability.REBOOT, HsDevice.Capability.CLEAR_PAIRING,
            HsDevice.Capability.RECORD_LOCAL, HsDevice.Capability.RECORD_STREAM,
            HsDevice.Capability.RECORD_LIST, HsDevice.Capability.RECORD_TRANSFER, HsDevice.Capability.AI_ASSISTANT,
            HsDevice.Capability.OTA, HsDevice.Capability.OFFLINE_LOG, HsDevice.Capability.CLASSIC_BT));

    /** HS02 在 HS01 之上多出的 7 项能力。 */
    private static final Set<HsDevice.Capability> HS02_EXTRA = EnumSet.of(
            HsDevice.Capability.DUAL_CONNECTION, HsDevice.Capability.DUAL_CONNECTION_DEVICES,
            HsDevice.Capability.CUSTOM_EQ, HsDevice.Capability.TOUCH_DOUBLE,
            HsDevice.Capability.SERVICE_STATUS, HsDevice.Capability.BEGINNER_MODE, HsDevice.Capability.INTERPRET);

    /** EQ 模式选项（协议取值 0..5）。 */
    private static final List<HsDevice.Option> EQ_MODE_OPTIONS = List.of(
            new HsDevice.Option("0", "音乐"),
            new HsDevice.Option("1", "电影"),
            new HsDevice.Option("2", "新闻"),
            new HsDevice.Option("3", "老年人"),
            new HsDevice.Option("4", "运动"),
            new HsDevice.Option("5", "自定义"));

    /** 音质模式选项。 */
    private static final List<HsDevice.Option> SOUND_QUALITY_OPTIONS = List.of(
            new HsDevice.Option(HsOptionIds.SOUND_QUALITY_HIGH, "高音质"),
            new HsDevice.Option(HsOptionIds.SOUND_QUALITY_ENDURANCE, "高续航"));

    /** 耳类型：0 左右耳，1 左耳，2 右耳（协议取值）。 */
    private static final String[] EAR_TYPES = {"左右耳", "左耳", "右耳"};

    /** 长触功能码：0 录音 1 语音助手 2 EQ切换 3 游戏模式 4 无。 */
    private static final String[] LONG_ACTIONS =
            {"录音", "语音助手", "EQ切换", "游戏模式", "无"};

    /** 短触功能码：0 语音助手 1 EQ切换 2 游戏模式 3 上一首 4 下一首 5 音量加 6 音量减 7 无。 */
    private static final String[] SHORT_ACTIONS =
            {"语音助手", "EQ切换", "游戏模式", "上一首", "下一首", "音量加", "音量减", "无"};

    /** 双触功能码：0 录音 1 语音助手 2 EQ切换 3 游戏模式 4 上一首 5 下一首 6 音量加 7 音量减 8 无。 */
    private static final String[] DOUBLE_ACTIONS =
            {"录音", "语音助手", "EQ切换", "游戏模式", "上一首", "下一首", "音量加", "音量减", "无"};

    /** 轻触耳机侧：1 左耳，2 右耳（协议取值）。 */
    private static final String[] EAR_SIDES = {"左耳", "右耳"};

    /** 轻触触击类型：1 双击，2 三击（协议取值）。 */
    private static final String[] TAP_TYPES = {"双击", "三击"};

    /** 轻触双击功能码：0 播放/暂停 1 音量加 2 音量减 3 上一曲 4 下一曲 5 无。 */
    private static final String[] TAP_TWICE_FUNCTIONS =
            {"播放/暂停", "音量加", "音量减", "上一曲", "下一曲", "无"};

    /** 轻触三击功能码：0 录音 1 语音助手 2 EQ切换 3 游戏模式 4 上一首 5 下一首 6 音量加 7 音量减 8 无。 */
    private static final String[] TAP_TRIPLE_FUNCTIONS =
            {"录音", "语音助手", "EQ切换", "游戏模式", "上一首", "下一首", "音量加", "音量减", "无"};

    private final List<HsDevice.Product> models;
    private final Map<HsDevice.Capability, HsDevice.Feature> descriptors;
    private final List<HsDevice.Feature> orderedDescriptors;

    public HsCatalogImpl() {
        this.descriptors = buildDescriptors();
        this.orderedDescriptors = List.copyOf(descriptors.values());
        this.models = buildModels();
    }

    public List<HsDevice.Product> models() {
        return models;
    }

    public Optional<HsDevice.Product> model(String modelId) {
        for (HsDevice.Product model : models) {
            if (model.modelId().equals(modelId)) {
                return Optional.of(model);
            }
        }
        return Optional.empty();
    }

    public List<HsDevice.Type> deviceTypes() {
        Set<HsDevice.Type> seen = EnumSet.noneOf(HsDevice.Type.class);
        List<HsDevice.Type> ordered = new ArrayList<>();
        for (HsDevice.Product model : models) {
            if (seen.add(model.deviceType())) {
                ordered.add(model.deviceType());
            }
        }
        return List.copyOf(ordered);
    }

    public List<HsDevice.Project> projects() {
        Set<String> seen = new LinkedHashSet<>();
        List<HsDevice.Project> ordered = new ArrayList<>();
        for (HsDevice.Product model : models) {
            if (seen.add(model.projectId())) {
                ordered.add(new HsDevice.Project(model.projectId(),
                        PROJECT_NAMES.getOrDefault(model.projectId(), model.projectId())));
            }
        }
        return List.copyOf(ordered);
    }

    public HsDevice.Feature descriptor(HsDevice.Capability capability) {
        HsDevice.Feature descriptor = capability == null ? null : descriptors.get(capability);
        if (descriptor == null) {
            throw new IllegalArgumentException("能力未登记: " + capability);
        }
        return descriptor;
    }

    public List<HsDevice.Feature> descriptors() {
        return orderedDescriptors;
    }

    /** 按型号取呈现。HS01 的轻触是 V11，选项不含左右耳。 */
    public HsDevice.Feature feature(String modelId, HsDevice.Capability capability) {
        if (HS01.equals(modelId) && capability == HsDevice.Capability.TOUCH_TAP) {
            return of(capability, "轻触触控", HsDevice.Group.CONTROL,
                    HsDevice.Form.CHOICE, hs01TapOptions());
        }
        return descriptor(capability);
    }

    private static List<HsDevice.Product> buildModels() {
        Set<HsDevice.Capability> hs01 = EnumSet.copyOf(HS01_CAPABILITIES);
        Set<HsDevice.Capability> hs02 = EnumSet.copyOf(HS01_CAPABILITIES);
        hs02.addAll(HS02_EXTRA);
        return List.of(
                new HsDevice.Product(HS01, HS01, PROJECT_HS, HsDevice.Type.EARBUDS,
                        Collections.unmodifiableSet(hs01)),
                new HsDevice.Product(HS02, HS02, PROJECT_HS, HsDevice.Type.EARBUDS,
                        Collections.unmodifiableSet(hs02)));
    }

    private static Map<HsDevice.Capability, HsDevice.Feature> buildDescriptors() {
        Map<HsDevice.Capability, HsDevice.Feature> table = new EnumMap<>(HsDevice.Capability.class);
        for (HsDevice.Capability capability : HsDevice.Capability.values()) {
            HsDevice.Feature descriptor = describe(capability);
            validate(descriptor);
            table.put(capability, descriptor);
        }
        return Collections.unmodifiableMap(table);
    }

    /**
     * 目录不变量（构造期校验，违反即抛）：
     * <ol>
     *   <li>{@code ACTION} 能力必须有可达命令（写入或动作映射至少一个）；</li>
     *   <li>{@code LEVEL} 能力必须带值域；</li>
     *   <li>{@code GATE} 能力不得带值域、不得有写入映射，也不进入快照控制值；</li>
     *   <li>其余形态（TOGGLE / CHOICE / CURVE / LIST）不得带值域。</li>
     * </ol>
     * 没有 {@code default}：新增控件形态必须在这里明确它的不变量。
     */
    private static void validate(HsDevice.Feature descriptor) {
        String capability = descriptor.capability().name();
        if (descriptor.title() == null || descriptor.title().trim().isEmpty()) {
            throw new IllegalStateException("能力缺少标题: " + capability);
        }
        if (descriptor.options() == null) {
            throw new IllegalStateException("能力缺少选项表: " + capability);
        }
        if (descriptor.range() == null) {
            throw new IllegalStateException("能力缺少值域字段: " + capability);
        }
        switch (descriptor.kind()) {
            case LEVEL -> validateLevel(descriptor);
            case GATE -> validateGate(descriptor);
            case ACTION -> validateAction(descriptor);
            case TOGGLE, CHOICE, CURVE, LIST -> validateNoRange(descriptor);
        }
    }

    private static void validateLevel(HsDevice.Feature descriptor) {
        if (descriptor.range().isEmpty()) {
            throw new IllegalStateException(
                    "LEVEL 能力必须给出值域: " + descriptor.capability());
        }
        HsDevice.Range range = descriptor.range().get();
        if (range.step() <= 0 || range.min() > range.max()) {
            throw new IllegalStateException("LEVEL 能力值域非法: " + descriptor.capability()
                    + " min=" + range.min() + " max=" + range.max() + " step=" + range.step());
        }
    }

    /**
     * 门控态：不产生通用控件行——不得可写、不得有值域。它同样不得进入快照控制值，
     * 这一条由 {@code everyCapabilitySatisfiesItsKindInvariant} 与
     * {@code gateCapabilitiesStayOutOfSnapshotValues} 断言（{@code value()} 对 GATE 恒为 null）。
     */
    private static void validateGate(HsDevice.Feature descriptor) {
        validateNoRange(descriptor);
    }

    /** 动作型不带值域。能不能落到命令由 {@link com.yovo.biz_ggec_hs.internal.interact.HsCapabilityRouter#verify} 核对。 */
    private static void validateAction(HsDevice.Feature descriptor) {
        validateNoRange(descriptor);
    }

    private static void validateNoRange(HsDevice.Feature descriptor) {
        if (descriptor.range().isPresent()) {
            throw new IllegalStateException("非 LEVEL 能力不得带值域: " + descriptor.capability());
        }
    }

    private static HsDevice.Feature describe(HsDevice.Capability capability) {
        return switch (capability) {
            case CONNECTION -> of(capability, "连接状态", HsDevice.Group.ABOUT,
                    HsDevice.Form.GATE, List.of());
            case DUAL_CONNECTION -> of(capability, "双连接", HsDevice.Group.DUAL_CONNECTION,
                    HsDevice.Form.TOGGLE, List.of());
            case DUAL_CONNECTION_DEVICES -> of(capability, "已配对设备",
                    HsDevice.Group.DUAL_CONNECTION, HsDevice.Form.LIST, List.of());
            case BATTERY -> of(capability, "电量", HsDevice.Group.ABOUT,
                    HsDevice.Form.GATE, List.of());
            case SOUND_QUALITY -> of(capability, "音质", HsDevice.Group.MUSIC,
                    HsDevice.Form.CHOICE, SOUND_QUALITY_OPTIONS);
            case EQ_MODE -> of(capability, "均衡器", HsDevice.Group.MUSIC,
                    HsDevice.Form.CHOICE, EQ_MODE_OPTIONS);
            case CUSTOM_EQ -> of(capability, "自定义均衡器", HsDevice.Group.MUSIC,
                    HsDevice.Form.CURVE, List.of());
            case LDAC -> of(capability, "LDAC", HsDevice.Group.MUSIC,
                    HsDevice.Form.TOGGLE, List.of());
            case VOLUME_ADAPTIVE -> of(capability, "音量自适应", HsDevice.Group.MUSIC,
                    HsDevice.Form.TOGGLE, List.of());
            case RESET_CONTROL -> of(capability, "重置触控设置", HsDevice.Group.CONTROL,
                    HsDevice.Form.ACTION, List.of());
            case REBOOT -> of(capability, "重启耳机", HsDevice.Group.ABOUT,
                    HsDevice.Form.ACTION, List.of());
            case TOUCH_LONG -> of(capability, "长按触控", HsDevice.Group.CONTROL,
                    HsDevice.Form.CHOICE,
                    pressOptions(PressActionConfiguration.LONG_ACTION_MAX, LONG_ACTIONS));
            case TOUCH_SHORT -> of(capability, "短按触控", HsDevice.Group.CONTROL,
                    HsDevice.Form.CHOICE,
                    pressOptions(PressActionConfiguration.SHORT_ACTION_MAX, SHORT_ACTIONS));
            case TOUCH_DOUBLE -> of(capability, "双击触控", HsDevice.Group.CONTROL,
                    HsDevice.Form.CHOICE,
                    pressOptions(PressActionConfiguration.DOUBLE_ACTION_MAX, DOUBLE_ACTIONS));
            case TOUCH_TAP -> of(capability, "轻触触控", HsDevice.Group.CONTROL,
                    HsDevice.Form.CHOICE, tapOptions());
            case WEAR_DETECT -> of(capability, "佩戴检测", HsDevice.Group.CONTROL,
                    HsDevice.Form.TOGGLE, List.of());
            case VOICE_WAKEUP -> of(capability, "语音唤醒", HsDevice.Group.CONTROL,
                    HsDevice.Form.TOGGLE, List.of());
            case GAME_MODE -> of(capability, "游戏模式", HsDevice.Group.CONTROL,
                    HsDevice.Form.TOGGLE, List.of());
            case FALL_ALERT -> of(capability, "跌落提醒", HsDevice.Group.FUNCTION,
                    HsDevice.Form.TOGGLE, List.of());
            case FIND_EAR -> of(capability, "查找耳机", HsDevice.Group.FUNCTION,
                    HsDevice.Form.ACTION, List.of());
            case VOLUME_REMIND -> of(capability, "音量提醒", HsDevice.Group.CONTROL,
                    HsDevice.Form.TOGGLE, List.of());
            case DEVICE_INFO -> of(capability, "设备信息", HsDevice.Group.ABOUT,
                    HsDevice.Form.GATE, List.of());
            case RENAME -> of(capability, "设备名称", HsDevice.Group.ABOUT,
                    HsDevice.Form.ACTION, List.of());
            case CLEAR_PAIRING -> of(capability, "清除配对", HsDevice.Group.ABOUT,
                    HsDevice.Form.ACTION, List.of());
            case RECORD_LOCAL -> of(capability, "本地录音", HsDevice.Group.RECORD,
                    HsDevice.Form.ACTION, List.of());
            case RECORD_STREAM -> of(capability, "随录随传", HsDevice.Group.RECORD,
                    HsDevice.Form.ACTION, List.of());
            case RECORD_LIST -> of(capability, "录音列表", HsDevice.Group.RECORD,
                    HsDevice.Form.LIST, List.of());
            case RECORD_TRANSFER -> of(capability, "录音传输", HsDevice.Group.RECORD,
                    HsDevice.Form.ACTION, List.of());
            case AI_ASSISTANT -> of(capability, "AI 助手", HsDevice.Group.FUNCTION,
                    HsDevice.Form.GATE, List.of());
            case SERVICE_STATUS -> of(capability, "服务状态", HsDevice.Group.FUNCTION,
                    HsDevice.Form.GATE, List.of());
            case BEGINNER_MODE -> of(capability, "新手模式", HsDevice.Group.FUNCTION,
                    HsDevice.Form.TOGGLE, List.of());
            case INTERPRET -> of(capability, "同声传译", HsDevice.Group.FUNCTION,
                    HsDevice.Form.TOGGLE, List.of());
            case OTA -> of(capability, "固件升级", HsDevice.Group.ABOUT,
                    HsDevice.Form.GATE, List.of());
            case OFFLINE_LOG -> of(capability, "离线日志", HsDevice.Group.ABOUT,
                    HsDevice.Form.GATE, List.of());
            case CLASSIC_BT -> of(capability, "经典蓝牙", HsDevice.Group.ABOUT,
                    HsDevice.Form.GATE, List.of());
        };
    }

    private static HsDevice.Feature of(HsDevice.Capability capability, String title,
                                             HsDevice.Group group, HsDevice.Form kind,
                                             List<HsDevice.Option> options) {
        return new HsDevice.Feature(capability, title, group, kind, options,
                Optional.empty());
    }

    private static List<HsDevice.Option> pressOptions(int actionMax, String[] actionTitles) {
        List<HsDevice.Option> options = new ArrayList<>();
        for (int earType = 0; earType < EAR_TYPES.length; earType++) {
            for (int actionType = 0; actionType <= actionMax; actionType++) {
                options.add(new HsDevice.Option(
                        HsOptionIds.press(earType, actionType),
                        EAR_TYPES[earType] + " · " + actionTitles[actionType]));
            }
        }
        return List.copyOf(options);
    }

    private static List<HsDevice.Option> tapOptions() {
        List<HsDevice.Option> options = new ArrayList<>();
        for (int earIndex = 0; earIndex < EAR_SIDES.length; earIndex++) {
            int earSide = earIndex + 1;
            for (int tapIndex = 0; tapIndex < TAP_TYPES.length; tapIndex++) {
                int tapType = tapIndex + 1;
                String[] functions = tapType == ControlCommand.TAP_TWICE
                        ? TAP_TWICE_FUNCTIONS : TAP_TRIPLE_FUNCTIONS;
                for (int functionType = 0; functionType < functions.length; functionType++) {
                    options.add(new HsDevice.Option(
                            HsOptionIds.tap(earSide, tapType, functionType),
                            EAR_SIDES[earIndex] + " · " + TAP_TYPES[tapIndex]
                                    + " · " + functions[functionType]));
                }
            }
        }
        return List.copyOf(options);
    }

    /** V11 轻触：触击类型 0 双击 / 1 三击，功能码与 {@link ControlCommandV11} 一致。 */
    private static List<HsDevice.Option> hs01TapOptions() {
        String[] taps = {"双击", "三击"};
        String[] functions = {
                "播放/暂停", "音量加", "音量减", "上一曲", "下一曲",
                "语音助手", "EQ切换", "游戏模式", "无"};
        List<HsDevice.Option> options = new ArrayList<>();
        for (int tapType = 0; tapType < taps.length; tapType++) {
            for (int functionType = 0; functionType < functions.length; functionType++) {
                options.add(new HsDevice.Option(
                        tapType + ":" + functionType,
                        taps[tapType] + " · " + functions[functionType]));
            }
        }
        return List.copyOf(options);
    }
}
