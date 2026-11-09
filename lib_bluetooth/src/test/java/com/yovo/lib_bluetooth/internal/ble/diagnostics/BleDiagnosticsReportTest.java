package com.yovo.lib_bluetooth.internal.ble.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDiagnosticReport;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * BLE-026 事件环形时间线容量裁剪、Hex 安全摘要截断与断连根因追溯诊断报告验证。
 */
public class BleDiagnosticsReportTest {

    private static final UUID SVC_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    @BeforeClass
    public static void installTestLogger() {
        FakeYoBleHarness.ensureTestLoggerInstalled();
    }

    @Test
    public void timelineRecorder_capsAt256Events_andTruncatesLongPayloadHexSafely() {
        YoBleTimelineRecorder recorder = new YoBleTimelineRecorder(256);
        for (int i = 0; i < 1000; i++) {
            recorder.record("ble-test", "event-" + i);
        }

        assertEquals(256, recorder.size());
        List<String> entries = recorder.formattedEntries();
        assertEquals(256, entries.size());
        assertTrue(entries.get(0).contains("event-744"));
        assertTrue(entries.get(255).contains("event-999"));

        byte[] payload = new byte[64];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i + 1);
        }
        String summary = YoBleTimelineRecorder.summarizeBytes(payload);
        assertTrue(summary.startsWith("len=64 [01 02 03"));
        assertTrue(summary.contains("...(+48B)]"));
        // 超过第 16 字节的部分（0x11..0x40）不得在摘要中明文展开
        assertFalse(summary.contains("11 12 13"));
        assertFalse(summary.contains("40"));
    }

    @Test
    public void exportDiagnosticReport_preservesFailureHistoryAndAvoidsPlaintextSecretLeak() {
        FakeYoBleHarness harness = new FakeYoBleHarness(
                BleConfig.builder().diagnosticsTimelineCapacity(256).build());
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:91");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            BleGattServiceInfo svc = new BleGattServiceInfo(
                    SVC_UUID, 1, true,
                    Collections.singletonList(new BleGattCharacteristicInfo(
                            CHR_UUID, 1,
                            BleGattCharacteristicInfo.PROPERTY_READ | BleGattCharacteristicInfo.PROPERTY_WRITE,
                            0, Collections.emptyList())),
                    Collections.emptyList());
            dev.setServices(Collections.singletonList(svc));

            // 1. 扫描 -> 停扫
            harness.client.scan(BleScanConfig.builder().build(), new BleScanObserver() {});
            harness.drain();
            harness.client.stopScan();
            harness.drain();

            // 2. 建连 -> 写入超长疑似敏感 48 字节负载 -> 发生 LINK_LOSS (HCI 0x08) 掉线
            harness.client.connect(id, BleConnectionConfig.builder()
                            .maxRetryCount(0)
                            .requestMtu(null)
                            .build(),
                    new BleConnectionObserver() {});
            harness.drain();

            byte[] sensitivePayload = ("0123456789ABCDEF_SECRET_TOKEN_TAIL_XYZ_987654321")
                    .getBytes(StandardCharsets.UTF_8);
            harness.client.write(id, BleGattTarget.characteristic(SVC_UUID, CHR_UUID),
                    sensitivePayload, BleWriteType.WITH_RESPONSE, new BleGattObserver() {});
            harness.drain();

            dev.simulateRemoteDisconnect(0x08);
            harness.drain();

            Result<BleDiagnosticReport> reportRes = harness.client.exportDiagnosticReport();
            assertTrue(reportRes.isSuccess());
            BleDiagnosticReport report = reportRes.getValue();

            assertTrue(report.isBleSupported());
            assertTrue(report.isAdapterEnabled());
            assertFalse(report.isScanning());
            assertEquals(1, report.activeConnectionCount());
            assertEquals(BleErrorCode.LINK_LOSS,
                    report.activeConnections().get(0).lastFailure().errorCode());

            String dump = report.dumpText();
            assertTrue(dump.contains("=== YoBleClient Diagnostic Dump ==="));
            assertTrue(dump.contains("lastError=LINK_LOSS(raw=8)"));
            assertTrue(dump.contains("len=" + sensitivePayload.length + " ["));
            assertTrue(dump.contains("(+32B)]"));
            // 超过前 16 字节后的 ASCII/Hex 尾部绝不应出现于诊断日志
            assertFalse(dump.contains("SECRET_TOKEN_TAIL_XYZ"));
            assertFalse(dump.contains("53 45 43 52 45 54"));

            // 主动清理断连会话后，Timeline 中依旧可追溯历史 LINK_LOSS 根因
            harness.client.disconnect(id);
            harness.drain();
            BleDiagnosticReport afterCleanup = harness.client.exportDiagnosticReport().getValue();
            assertEquals(0, afterCleanup.activeConnectionCount());
            assertTrue(afterCleanup.dumpText().contains("LINK_LOSS"));
            assertFalse(afterCleanup.recentTimelineEntries().isEmpty());
        } finally {
            harness.release();
        }
    }
}
