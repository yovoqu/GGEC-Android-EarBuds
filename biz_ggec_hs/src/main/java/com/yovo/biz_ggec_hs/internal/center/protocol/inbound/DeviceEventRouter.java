package com.yovo.biz_ggec_hs.internal.center.protocol.inbound;


import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.center.protocol.batterycmd.CaseChargingStatusCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.batterycmd.EarInCaseStatusCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.GetPairedDevicesCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.event.ProtocolEventRouter;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.VoiceAssistantQueryCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.ServiceStatusCommand;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 主动上报帧解析器：只写入 {@link DeviceDomainStore}，不持有 App 回调，不感知心跳。
 */
public final class DeviceEventRouter {
    private static final String TAG = "DeviceEventRouter";

    private final ProtocolEventRouter router = new ProtocolEventRouter();
    private final DeviceDomainStore domain;

    /** 设备主动结束录音会话的桥接触发（录音/随录随传/AI翻译位 1→0 时调用）。 */
    private volatile Runnable streamStopTrigger;

    public DeviceEventRouter(DeviceDomainStore domain) {
        if (domain == null) {
            throw new IllegalArgumentException("主动上报路由依赖不能为空");
        }
        this.domain = domain;
        for (String prefix : EventCatalog.prefixes()) {
            switch (prefix) {
                case "99EC80":
                case "99EC95":
                case "99EC96":
                    router.register(prefix, this::handleBatteryFrame);
                    break;
                case "99EC83":
                case "99ECB5":
                    router.register(prefix, this::handleMusicFrame);
                    break;
                case "99EC89":
                case "99EC9F":
                case "99ECCB":
                case "99ECD7":
                case "99ECD2":
                    router.register(prefix, this::handleFunctionFrame);
                    break;
                case "99ECA7":
                    router.register(prefix, this::handlePairedDeviceFrame);
                    break;
                default:
                    throw new IllegalStateException("EventCatalog 前缀未绑定处理器: " + prefix);
            }
        }
    }

    public boolean route(String frame) {
        boolean handled;
        try {
            handled = router.route(frame);
        } catch (RuntimeException e) {
            HsLog.e(TAG, "主动上报路由执行失败: " + frame, e);
            return false;
        }
        if (!handled) {
            HsLog.d(TAG, "未识别的主动上报数据: " + frame);
        }
        return handled;
    }

    /**
     * 注册设备主动停录桥接：录音 02 / 随录随传 04 从 1→0，
     * 或 AI翻译 06 从 1 变为 0/2/3/4（空闲、低电、通话、摘下停止）时触发。
     * 由 {@link DeviceEventPipeline} 注入 {@code StreamAudioService#stopByRemote()}。
     */
    public void setStreamStopTrigger(Runnable trigger) {
        this.streamStopTrigger = trigger;
    }

    public void notifyFileExportSucceeded() {
        emitFileExportStatus("3", "同步导出成功结束");
    }

    public void notifyFileExportFailed() {
        emitFileExportStatus("2", "同步导出失败");
    }

    private void emitFileExportStatus(String exportValue, String reason) {
        Map<String, String> status = buildStatusWithFileExport(exportValue);
        domain.setServiceStatus(status);
        HsLog.proactive(TAG, "[SDK内部][服务状态] " + reason
                + "，文件导出=" + exportValue + " 状态=" + status);
    }

    private Map<String, String> buildStatusWithFileExport(String exportValue) {
        Map<String, String> previous = domain.getServiceStatus();
        Map<String, String> status;
        if (previous != null && !previous.isEmpty()) {
            status = new LinkedHashMap<>(previous);
        } else {
            status = new LinkedHashMap<>();
            for (int i = 0; i < 6; i++) {
                status.put(String.format(Locale.ROOT, "%02d", i), "0");
            }
        }
        status.put("03", exportValue);
        return status;
    }

    private boolean handleBatteryFrame(String frame) {
        String prefix = frame.substring(0, 6).toUpperCase(Locale.ROOT);
        boolean valid = false;
        try {
            switch (prefix) {
                case "99EC80":
                    valid = parseBatteryLevels(frame);
                    break;
                case "99EC95":
                    int charging = CaseChargingStatusCommand.parseChargingStatus(frame);
                    if (charging != -1) {
                        domain.updateCaseChargingStatus(
                                charging, Command.ResultCode.SUCCESS);
                        valid = true;
                    }
                    break;
                case "99EC96":
                    int inCase = EarInCaseStatusCommand.parseInCaseStatus(frame);
                    if (inCase != -1) {
                        domain.updateEarInCaseStatus(
                                inCase, Command.ResultCode.SUCCESS);
                        valid = true;
                    }
                    break;
                default:
                    return false;
            }
        } catch (RuntimeException e) {
            HsLog.e(TAG, "解析电池主动上报失败: " + frame, e);
        }
        if (valid) {
            DeviceDomainStore.BatterySnapshot snapshot = domain.battery();
            HsLog.proactive(TAG, "[主动上报][电池状态] 左耳=" + snapshot.getLeftLevel()
                    + ", 右耳=" + snapshot.getRightLevel()
                    + ", 充电盒=" + snapshot.getCaseLevel()
                    + ", 充电盒充电=" + snapshot.isCaseCharging()
                    + ", 在仓状态=" + snapshot.getEarInCaseStatus());
        } else {
            HsLog.w(TAG, "电池主动上报格式无效: " + frame);
        }
        return true;
    }

    private boolean parseBatteryLevels(String frame) {
        String normalized = frame.toUpperCase(Locale.ROOT);
        if (normalized.length() == 18
                && normalized.startsWith("99EC800002")
                && normalized.endsWith("1234")) {
            int batteryId = Integer.parseInt(normalized.substring(10, 12), 16);
            int level = Integer.parseInt(normalized.substring(12, 14), 16);
            if (batteryId < 1 || batteryId > 3 || level < 0 || level > 100) {
                return false;
            }
            String value = level == 0 ? "--" : String.valueOf(level);
            domain.updateBatteryLevel(batteryId, value, Command.ResultCode.SUCCESS);
            return true;
        }
        if (normalized.length() == 26
                && normalized.startsWith("99EC800003")
                && normalized.endsWith("1234")) {
            String left = parseLevel(normalized, 10, 1);
            String right = parseLevel(normalized, 14, 2);
            String batteryCase = parseLevel(normalized, 18, 3);
            if (left == null || right == null || batteryCase == null) {
                HsLog.w(TAG, "组合电量主动上报的设备编号无效: " + frame);
                return false;
            }
            domain.updateBatteryLevels(
                    left, right, batteryCase, Command.ResultCode.SUCCESS);
            return true;
        }
        return false;
    }

    private String parseLevel(String frame, int idOffset, int expectedId) {
        int id = Integer.parseInt(frame.substring(idOffset, idOffset + 2), 16);
        if (id != expectedId) {
            return null;
        }
        int level = Integer.parseInt(frame.substring(idOffset + 2, idOffset + 4), 16);
        if (level < 0 || level > 100) {
            return null;
        }
        return level == 0 ? "--" : String.valueOf(level);
    }

    private boolean handleMusicFrame(String frame) {
        if (frame.length() < 12) {
            HsLog.w(TAG, "音乐状态主动上报长度不足: " + frame);
            return true;
        }
        try {
            String prefix = frame.substring(0, 6).toUpperCase(Locale.ROOT);
            if ("99EC83".equals(prefix)) {
                if (!ProtocolValues.isCompleteFrame(frame, prefix)
                        || (frame.length() != 16 && frame.length() != 18)) {
                    return true;
                }
                int mode = Integer.parseInt(frame.substring(10, 12), 16);
                if (mode < 0 || mode > 5) {
                    return true;
                }
                domain.setEqMode(mode);
                HsLog.proactive(TAG, "[主动上报][EQ模式] 模式=" + mode);
            } else if ("99ECB5".equals(prefix)) {
                Map<String, String> status = ServiceStatusCommand.parseStatusMap(frame);
                if (status != null) {
                    notifyDeviceStreamStop(status);
                    domain.setServiceStatus(status);
                    HsLog.proactive(TAG, "[主动上报][服务状态] 状态=" + status);
                }
            }
        } catch (RuntimeException e) {
            HsLog.e(TAG, "解析音乐状态主动上报失败: " + frame, e);
        }
        return true;
    }

    private boolean handleFunctionFrame(String frame) {
        if (frame.length() < 12) {
            HsLog.w(TAG, "功能状态主动上报长度不足: " + frame);
            return true;
        }
        try {
            String prefix = frame.substring(0, 6).toUpperCase(Locale.ROOT);
            if ("99EC89".equals(prefix)) {
                if (!ProtocolValues.isCompleteFrame(frame, prefix) || frame.length() != 16) {
                    return true;
                }
                Boolean status = ProtocolValues.parseBooleanByte(frame.substring(10, 12));
                if (status == null) {
                    return true;
                }
                domain.setGameMode(status);
                HsLog.proactive(TAG, "[主动上报][游戏模式] 开启=" + status);
            } else if ("99EC9F".equals(prefix)) {
                if (!ProtocolValues.isCompleteFrame(frame, prefix) || frame.length() != 16) {
                    return true;
                }
                int status = Integer.parseInt(frame.substring(10, 12), 16);
                if (status < 0 || status > 3) {
                    return true;
                }
                domain.setFindAlertStatus(status);
                HsLog.proactive(TAG, "[主动上报][提示音状态] 状态=" + status);
            } else if ("99ECCB".equals(prefix)) {
                Integer code = VoiceAssistantQueryCommand.parseStatusCode(frame);
                if (code == null) {
                    return true;
                }
                domain.setVoiceAssistantCode(code);
                HsLog.proactive(TAG, "[主动上报][语音助手] code=" + code
                        + " (" + (code == 1 ? "唤醒" : "睡眠") + ")");
            } else if ("99ECD2".equals(prefix)) {
                if (!ProtocolValues.isCompleteFrame(frame, prefix) || frame.length() != 18) {
                    return true;
                }
                int earType = Integer.parseInt(frame.substring(10, 12), 16);
                int actionType = Integer.parseInt(frame.substring(12, 14), 16);
                if (earType < 0 || earType > 2 || actionType < 0 || actionType > 4) {
                    return true;
                }
                domain.setBeginnerMode(earType, actionType);
                HsLog.proactive(TAG, "[主动上报][新手模式] earType=" + earType
                        + " actionType=" + actionType);
            } else if ("99ECD7".equals(prefix)) {
                if (!ProtocolValues.isCompleteFrame(frame, prefix) || frame.length() != 16) {
                    return true;
                }
                int earType = Integer.parseInt(frame.substring(10, 12), 16);
                if (earType < 0 || earType > 1) {
                    return true;
                }
                domain.setLeftAndRightEarExchange(earType);
                HsLog.proactive(TAG, "[主动上报][左右耳自适应] earType=" + earType);
            }
        } catch (RuntimeException e) {
            HsLog.e(TAG, "解析功能状态主动上报失败: " + frame, e);
        }
        return true;
    }

    private boolean handlePairedDeviceFrame(String frame) {
        try {
            List<HsControl.Paired> devices = GetPairedDevicesCommand.parseDevices(frame);
            if (devices == null) {
                return true;
            }
            domain.setPairedDevices(devices);
            HsLog.proactive(TAG, "[主动上报][已配对设备列表] 数量=" + devices.size());
        } catch (RuntimeException e) {
            HsLog.e(TAG, "解析已配对设备列表主动上报失败: " + frame, e);
        }
        return true;
    }

    /**
     * 设备主动停录检测：耳机按键结束录音/翻译后主动上报业务状态，
     * 录音(02)/随录随传(04) 位从 1→0、AI翻译(06) 位从 1 变为 0/2/3/4 即视为会话被设备侧停止，
     * 通知上层按远端停止正常收尾，避免流停滞看门狗把正常停录误判为失败。
     */
    private void notifyDeviceStreamStop(Map<String, String> current) {
        Map<String, String> previous = domain.getServiceStatus();
        if (previous == null || !hasStreamBitTurnedOff(previous, current)) {
            return;
        }
        Runnable trigger = streamStopTrigger;
        if (trigger != null) {
            try {
                trigger.run();
            } catch (RuntimeException e) {
                HsLog.e(TAG, "设备停录桥接执行失败", e);
            }
        }
    }

    private static boolean hasStreamBitTurnedOff(Map<String, String> previous,
                                                 Map<String, String> current) {
        return wentToOneToZero(previous, current, "02")
                || wentToOneToZero(previous, current, "04")
                || aiTranslateSessionEnded(previous, current);
    }

    private static boolean aiTranslateSessionEnded(Map<String, String> previous,
                                                     Map<String, String> current) {
        if (!"1".equals(previous.get("06"))) {
            return false;
        }
        String next = current.get("06");
        return "0".equals(next) || "2".equals(next) || "3".equals(next) || "4".equals(next);
    }

    private static boolean wentToOneToZero(Map<String, String> previous,
                                           Map<String, String> current,
                                           String key) {
        return "1".equals(previous.get(key)) && "0".equals(current.get(key));
    }
}
