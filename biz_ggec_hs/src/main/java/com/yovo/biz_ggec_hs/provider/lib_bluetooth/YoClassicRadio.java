package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.port.HsClassicRadio;
import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtFailure;
import com.yovo.lib_bluetooth.model.classic.CbtLinkReadiness;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionConfig;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionHandle;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionObserver;
import com.yovo.lib_bluetooth.model.common.Failure;

import java.util.Objects;

/**
 * 经典音频链路适配：把 {@code lib_bluetooth} 的 {@link YoCbtClient#openLinkSession} 的
 * 三态就绪事实（READY / DOWN / UNKNOWN）投影成 HS 会话能消费的
 * {@link HsClassicRadio.Callback}（onReady / onFailed / onLost）。
 *
 * <p><b>为什么这里没有配对/ACL/Profile 的任何判定</b>：「先查后等、未知不重连、显式超时、
 * 失败即终态」这些链路就绪策略已全部收敛到库内 {@code YoCbtLinkSession}。本类只做
 * <b>事实→回调</b>的单点投影，不再组合 {@code getAclState / getConnectedProfiles /
 * queryConnectedDevices / observe* } 去自行判链路——那正是过去 biz 关注蓝牙细节、且重复
 * 触发 CONNECT 导致必然超时的根因。</p>
 */
public final class YoClassicRadio implements HsClassicRadio {

    private final YoCbtClient client;
    private final Object lock = new Object();

    private int generation;
    private boolean released;
    @NonNull
    private String address = "";
    @Nullable
    private Callback callback;
    @Nullable
    private CbtLinkSessionHandle session;
    /** 是否已经上报过 READY；用于把「就绪后再掉线」识别成 onLost。 */
    private boolean announced;

    public YoClassicRadio(@NonNull YoCbtClient client) {
        this.client = client;
    }

    @NonNull
    public static YoClassicRadio open(@NonNull Context context) {
        Context application = context.getApplicationContext();
        Context target = application == null ? context : application;
        return new YoClassicRadio(YoBluetoothSDK.classic(target));
    }

    @Override
    public void open(@NonNull String classicAddress, @NonNull Callback callback) {
        Objects.requireNonNull(classicAddress, "classicAddress");
        Objects.requireNonNull(callback, "callback");
        cancel();
        int current;
        synchronized (lock) {
            if (released) {
                throw new IllegalStateException("经典蓝牙链路已释放");
            }
            current = ++generation;
            address = classicAddress.trim();
            this.callback = callback;
            announced = false;
        }
        CbtDeviceId deviceId = CbtDeviceId.fromAddress(address);
        CbtLinkSessionHandle handle = client.openLinkSession(
                deviceId,
                CbtLinkSessionConfig.defaults(),
                new CbtLinkSessionObserver() {
                    @Override
                    public void onReadinessChanged(@NonNull CbtDeviceId id,
                                                   @NonNull CbtLinkReadiness readiness) {
                        if (readiness == CbtLinkReadiness.READY) {
                            notifyReady(current, id);
                        } else {
                            notifyLost(current, id);
                        }
                    }

                    @Override
                    public void onLinkFailed(@NonNull CbtDeviceId id, @NonNull CbtFailure failure) {
                        notifyFailed(current, failure);
                    }

                    @Override
                    public void onLinkCancelled(@NonNull CbtDeviceId id) {
                        // 主动取消不产生业务回调。
                    }
                });
        synchronized (lock) {
            if (current != generation) {
                handle.cancel();
                return;
            }
            session = handle;
        }
    }

    @Override
    public void cancel() {
        CbtLinkSessionHandle s;
        synchronized (lock) {
            generation++;
            callback = null;
            announced = false;
            s = session;
            session = null;
        }
        if (s != null) {
            s.cancel();
        }
    }

    @Override
    public void release() {
        synchronized (lock) {
            released = true;
        }
        cancel();
        client.release();
    }

    private void notifyReady(int current, @NonNull CbtDeviceId id) {
        if (!same(current, id)) {
            return;
        }
        Callback cb;
        synchronized (lock) {
            if (current != generation || announced) {
                return;
            }
            announced = true;
            cb = callback;
        }
        if (cb != null) {
            cb.onReady();
        }
    }

    private void notifyLost(int current, @NonNull CbtDeviceId id) {
        if (!same(current, id)) {
            return;
        }
        Callback cb;
        synchronized (lock) {
            // 只有「已就绪过」的掉线才上报 onLost；就绪前的 DOWN/UNKNOWN 交由库继续收敛。
            if (current != generation || !announced) {
                return;
            }
            announced = false;
            cb = callback;
        }
        if (cb != null) {
            cb.onLost();
        }
    }

    private void notifyFailed(int current, @NonNull CbtFailure failure) {
        Callback cb;
        synchronized (lock) {
            if (current != generation) {
                return;
            }
            generation++;
            cb = callback;
            callback = null;
        }
        if (cb != null) {
            cb.onFailed(mapFailure(failure));
        }
    }

    private boolean same(int current, @Nullable CbtDeviceId id) {
        String target;
        synchronized (lock) {
            if (current != generation) {
                return false;
            }
            target = address;
        }
        return id != null && target.equals(id.value());
    }

    @NonNull
    private static HsControl.Failure mapFailure(@Nullable Failure failure) {
        if (!(failure instanceof CbtFailure cbtFailure)) {
            return HsControl.Failure.INTERNAL_ERROR;
        }
        return switch (cbtFailure.errorCode()) {
            case BLUETOOTH_DISABLED -> HsControl.Failure.BLUETOOTH_DISABLED;
            case PERMISSION_DENIED -> HsControl.Failure.PERMISSION_DENIED;
            case LINK_READINESS_TIMEOUT -> HsControl.Failure.TIMEOUT;
            case LINK_READINESS_REJECTED, BOND_FAILED -> HsControl.Failure.REJECTED;
            default -> HsControl.Failure.DEVICE_ERROR;
        };
    }
}
