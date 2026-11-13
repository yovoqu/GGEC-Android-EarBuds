package com.yovo.biz_ggec_hs.internal.scan;

import static org.junit.Assert.assertEquals;

import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsScan;

import org.junit.Test;

import java.util.List;

public final class HsScanOrderTest {

    @Test
    public void newcomersInsertByRssi() {
        List<HsScan.Found> ordered = HsScanOrder.arrange(List.of(
                found("AA", -70),
                found("BB", -40),
                found("CC", -55)), List.of());

        assertEquals(List.of("BB", "CC", "AA"), addresses(ordered));
    }

    @Test
    public void smallRssiDriftDoesNotSwapNeighbors() {
        List<HsScan.Found> previous = List.of(found("AA", -50), found("BB", -54));
        List<HsScan.Found> ordered = HsScanOrder.arrange(List.of(
                found("AA", -53),
                found("BB", -50)), previous);

        assertEquals(List.of("AA", "BB"), addresses(ordered));
    }

    @Test
    public void neighborSwapsWhenGapReachesTheBand() {
        List<HsScan.Found> previous = List.of(found("AA", -40), found("BB", -50));
        List<HsScan.Found> ordered = HsScanOrder.arrange(List.of(
                found("AA", -60),
                found("BB", -40)), previous);

        assertEquals(List.of("BB", "AA"), addresses(ordered));
    }

    private static List<String> addresses(List<HsScan.Found> found) {
        return found.stream().map(item -> item.radios().bleAddress()).toList();
    }

    private static HsScan.Found found(String address, int rssi) {
        return new HsScan.Found("HS01", new HsDevice.Radios(address, "", "", ""), rssi);
    }
}
