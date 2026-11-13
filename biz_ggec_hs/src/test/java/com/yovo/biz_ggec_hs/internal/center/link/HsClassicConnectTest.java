package com.yovo.biz_ggec_hs.internal.center.link;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.port.HsClassicLink;
import com.yovo.biz_ggec_hs.internal.center.port.HsClassicRadio;

import org.junit.Test;

public final class HsClassicConnectTest {

    @Test
    public void unknownAudioDoesNotBondOrConnect() {
        Harness harness = new Harness(BondAudio.unknown());
        harness.connect.open("E8:00:00:00:00:01", harness.callback);
        assertEquals(0, harness.link.bonds);
        assertEquals(0, harness.link.audios);
        assertNull(harness.callback.failure);
        assertTrue(harness.delay.pending());
    }

    @Test
    public void unpairedDeviceBondsOnce() {
        Harness harness = new Harness(new HsClassicLink.Snapshot(
                HsClassicLink.Bond.NONE, HsClassicLink.Audio.DOWN));
        harness.connect.open("E8:00:00:00:00:01", harness.callback);
        assertEquals(1, harness.link.bonds);
        assertEquals(0, harness.link.audios);
    }

    @Test
    public void rejectedBondWhileBondingDoesNotFail() {
        Harness harness = new Harness(new HsClassicLink.Snapshot(
                HsClassicLink.Bond.NONE, HsClassicLink.Audio.DOWN));
        harness.link.onBond = new HsClassicLink.Snapshot(
                HsClassicLink.Bond.BONDING, HsClassicLink.Audio.DOWN);
        harness.link.bondOk = false;
        harness.connect.open("E8:00:00:00:00:01", harness.callback);
        assertEquals(1, harness.link.bonds);
        assertNull(harness.callback.failure);
        assertEquals(0, harness.link.audios);
    }

    @Test
    public void bondedAudioIsReadyWithoutAnotherConnect() {
        Harness harness = new Harness(new HsClassicLink.Snapshot(
                HsClassicLink.Bond.BONDED, HsClassicLink.Audio.UP));
        harness.connect.open("E8:00:00:00:00:01", harness.callback);
        assertTrue(harness.callback.ready);
        assertEquals(0, harness.link.audios);
    }

    @Test
    public void bondedWithoutAudioConnectsOnce() {
        Harness harness = new Harness(new HsClassicLink.Snapshot(
                HsClassicLink.Bond.BONDED, HsClassicLink.Audio.DOWN));
        harness.connect.open("E8:00:00:00:00:01", harness.callback);
        assertEquals(1, harness.link.audios);
        assertNull(harness.callback.failure);
    }

    @Test
    public void timeoutFailsTheAttempt() {
        Harness harness = new Harness(BondAudio.unknown());
        harness.connect.open("E8:00:00:00:00:01", harness.callback);
        harness.delay.fire();
        assertEquals(HsControl.Failure.TIMEOUT, harness.callback.failure);
    }

    @Test
    public void audioDropAfterReadyIsLost() {
        Harness harness = new Harness(new HsClassicLink.Snapshot(
                HsClassicLink.Bond.BONDED, HsClassicLink.Audio.UP));
        harness.connect.open("E8:00:00:00:00:01", harness.callback);
        harness.link.set(new HsClassicLink.Snapshot(
                HsClassicLink.Bond.BONDED, HsClassicLink.Audio.DOWN));
        assertTrue(harness.callback.lost);
    }

    private static final class BondAudio {
        private static HsClassicLink.Snapshot unknown() {
            return new HsClassicLink.Snapshot(HsClassicLink.Bond.NONE, HsClassicLink.Audio.UNKNOWN);
        }
    }

    private static final class Harness {
        private final Link link;
        private final ManualDelay delay = new ManualDelay();
        private final Recording callback = new Recording();
        private final HsClassicConnect connect;

        private Harness(HsClassicLink.Snapshot initial) {
            link = new Link(initial);
            connect = new HsClassicConnect(link, delay);
        }
    }

    private static final class Link implements HsClassicLink {
        private Snapshot snapshot;
        private Listener listener;
        private int bonds;
        private int audios;
        private boolean bondOk = true;
        private Snapshot onBond;

        private Link(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public void watch(String classicAddress, Listener listener) {
            this.listener = listener;
        }

        @Override
        public void unwatch() {
            listener = null;
        }

        @Override
        public Snapshot read(String classicAddress) {
            return snapshot;
        }

        @Override
        public boolean createBond(String classicAddress) {
            bonds++;
            if (onBond != null) {
                snapshot = onBond;
            }
            return bondOk;
        }

        @Override
        public boolean connectAudio(String classicAddress) {
            audios++;
            return true;
        }

        @Override
        public void release() {
        }

        private void set(Snapshot snapshot) {
            this.snapshot = snapshot;
            if (listener != null) {
                listener.onChanged();
            }
        }
    }

    private static final class ManualDelay implements HsClassicConnect.Delay {
        private Runnable task;

        @Override
        public void postDelayed(Runnable task, long delayMillis) {
            this.task = task;
        }

        @Override
        public void removeCallbacks(Runnable task) {
            if (this.task == task) {
                this.task = null;
            }
        }

        private boolean pending() {
            return task != null;
        }

        private void fire() {
            Runnable current = task;
            task = null;
            current.run();
        }
    }

    private static final class Recording implements HsClassicRadio.Callback {
        private boolean ready;
        private boolean lost;
        private HsControl.Failure failure;

        @Override
        public void onReady() {
            ready = true;
        }

        @Override
        public void onFailed(HsControl.Failure failure) {
            this.failure = failure;
        }

        @Override
        public void onLost() {
            lost = true;
        }
    }
}
