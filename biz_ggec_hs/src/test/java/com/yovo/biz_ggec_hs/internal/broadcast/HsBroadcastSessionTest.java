package com.yovo.biz_ggec_hs.internal.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.model.broadcast.HsGeneration;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class HsBroadcastSessionTest {
    @Test
    public void dropsAnAssemblyAfterTwoSeconds() {
        long[] now = {0L};
        HsBroadcastSessionImpl session = new HsBroadcastSessionImpl(
                HsBroadcastRegistry.standard(),
                new Hs02BroadcastAssembler(),
                () -> now[0]);

        HsBroadcastReport first = session.observe(HsScanSnapshot.of(
                "11:22:33:44:55:66",
                Collections.emptyList(),
                manufacturerOnly(),
                null));
        assertEquals(HsBroadcastReport.Status.INCOMPLETE, first.status());
        assertEquals(HsGeneration.HS02, first.generation());

        now[0] = 2_000_000_001L;
        HsBroadcastReport later = session.observe(HsScanSnapshot.of(
                "11:22:33:44:55:66",
                Collections.emptyList(),
                completeName("Vidda V11"),
                null));
        assertEquals(HsBroadcastReport.Status.NOT_HS, later.status());
        assertNull(later.generation());
    }

    private static byte[] completeName(String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] field = new byte[nameBytes.length + 2];
        field[0] = (byte) (nameBytes.length + 1);
        field[1] = 0x09;
        System.arraycopy(nameBytes, 0, field, 2, nameBytes.length);
        return field;
    }

    private static byte[] manufacturerOnly() {
        byte[] manufacturer = new byte[]{
                (byte) 0xFF, (byte) 0xFF,
                0x48, 0x43,
                0x01,
                0x02,
                0x12,
                0x01, 0x00,
                0x00, 0x00,
                0x0A, 0x06,
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC,
                (byte) 0xDD, (byte) 0xEE, (byte) 0xFF,
                0x0B, 0x01, 0x00
        };
        byte[] packet = new byte[manufacturer.length + 2];
        packet[0] = (byte) (manufacturer.length + 1);
        packet[1] = (byte) 0xFF;
        System.arraycopy(manufacturer, 0, packet, 2, manufacturer.length);
        return packet;
    }
}
