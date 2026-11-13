package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.session.DeviceSession;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 打开一台设备：已是当前就绪设备就直接成功，缺经典地址就明确失败，否则交给会话。
 *
 * <p>"缺经典地址"是双模链路的前置条件缺失，属于产品分支，不在这里吞掉也不假装成功。</p>
 */
public final class OpenDevice {

    private final DeviceSession session;

    public OpenDevice(DeviceSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public void execute(DeviceTarget target, Consumer<ConnectSettlement> onSettled) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(onSettled, "onSettled");
        DeviceTarget active = session.target().orElse(null);
        if (active != null && active.is(target.id())
                && session.current().connection() == ConnectionState.READY) {
            onSettled.accept(new ConnectSettlement.Ready());
            return;
        }
        if (!target.identity().hasClassicAddress()) {
            onSettled.accept(new ConnectSettlement.Failed(DeviceFailure.INCOMPLETE_IDENTITY));
            return;
        }
        session.open(target, onSettled);
    }
}
