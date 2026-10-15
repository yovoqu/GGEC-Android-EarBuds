package com.yovo.lib_bluetooth.internal.classic.profiles.core;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 从 SDP UUID 与 CoD 等设备证据解析支持的 {@link YoCbtProfileType}，支持多源合并。
 */
public final class YoCbtProfileResolver {

    // -- 已知 Bluetooth UUID（16 位 UUID 扩展为 128 位） --------------------------

    private static final UUID UUID_SPP      = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_A2DP_SRC = UUID.fromString("0000110B-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_A2DP_SNK = UUID.fromString("0000110D-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_AVRCP    = UUID.fromString("0000110A-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_HS       = UUID.fromString("00001108-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_HFP_AG   = UUID.fromString("00001112-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_HFP_HF   = UUID.fromString("0000111E-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_HID      = UUID.fromString("00001124-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_PAN      = UUID.fromString("00001115-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_PAN_GN   = UUID.fromString("00001116-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_PAN_NAP  = UUID.fromString("00001117-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_PBAP_PCE = UUID.fromString("0000112E-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_PBAP_PSE = UUID.fromString("0000112F-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_MAP_MAS  = UUID.fromString("00001132-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_MAP_MNS  = UUID.fromString("00001133-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_OPP      = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB");

    // -- 设备类别（CoD）主类常量 --------------------------------------------------------

    private static final int MAJOR_AUDIO       = 0x0400;
    private static final int MAJOR_PERIPHERAL  = 0x0500;
    private static final int MAJOR_COMPUTER    = 0x0200;

    // 注：次类位掩码取决于 CoD 格式。
    // 次类 0x80 = 键盘，0x40 = 鼠标（见 Bluetooth Assigned Numbers）。

    private static final int MINOR_KEYBOARD = 0x80;
    private static final int MINOR_MOUSE    = 0x40;

    private YoCbtProfileResolver() {
        // 工具类 — 禁止实例化。
    }

    // -- 从 SDP UUID 解析 --------------------------------------------------------------

    /**
     * 从远端设备广播的 SDP 服务 UUID 列表解析 Profile 类型。
     * 每个已知 16 位 Bluetooth UUID 映射到一个或多个 {@link YoCbtProfileType}。
     *
     * @param uuids SDP 期间或设备缓存 UUID 列表获得的
     *              服务 UUID 列表，不可为 null。
     * @return 不可变的已解析 Profile 类型集合；永不为 null，但可能为空。
     */
    @NonNull
    public static Set<YoCbtProfileType> resolveFromUuids(@NonNull final List<UUID> uuids) {
        if (uuids.isEmpty()) {
            return Collections.emptySet();
        }

        final Set<YoCbtProfileType> result = EnumSet.noneOf(YoCbtProfileType.class);

        for (final UUID uuid : uuids) {
            if (uuid == null) continue;

            if (uuid.equals(UUID_SPP)) {
                result.add(YoCbtProfileType.SPP);
            } else if (uuid.equals(UUID_A2DP_SRC) || uuid.equals(UUID_A2DP_SNK)) {
                result.add(YoCbtProfileType.A2DP);
            } else if (uuid.equals(UUID_AVRCP)) {
                result.add(YoCbtProfileType.AVRCP);
            } else if (uuid.equals(UUID_HS)) {
                result.add(YoCbtProfileType.HSP);
                result.add(YoCbtProfileType.HEADSET);
            } else if (uuid.equals(UUID_HFP_AG) || uuid.equals(UUID_HFP_HF)) {
                result.add(YoCbtProfileType.HFP);
                result.add(YoCbtProfileType.HEADSET);
            } else if (uuid.equals(UUID_HID)) {
                result.add(YoCbtProfileType.HID_HOST);
            } else if (uuid.equals(UUID_PAN) || uuid.equals(UUID_PAN_GN) || uuid.equals(UUID_PAN_NAP)) {
                result.add(YoCbtProfileType.PAN);
            } else if (uuid.equals(UUID_PBAP_PCE) || uuid.equals(UUID_PBAP_PSE)) {
                result.add(YoCbtProfileType.PBAP);
            } else if (uuid.equals(UUID_MAP_MAS) || uuid.equals(UUID_MAP_MNS)) {
                result.add(YoCbtProfileType.MAP);
            } else if (uuid.equals(UUID_OPP)) {
                result.add(YoCbtProfileType.OPP);
            }
            // HEARING_AID 与 LE_AUDIO 使用厂商专用 UUID，此处不解析。
        }

        return Collections.unmodifiableSet(result);
    }

    // -- 从设备类别（CoD）解析 --------------------------------------------------------

    /**
     * 从设备的 Class of Device（CoD）值推断可能的 Profile 类型。CoD 提示
     * 仅粗略反映设备能力，可靠度低于 SDP UUID。
     *
     * @param deviceClass 待检查的设备类别，不可为 null。
     * @return 从 CoD 推断的不可变 Profile 类型集合；永不为 null，但可能为空。
     */
    @NonNull
    public static Set<YoCbtProfileType> resolveFromCoD(@NonNull final YoCbtDeviceClass deviceClass) {
        final int major = deviceClass.getMajorDeviceClass();
        final int minor = deviceClass.getDeviceClass();
        final Set<YoCbtProfileType> result = EnumSet.noneOf(YoCbtProfileType.class);

        if (major == MAJOR_AUDIO) {
            // 音视频设备通常支持 A2DP + HFP/Headset。
            result.add(YoCbtProfileType.A2DP);
            result.add(YoCbtProfileType.HEADSET);
            result.add(YoCbtProfileType.HFP);
        } else if (major == MAJOR_PERIPHERAL) {
            // 外设主类：检查 HID（键盘、鼠标）。
            if ((minor & MINOR_KEYBOARD) != 0 || (minor & MINOR_MOUSE) != 0) {
                result.add(YoCbtProfileType.HID_HOST);
            }
        } else if (major == MAJOR_COMPUTER) {
            // 计算机主类：通常支持 PAN 与 SPP。
            result.add(YoCbtProfileType.PAN);
            result.add(YoCbtProfileType.SPP);
        }

        return Collections.unmodifiableSet(result);
    }

    // -- 合并 ----------------------------------------------------------------------------------

    /**
     * 将多个 Profile 类型集合合并为统一集合。用于合并
     * SDP UUID 解析、CoD 提示、缓存能力数据等来源的结果。
     *
     * <p>各集合中的重复类型在结果中合并为单一项。
     *
     * @param sets 要合并的一个或多个 Profile 类型集合。
     * @return 包含所有输入集合并集的新可变集合；永不为 null。
     */
    @NonNull
    public static Set<YoCbtProfileType> merge(@NonNull final Set<YoCbtProfileType>... sets) {
        final Set<YoCbtProfileType> merged = new HashSet<>();
        for (final Set<YoCbtProfileType> set : sets) {
            if (set != null) {
                merged.addAll(set);
            }
        }
        return merged;
    }

    /**
     * 将多个 Profile 类型集合并为统一集合。
     *
     * @param collections 要合并的一个或多个 Profile 类型集合。
     * @return 包含所有输入集合并集的新可变集合；永不为 null。
     */
    @NonNull
    public static Set<YoCbtProfileType> mergeCollections(
            @NonNull final Collection<YoCbtProfileType>... collections) {
        final Set<YoCbtProfileType> merged = new HashSet<>();
        for (final Collection<YoCbtProfileType> col : collections) {
            if (col != null) {
                merged.addAll(col);
            }
        }
        return merged;
    }
}
