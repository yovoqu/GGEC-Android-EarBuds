package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.session.DeviceSession;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 打开一台设备：缺经典地址就明确失败，否则交给会话。
 *
 * <p>"缺经典地址"是双模链路的前置条件缺失，属于产品分支，不在这里吞掉也不假装成功。
 * 「已是同一台就绪设备」的判定不在这里重复一遍——会话是会话状态的唯一持有者，
 * 它会把这种重复意图直接结算成就绪且不重连。</p>
 */
public final class OpenDevice {

    private final DeviceSession session;

    public OpenDevice(DeviceSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public void execute(DeviceTarget target, Consumer<ConnectSettlement> onSettled) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(onSettled, "onSettled");
        if (!target.identity().hasClassicAddress()) {
            onSettled.accept(new ConnectSettlement.Failed(DeviceFailure.INCOMPLETE_IDENTITY));
            return;
        }
        session.open(target, onSettled);
    }
}
