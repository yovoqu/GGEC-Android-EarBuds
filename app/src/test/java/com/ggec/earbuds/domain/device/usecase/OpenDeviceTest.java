package com.ggec.earbuds.domain.device.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.session.DeviceSession;
import com.ggec.earbuds.testing.device.DeviceFixtures;
import com.ggec.earbuds.testing.device.FakeDeviceLinkPort;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class OpenDeviceTest {

    @Test
    public void missingClassicAddressFailsWithoutTouchingTheLink() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        OpenDevice useCase = new OpenDevice(new DeviceSession(port));
        List<ConnectSettlement> outcomes = new ArrayList<>();

        useCase.execute(DeviceFixtures.targetWithoutClassic(), outcomes::add);

        assertEquals(0, port.connects.size());
        assertTrue(outcomes.get(0) instanceof ConnectSettlement.Failed failed
                && failed.failure() == DeviceFailure.INCOMPLETE_IDENTITY);
    }

    @Test
    public void alreadyReadyDeviceSettlesWithoutReconnecting() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        OpenDevice useCase = new OpenDevice(session);
        useCase.execute(DeviceFixtures.target(), settlement -> {
        });
        port.emit(DeviceFixtures.ready("1.0.0", 60));

        List<ConnectSettlement> outcomes = new ArrayList<>();
        useCase.execute(DeviceFixtures.target(), outcomes::add);

        assertEquals(1, port.connects.size());
        assertEquals(1, outcomes.size());
        assertTrue(outcomes.get(0) instanceof ConnectSettlement.Ready);
    }
}
