package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.session.DeviceSession;

import java.util.Objects;

/** 关闭当前设备会话。台账与档案保留，下一次打开还能用。 */
public final class CloseDevice {

    private final DeviceSession session;

    public CloseDevice(DeviceSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public void execute() {
        session.close();
    }
}
