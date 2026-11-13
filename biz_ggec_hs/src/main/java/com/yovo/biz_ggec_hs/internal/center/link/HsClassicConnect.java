package com.yovo.biz_ggec_hs.internal.center.link;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.port.HsClassicLink;
import com.yovo.biz_ggec_hs.internal.center.port.HsClassicRadio;

/**
 * 按 {@link HsClassicPlan} 把经典音频链路推到「可以开 GATT」。
 * 链路只提供事实和动作，超时与失败分类在这里。
 */
public final class HsClassicConnect implements HsClassicRadio {

    /** 超时用的延时。测试注入可控实现，生产用主线程 {@link HsMainDelay}。 */
    public interface Delay {
        void postDelayed(Runnable task, long delayMillis);

        void removeCallbacks(Runnable task);
    }

    private final HsClassicLink link;
    private final Delay delay;
    private final Object lock = new Object();

    private int generation;
    private boolean bondAttempted;
    private boolean audioRequested;
    private boolean announced;
    private boolean timeoutPosted;
    private String address = "";
    private HsClassicRadio.Callback callback;
    private Runnable timeout;

    public HsClassicConnect(HsClassicLink link, Delay delay) {
        if (link == null || delay == null) {
            throw new IllegalArgumentException("经典连接依赖不能为空");
        }
        this.link = link;
        this.delay = delay;
    }

    @Override
    public void open(String classicAddress, HsClassicRadio.Callback callback) {
        if (classicAddress == null || classicAddress.isBlank() || callback == null) {
            throw new IllegalArgumentException("经典连接目标不能为空");
        }
        cancel();
        int current;
        synchronized (lock) {
            current = ++generation;
            address = classicAddress;
            this.callback = callback;
            bondAttempted = false;
            audioRequested = false;
            announced = false;
            timeoutPosted = false;
            timeout = () -> finish(current, HsControl.Failure.TIMEOUT, false);
        }
        link.watch(classicAddress, () -> advance(current));
        advance(current);
    }

    @Override
    public void cancel() {
        Runnable pending;
        synchronized (lock) {
            generation++;
            callback = null;
            address = "";
            pending = timeout;
            timeout = null;
            timeoutPosted = false;
        }
        if (pending != null) {
            delay.removeCallbacks(pending);
        }
        link.unwatch();
    }

    @Override
    public void release() {
        cancel();
        link.release();
    }

    private void advance(int current) {
        String target;
        boolean attempted;
        boolean requested;
        boolean alreadyUp;
        synchronized (lock) {
            if (current != generation || callback == null) {
                return;
            }
            target = address;
            attempted = bondAttempted;
            requested = audioRequested;
            alreadyUp = announced;
        }
        HsClassicLink.Snapshot facts = link.read(target);
        HsClassicPlan.Step step = HsClassicPlan.step(facts.bond(), facts.audio(), attempted);
        if (alreadyUp && facts.audio() == HsClassicLink.Audio.DOWN) {
            finish(current, HsControl.Failure.DEVICE_ERROR, true);
            return;
        }
        if (step == HsClassicPlan.Step.READY) {
            finishReady(current);
            return;
        }
        if (step == HsClassicPlan.Step.FAIL) {
            finish(current, HsControl.Failure.DEVICE_ERROR, false);
            return;
        }
        refreshTimeout(current, step, facts.bond());
        if (step == HsClassicPlan.Step.CREATE_BOND && !attempted) {
            markBondAttempted(current);
            if (!link.createBond(target)) {
                apply(current, HsClassicPlan.afterBondRejected(link.read(target).bond()));
            }
            return;
        }
        if (step == HsClassicPlan.Step.CONNECT_AUDIO && !requested) {
            markAudioRequested(current);
            if (!link.connectAudio(target)) {
                apply(current, HsClassicPlan.afterAudioRejected(link.read(target).audio()));
            }
        }
    }

    private void apply(int current, HsClassicPlan.Step step) {
        if (step == HsClassicPlan.Step.READY) {
            finishReady(current);
            return;
        }
        if (step == HsClassicPlan.Step.FAIL) {
            finish(current, HsControl.Failure.DEVICE_ERROR, false);
            return;
        }
        if (step == HsClassicPlan.Step.CONNECT_AUDIO) {
            boolean requested;
            String target;
            synchronized (lock) {
                if (current != generation || address.isEmpty()) {
                    return;
                }
                requested = audioRequested;
                target = address;
                audioRequested = true;
            }
            if (!requested && !link.connectAudio(target)) {
                finish(current, HsControl.Failure.DEVICE_ERROR, false);
            }
            return;
        }
        String target;
        synchronized (lock) {
            if (current != generation || address.isEmpty()) {
                return;
            }
            target = address;
        }
        refreshTimeout(current, step, link.read(target).bond());
    }

    private void markBondAttempted(int current) {
        synchronized (lock) {
            if (current == generation) {
                bondAttempted = true;
            }
        }
    }

    private void markAudioRequested(int current) {
        synchronized (lock) {
            if (current == generation) {
                audioRequested = true;
            }
        }
    }

    private void refreshTimeout(int current, HsClassicPlan.Step step, HsClassicLink.Bond bond) {
        Runnable pending;
        boolean post;
        synchronized (lock) {
            if (current != generation || timeout == null) {
                return;
            }
            pending = timeout;
            if (!HsClassicPlan.countsTowardTimeout(step, bond)) {
                timeoutPosted = false;
                post = false;
            } else if (timeoutPosted) {
                return;
            } else {
                timeoutPosted = true;
                post = true;
            }
        }
        delay.removeCallbacks(pending);
        if (post) {
            delay.postDelayed(pending, HsClassicPlan.WAIT_MILLIS);
        }
    }

    private void finishReady(int current) {
        HsClassicRadio.Callback listener = take(current, true);
        if (listener != null) {
            listener.onReady();
        }
    }

    private void finish(int current, HsControl.Failure failure, boolean lost) {
        HsClassicRadio.Callback listener = take(current, false);
        if (listener == null) {
            return;
        }
        if (lost) {
            listener.onLost();
        } else {
            listener.onFailed(failure);
        }
    }

    private HsClassicRadio.Callback take(int current, boolean ready) {
        Runnable pending;
        HsClassicRadio.Callback listener;
        synchronized (lock) {
            if (current != generation || callback == null) {
                return null;
            }
            if (ready) {
                if (announced) {
                    return null;
                }
                announced = true;
            } else {
                generation++;
            }
            listener = callback;
            if (!ready) {
                callback = null;
            }
            pending = timeout;
            timeout = null;
            timeoutPosted = false;
        }
        if (pending != null) {
            delay.removeCallbacks(pending);
        }
        if (!ready) {
            link.unwatch();
        }
        return listener;
    }
}
