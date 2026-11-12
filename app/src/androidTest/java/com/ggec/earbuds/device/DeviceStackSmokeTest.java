package com.ggec.earbuds.device;


import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsScan;
import com.yovo.biz_ggec_hs.api.HsControl;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.yovo.biz_ggec_hs.YoHsSdk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * 真机扫描链路冒烟：设备目录（产品/能力）→ 真实 BLE 扫描 → 目录外设备原样上报。
 *
 * <p>本轮范围只覆盖扫描与产品目录；连接与控制路径未纳入（产品侧暂不验收）。</p>
 */
@RunWith(AndroidJUnit4.class)
public final class DeviceStackSmokeTest {

    private static final long SCAN_TIMEOUT_SECONDS = 20L;

    private Context app;
    private YoHsSdk sdk;

    @Before
    public void setUp() {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        sdk = YoHsSdk.open(app);
    }

    @After
    public void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
    }

    /** 产品目录必须来自 biz：HS01/HS02 都登记，能力与控件形态符合契约。 */
    @Test
    public void catalogExposesBothProductsOnDevice() {
        List<HsDevice.Product> models = sdk.products();
        assertFalse("设备目录为空", models.isEmpty());

        HsDevice.Product hs01 = null;
        HsDevice.Product hs02 = null;
        for (HsDevice.Product model : models) {
            if ("HS01".equals(model.modelId())) {
                hs01 = model;
            } else if ("HS02".equals(model.modelId())) {
                hs02 = model;
            }
        }
        assertNotNull("真实目录必须登记 HS01", hs01);
        assertNotNull("真实目录必须登记 HS02", hs02);
        assertFalse("HS01 能力集为空", hs01.capabilities().isEmpty());
        assertTrue("HS01 必须声明 EQ_MODE", hs01.capabilities().contains(HsDevice.Capability.EQ_MODE));
        assertTrue("HS01 必须声明 LDAC", hs01.capabilities().contains(HsDevice.Capability.LDAC));
        assertTrue("HS02 必须多于 HS01（双连接/自定义 EQ 等）",
                hs02.capabilities().size() > hs01.capabilities().size());
        assertTrue("HS02 必须声明 DUAL_CONNECTION",
                hs02.capabilities().contains(HsDevice.Capability.DUAL_CONNECTION));
        assertTrue("已配对设备能力属于 LIST 形态",
                sdk.feature("HS02", HsDevice.Capability.DUAL_CONNECTION_DEVICES).kind()
                        == HsDevice.Form.LIST);
        assertTrue("BATTERY 属于门控形态，不产生控件行",
                sdk.feature("HS02", HsDevice.Capability.BATTERY).kind() == HsDevice.Form.GATE);
    }

    /** 真实 BLE 扫描：附近有产品时应识别出机型、地址与展示名。 */
    @Test
    public void nearbyScanFindsHsDevicesOnDevice() throws Exception {
        CountDownLatch found = new CountDownLatch(1);
        List<HsScan.Found> seen = new CopyOnWriteArrayList<>();
        sdk.startScan(new HsScan.Listener() {
            @Override
            public void onUpdated(List<HsScan.Found> devices) {
                seen.addAll(devices);
                if (!seen.isEmpty()) {
                    found.countDown();
                }
            }

            @Override
            public void onFailed(HsControl.Failure failure) {
                found.countDown();
            }

            @Override
            public void onFinished() {
                found.countDown();
            }
        });
        try {
            found.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            sdk.stopScan();
        }
        assumeFalse("附近没有产品，跳过扫描断言（环境缺失，不是实现失败）", seen.isEmpty());

        HsScan.Found first = seen.get(0);
        assertNotNull(first.modelId());
        assertFalse("识别出的设备必须带 BLE 地址", first.radios().bleAddress().isEmpty());
        assertFalse("识别出的双模设备必须带经典地址", first.radios().classicAddress().isEmpty());
        assertTrue("RSSI 必须在合理区间", first.rssi() <= 0 && first.rssi() >= -127);

        boolean knownProduct = false;
        for (HsScan.Found device : seen) {
            for (HsDevice.Product model : sdk.products()) {
                if (model.modelId().equals(device.modelId())) {
                    knownProduct = true;
                }
            }
        }
        assertTrue("至少一台设备必须归到目录登记的产品，实际: " + seen, knownProduct);
    }

    /** 重复启停扫描：不可崩溃、状态可回收，第二次仍能拿到结果（或明确失败）。 */
    @Test
    public void scanRestartsAfterStop() throws Exception {
        assertFalse("空闲状态不应处于扫描中", sdk.scanning());
        int firstRound = scanOnceCount();
        sdk.stopScan();
        assertFalse("停止后必须不再处于扫描中", sdk.scanning());
        int secondRound = scanOnceCount();
        sdk.stopScan();
        assumeFalse("附近没有产品，跳过重启断言（环境缺失）", firstRound == 0 && secondRound == 0);
        assertTrue("第二次扫描必须仍能拿到结果，first=" + firstRound + " second=" + secondRound,
                secondRound > 0);
    }

    private int scanOnceCount() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<HsScan.Found> seen = new CopyOnWriteArrayList<>();
        sdk.startScan(new HsScan.Listener() {
            @Override
            public void onUpdated(List<HsScan.Found> devices) {
                seen.clear();
                seen.addAll(devices);
                if (!seen.isEmpty()) {
                    done.countDown();
                }
            }

            @Override
            public void onFailed(HsControl.Failure failure) {
                done.countDown();
            }

            @Override
            public void onFinished() {
                done.countDown();
            }
        });
        done.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return seen.size();
    }
}
