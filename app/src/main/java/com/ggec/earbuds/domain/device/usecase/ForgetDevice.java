package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.port.DeviceRosterRepository;
import com.ggec.earbuds.domain.device.session.DeviceSession;

import java.util.Objects;

/**
 * 忘记一台设备：若是当前活动设备先断开，再从台账删掉（含档案）。
 *
 * <p>只删 APP 侧记录，不动设备的蓝牙绑定。</p>
 */
public final class ForgetDevice {

    private final DeviceRosterRepository roster;
    private final DeviceSession session;

    public ForgetDevice(DeviceRosterRepository roster, DeviceSession session) {
        this.roster = Objects.requireNonNull(roster, "roster");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** @return 该设备是否在台账里；不存在时不断连、不改数据。 */
    public boolean execute(DeviceId id) {
        Objects.requireNonNull(id, "id");
        if (roster.current().find(id).isEmpty()) {
            return false;
        }
        boolean active = session.target().map(target -> target.is(id)).orElse(false);
        if (active) {
            session.close();
        }
        roster.update(current -> current.without(id));
        return true;
    }
}
