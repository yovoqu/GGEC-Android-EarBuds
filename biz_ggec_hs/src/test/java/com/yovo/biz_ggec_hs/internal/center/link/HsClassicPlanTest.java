package com.yovo.biz_ggec_hs.internal.center.link;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.port.HsClassicLink.Audio;
import com.yovo.biz_ggec_hs.internal.center.port.HsClassicLink.Bond;

import org.junit.Test;

public final class HsClassicPlanTest {

    @Test
    public void bondedAudioIsReadyForGatt() {
        assertEquals(HsClassicPlan.Step.READY, HsClassicPlan.step(Bond.BONDED, Audio.UP, false));
    }

    @Test
    public void unknownAudioWaits() {
        assertEquals(HsClassicPlan.Step.WAIT, HsClassicPlan.step(Bond.NONE, Audio.UNKNOWN, false));
        assertEquals(HsClassicPlan.Step.WAIT, HsClassicPlan.step(Bond.BONDED, Audio.UNKNOWN, false));
    }

    @Test
    public void unpairedDeviceBondsOnceThenFailsIfStillUnpaired() {
        assertEquals(HsClassicPlan.Step.CREATE_BOND, HsClassicPlan.step(Bond.NONE, Audio.DOWN, false));
        assertEquals(HsClassicPlan.Step.FAIL, HsClassicPlan.step(Bond.NONE, Audio.DOWN, true));
    }

    @Test
    public void bondedWithoutAudioConnectsProfiles() {
        assertEquals(HsClassicPlan.Step.CONNECT_AUDIO,
                HsClassicPlan.step(Bond.BONDED, Audio.DOWN, false));
    }

    @Test
    public void rejectedBondFollowsTheLiveBondState() {
        assertEquals(HsClassicPlan.Step.WAIT, HsClassicPlan.afterBondRejected(Bond.BONDING));
        assertEquals(HsClassicPlan.Step.CONNECT_AUDIO, HsClassicPlan.afterBondRejected(Bond.BONDED));
        assertEquals(HsClassicPlan.Step.FAIL, HsClassicPlan.afterBondRejected(Bond.NONE));
    }

    @Test
    public void bondingAndBusyAudioOnlyWait() {
        assertEquals(HsClassicPlan.Step.WAIT, HsClassicPlan.step(Bond.BONDING, Audio.DOWN, false));
        assertEquals(HsClassicPlan.Step.WAIT, HsClassicPlan.step(Bond.BONDED, Audio.BUSY, true));
        assertFalse(HsClassicPlan.countsTowardTimeout(HsClassicPlan.Step.WAIT, Bond.BONDING));
        assertTrue(HsClassicPlan.countsTowardTimeout(HsClassicPlan.Step.CONNECT_AUDIO, Bond.BONDED));
    }
}
