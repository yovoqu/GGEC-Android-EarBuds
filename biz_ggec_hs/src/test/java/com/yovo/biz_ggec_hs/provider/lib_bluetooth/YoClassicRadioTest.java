package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.port.HsClassicRadio;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtErrorCode;
import com.yovo.lib_bluetooth.model.classic.CbtFailure;
import com.yovo.lib_bluetooth.model.classic.CbtLinkReadiness;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionHandle;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionObserver;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * HS 经典链路适配的三态→回调投影契约。
 *
 * <p>用动态代理造 {@link YoCbtClient}：只放行 {@code openLinkSession}/{@code release}，
 * 其余任何方法一律抛 {@link UnsupportedOperationException}——这既省去 30+ 桩方法，
 * 又顺带证明 {@link YoClassicRadio} <b>只</b>消费 {@code openLinkSession}，
 * 绝不自行调用 {@code getAclState/queryConnectedDevices/observe*} 去判链路。</p>
 */
public final class YoClassicRadioTest {

    private static final String MAC = "AA:BB:CC:DD:EE:FF";
    private static final CbtDeviceId ID = CbtDeviceId.fromAddress(MAC);

    private static final class FakeHandle implements CbtLinkSessionHandle {
        final AtomicInteger cancelCalls = new AtomicInteger();
        private boolean active = true;

        @Override
        public void cancel() {
            cancelCalls.incrementAndGet();
            active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    private static final class Recorder implements HsClassicRadio.Callback {
        int ready;
        int lost;
        final List<HsControl.Failure> failures = new ArrayList<>();

        @Override
        public void onReady() {
            ready++;
        }

        @Override
        public void onFailed(@NonNull HsControl.Failure failure) {
            failures.add(failure);
        }

        @Override
        public void onLost() {
            lost++;
        }
    }

    private static final class Harness {
        final FakeHandle handle = new FakeHandle();
        CbtLinkSessionObserver observer;
        CbtDeviceId openedDeviceId;
        YoClassicRadio radio;

        Harness() {
            YoCbtClient client = (YoCbtClient) Proxy.newProxyInstance(
                    YoCbtClient.class.getClassLoader(),
                    new Class<?>[]{YoCbtClient.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "openLinkSession":
                                openedDeviceId = (CbtDeviceId) args[0];
                                observer = (CbtLinkSessionObserver) args[2];
                                return handle;
                            case "release":
                                return null;
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
            radio = new YoClassicRadio(client);
        }
    }

    private static CbtFailure readinessFailure(CbtErrorCode code) {
        return new CbtFailure(code, code.name(), null, true, true, "test", ID);
    }

    @Test
    public void openDelegatesToOpenLinkSessionAndReportsReadyOnce() {
        Harness h = new Harness();
        Recorder r = new Recorder();

        h.radio.open(MAC, r);
        assertNotNull("必须通过 openLinkSession 建链", h.observer);
        assertEquals(MAC, h.openedDeviceId.value());

        h.observer.onReadinessChanged(ID, CbtLinkReadiness.READY);
        h.observer.onReadinessChanged(ID, CbtLinkReadiness.READY);
        assertEquals("READY 只上报一次", 1, r.ready);
        assertEquals(0, r.failures.size());
        assertEquals(0, r.lost);
    }

    @Test
    public void downBeforeReadyDoesNotReportLost() {
        Harness h = new Harness();
        Recorder r = new Recorder();

        h.radio.open(MAC, r);
        h.observer.onReadinessChanged(ID, CbtLinkReadiness.DOWN);
        assertEquals("就绪前的 DOWN 不应上报 onLost", 0, r.lost);
        assertEquals(0, r.ready);
    }

    @Test
    public void readyThenDownReportsLostOnce() {
        Harness h = new Harness();
        Recorder r = new Recorder();

        h.radio.open(MAC, r);
        h.observer.onReadinessChanged(ID, CbtLinkReadiness.READY);
        h.observer.onReadinessChanged(ID, CbtLinkReadiness.DOWN);
        h.observer.onReadinessChanged(ID, CbtLinkReadiness.DOWN);
        assertEquals(1, r.ready);
        assertEquals("就绪后掉线只报一次 onLost", 1, r.lost);
    }

    @Test
    public void linkFailedMapsToHsFailure() {
        Harness h = new Harness();
        Recorder r = new Recorder();

        h.radio.open(MAC, r);
        h.observer.onLinkFailed(ID, readinessFailure(CbtErrorCode.LINK_READINESS_TIMEOUT));
        assertEquals(1, r.failures.size());
        assertEquals(HsControl.Failure.TIMEOUT, r.failures.get(0));

        // 终态后迟到的就绪不得再上报
        h.observer.onReadinessChanged(ID, CbtLinkReadiness.READY);
        assertEquals(0, r.ready);
    }

    @Test
    public void cancelCancelsSessionAndIgnoresLateCallbacks() {
        Harness h = new Harness();
        Recorder r = new Recorder();

        h.radio.open(MAC, r);
        h.radio.cancel();
        assertEquals(1, h.handle.cancelCalls.get());

        h.observer.onReadinessChanged(ID, CbtLinkReadiness.READY);
        assertEquals("取消后迟到回调必须丢弃", 0, r.ready);
    }
}
