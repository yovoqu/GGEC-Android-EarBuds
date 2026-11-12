package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBtLogger;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内只跑一个 BLE 扫描。第一个订阅者启动，最后一个取消后停止。
 * 每一帧原始结果发给当前全部订阅者。
 *
 * <p>生命周期由 {@link #subscribe} / {@link #release} 与订阅句柄 cancel 驱动；每个订阅者
 * 共享同一个 {@link Engine}。引擎失败（例如适配器被关闭）只把引擎置为 STOPPED 并保留订阅者：
 * 引擎恢复后经同一个 fanout 继续向同一批订阅者投递帧，订阅句柄仍可 cancel；再次订阅同一引擎会重启它。</p>
 *
 * <p><b>锁归属：</b>本类只有 {@link #GATE} 一把锁，它只保护会话状态与订阅者集合。持有 GATE 期间
 * 绝不调用 {@link Engine#start} / {@link Engine#stop}，也绝不投递订阅者回调，所以
 * {@link BleOperationHandle} 的监视器（cancel → detach）与 GATE 之间不存在锁序环：没有任何线程会
 * 在持有 GATE 时去等一个正被取消路径持有的监视器。</p>
 *
 * <p><b>启动归属：</b>一次启动由唯一线程拥有，代次记在 {@link #startEpoch}。启动线程在锁外调用
 * {@link Engine#start}，再回锁内按「代次 + hub 归属 + 订阅者非空」二次确认；确认作废时仍由同一个
 * 线程按「停扫 → 必要时重新取一次代次启动」收尾，因此补偿停扫永远排在它自己的启动之后，
 * 不会落到之后才开始的会话上。</p>
 *
 * <p><b>终态归属：</b>每次 {@link Engine#start} 都用 {@link #newFanout(long)} 建一个绑定该代次的
 * 回调入口交给引擎，引擎只能把某次会话的 started / failed / stopped 交回给它发起的代次。终态到达时
 * 只有「该代次仍是当前会话」才允许迁移 {@link #engineState} 与分发订阅者；被作废代次迟到的终态一律
 * 吸收（不改状态、不分发、只记日志）。停扫是异步终态：{@code engine.stop()} 只把停扫任务投递给引擎
 * 的扫描状态线程，补偿重启可能已经让新代次跑起来，因此没有代次归属的终态会把新会话误标为 STOPPED，
 * 并把「旧会话结束」分发给停扫窗口内新挂上的订阅者。</p>
 *
 * <p><b>分发归属：</b>终态分发集合与终态判定必须是同一个临界区里的事实：每次 started / failed /
 * stopped 都在 {@link #GATE} 内先判定代次与状态，并在同一把锁内 {@code List.copyOf(subscribers)}
 * 取定本次分发的接收者，再在锁外投递。锁外读 {@code subscribers} 会把「判定之后、投递之前」才
 * 挂上的订阅者算进本次分发——判定把会话置 STOPPED 之后新订阅会开新代次，那个订阅者属于新会话，
 * 收到旧会话的终态会让业务把自己的会话收掉（见 {@code HsNearbyScanCallbacks}）。</p>
 *
 * <p><b>迁移归属：</b>停扫与补偿启动都不在 {@link BleOperationHandle} 的取消调用栈上执行：
 * {@code detach} 只在 GATE 内登记待办，由 {@link #TRANSITIONS} 单线程在锁外推进。句柄监视器上
 * 既没有引擎调用也没有业务回调，两个订阅在 {@code onScanFailed} 里互 cancel 时不会形成监视器级
 * 锁序环。</p>
 */
public final class YoBleScanHub {

    public interface Engine {
        @NonNull
        BleOperationHandle start(@NonNull BleScanObserver fanout);

        @NonNull
        Result<Void> stop();
    }

    /**
     * 会话状态。所有迁移都在 {@link #GATE} 内完成。
     * STARTING 表示启动在途、STOPPING 表示停扫在途；这两种状态下新的订阅者只挂载，不再启动引擎。
     */
    private enum EngineState {
        IDLE,
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED
    }

    /** 启动线程二次确认的结果。 */
    private enum StartOutcome {
        /** 本次启动成为运行会话。 */
        RUNNING,
        /** 引擎在 start() 内同步上报失败：会话保留，不重复停扫。 */
        SYNC_FAILED,
        /** 本次启动已作废，由本线程补偿停扫。 */
        INVALIDATED
    }

    private static final String TAG = "YoBleScanHub";
    private static final Object GATE = new Object();
    private static final AtomicLong SUBSCRIPTION_SEQ = new AtomicLong();

    /**
     * 会话迁移线程：停扫与补偿重启从这里发出，绝不在订阅句柄的取消调用栈上调用引擎，
     * 因此句柄监视器不会与业务回调、也不会与另一个句柄的监视器形成锁序环。
     * 进程内只有这一个扫描器，所以这一个守护线程随进程常驻（库内没有它的所有者可关闭它）。
     */
    private static final ExecutorService TRANSITIONS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "YoBleScanHub-transition");
        thread.setDaemon(true);
        return thread;
    });

    @Nullable
    private static YoBleScanHub current;

    @NonNull
    private final Engine engine;
    private final CopyOnWriteArrayList<BleScanObserver> subscribers = new CopyOnWriteArrayList<>();
    private EngineState engineState = EngineState.IDLE;
    /** 已分配的启动代次。 */
    private long epochSeq;
    /** 当前被授权的会话代次：只有持有它的线程可以确认并收尾这次启动。 */
    private long startEpoch;
    /** 引擎在 start() 内同步失败时对应的启动代次；确认阶段据此判断“失败已处理、不再停扫”。 */
    private long failedEpoch;

    private YoBleScanHub(@NonNull Engine engine) {
        this.engine = engine;
    }

    /**
     * 订阅原始扫描帧。首个订阅者启动引擎；引擎启动在锁外进行，启动成功与否由
     * {@link #confirmStart(long)} 在锁内二次确认。
     */
    @NonNull
    public static BleOperationHandle subscribe(@NonNull Engine engine,
                                               @NonNull BleScanObserver observer) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(observer, "observer");
        YoBleScanHub hub;
        long epoch;
        Engine retiredEngine = null;
        synchronized (GATE) {
            if (current != null && current.engine != engine) {
                // 旧 hub 绑定的是已被替换/释放的引擎：在锁内决定退役，出锁后再停旧引擎。
                retiredEngine = current.retireLocked();
            }
            if (current == null) {
                current = new YoBleScanHub(engine);
            }
            hub = current;
            epoch = hub.attach(observer);
        }
        if (retiredEngine != null) {
            retiredEngine.stop();
        }
        if (epoch != 0L) {
            hub.startClaim(epoch);
        }
        return new BleOperationHandle(
                "ble-scan-subscription-" + SUBSCRIPTION_SEQ.incrementAndGet(),
                () -> hub.detach(observer));
    }

    /**
     * 释放绑定到该引擎的进程内扫描器：清空订阅者并丢弃 hub。引擎资源由引擎自身释放，
     * 此处不再调用 {@link Engine#stop()}，避免与控制器释放路径重复停扫；若此刻仍有在途启动，
     * 它的二次确认会看到归属已失效并补齐停扫。
     */
    public static void release(@NonNull Engine engine) {
        Objects.requireNonNull(engine, "engine");
        synchronized (GATE) {
            if (current == null || current.engine != engine) {
                return;
            }
            current.subscribers.clear();
            current.engineState = EngineState.STOPPED;
            current = null;
        }
    }

    /** 测试用：丢掉进程内扫描器，避免用例互相粘连。 */
    @VisibleForTesting
    static void resetForTest() {
        synchronized (GATE) {
            if (current == null) {
                return;
            }
            current.subscribers.clear();
            current.engineState = EngineState.STOPPED;
            current = null;
        }
    }

    /** 测试用：当前 hub 的引擎是否处于 RUNNING（观察失败后自动恢复的分支）。 */
    @VisibleForTesting
    static boolean isEngineRunningForTest() {
        synchronized (GATE) {
            return current != null && current.engineState == EngineState.RUNNING;
        }
    }

    /**
     * 测试用：往会话迁移队列里投一个栅栏任务，返回它在队列排空时放行的闸门。
     * 用例据此拿到「detach 的停扫/补偿重启已经落地」这个确定点，用条件通知而不是拿线程调度当同步。
     */
    @VisibleForTesting
    @NonNull
    static CountDownLatch submitTransitionBarrierForTest() {
        CountDownLatch barrier = new CountDownLatch(1);
        TRANSITIONS.execute(barrier::countDown);
        return barrier;
    }

    /**
     * 挂载订阅者，并决定本线程是否成为本次启动的所有者（调用方持有 GATE）。
     *
     * @return 需要在锁外发起的启动代次；0 表示启动在途/运行中，本次挂载只共享既有会话
     */
    private long attach(@NonNull BleScanObserver observer) {
        subscribers.add(observer);
        if (engineState == EngineState.IDLE || engineState == EngineState.STOPPED) {
            engineState = EngineState.STARTING;
            startEpoch = ++epochSeq;
            return startEpoch;
        }
        return 0L;
    }

    /**
     * 启动所有者：锁外启动引擎，锁内二次确认。确认作废时本线程独占收尾，
     * 因此启动与补偿停扫严格按顺序由同一线程发出，不会被并发取消插队。
     */
    private void startClaim(long initialEpoch) {
        long epoch = initialEpoch;
        while (true) {
            engine.start(newFanout(epoch));
            if (confirmStart(epoch) != StartOutcome.INVALIDATED) {
                return;
            }
            // 作废的这一代由本线程收尾：登记代次就是被作废的 epoch，收尾时据此判断引擎归属
            // 是否已经交给后来的启动（见 finishStopAndMaybeResume）。
            long resumedEpoch = finishStopAndMaybeResume(epoch);
            if (resumedEpoch == 0L) {
                return;
            }
            epoch = resumedEpoch;
        }
    }

    /**
     * 启动的二次锁内确认（调用方不持锁）：代次仍是自己的、hub 仍是归属者、订阅者非空才算启动成功。
     * 确认作废时把会话交给本线程收尾（阻止新启动插进停扫窗口）。
     *
     * <p>到达这里时 {@link #startEpoch} 必然仍是 {@code epoch}：STARTING 期间 {@link #attach} 不会
     * 分配新代次；唯一能把 STARTING 改成 STOPPED 的失败上报绑定的是同一个代次，会先命中
     * {@link #failedEpoch}；{@link #release} / {@link #resetForTest} 会清掉 hub 归属，命中第一条分支。</p>
     */
    @NonNull
    private StartOutcome confirmStart(long epoch) {
        synchronized (GATE) {
            if (!holdsOwnership()) {
                // 归属已失效（释放或换引擎）：本次启动必须由本线程停掉，不留无人接收的会话。
                engineState = EngineState.STOPPING;
                return StartOutcome.INVALIDATED;
            }
            if (failedEpoch == epoch) {
                // 引擎在 start() 内同步失败：fanout 已把会话置 STOPPED 并保留订阅者，不重复停扫。
                return StartOutcome.SYNC_FAILED;
            }
            if (engineState == EngineState.STARTING && startEpoch == epoch && !subscribers.isEmpty()) {
                engineState = EngineState.RUNNING;
                return StartOutcome.RUNNING;
            }
            engineState = EngineState.STOPPING;
            return StartOutcome.INVALIDATED;
        }
    }

    /**
     * 停扫收尾（调用方已把会话置 STOPPING 并登记本次停扫的代次）：先停引擎，再按会话的真实状态
     * 决定是否重新取一次代次启动。停扫期间到达的订阅者不会自己启动引擎，由这里接手。
     *
     * <p>重启的前提是下面三条同时成立，每条都直接对应本类状态机里的一个事实，不靠额外标志位：</p>
     * <ol>
     *   <li><b>仍有订阅者</b>：{@code subscribers} 非空。空集合意味着没人要扫描帧，重启只会造出
     *       一个没有接收者的会话。</li>
     *   <li><b>当前没有运行中的引擎</b>：{@code engineState} 既不是 STARTING 也不是 RUNNING。
     *       停扫是异步终态，等待期间同一代次可能已经在 {@code onScanFailed} 里把会话置 STOPPED
     *       ——那说明引擎确实已经停了（{@code YoBleScanController.stopScan()} 在无活动会话时甚至
     *       直接回 ILLEGAL_STATE），此时必须重启，否则停扫窗口内挂上的订阅者会停在 STOPPED 且
     *       没有引擎。反过来，若别人已经拿到新代次（STARTING 启动在途 / RUNNING 已运行），引擎
     *       归属就是那一次启动的，这里再启一次会并存两个会话。</li>
     *   <li><b>本次停扫仍是最新登记</b>：{@code registeredEpoch == epochSeq}。登记之后若已有人分配
     *       了新代次（STOPPED 时新订阅 {@link #attach} 会 {@code ++epochSeq}），引擎归属已经交给
     *       那次启动；本次收尾只负责把引擎停掉，不得再启。</li>
     * </ol>
     *
     * <p>只在迁移线程或启动所有者线程上调用，绝不在订阅句柄的取消调用栈上调用。</p>
     *
     * @param registeredEpoch 登记本次停扫时的会话代次（最后一条判据的基准）
     * @return 需要重新发起的启动代次；0 表示本次收尾不启动
     */
    private long finishStopAndMaybeResume(long registeredEpoch) {
        engine.stop();
        synchronized (GATE) {
            boolean stillRegistered = registeredEpoch == epochSeq;
            boolean engineClaimed = engineState == EngineState.STARTING
                    || engineState == EngineState.RUNNING;
            if (holdsOwnership() && stillRegistered && !engineClaimed && !subscribers.isEmpty()) {
                engineState = EngineState.STARTING;
                startEpoch = ++epochSeq;
                return startEpoch;
            }
            if (engineState == EngineState.STOPPING) {
                // 只有「本次停扫仍停在这里」才落到 STOPPED；其它状态代表归属已经转移
                // （新代次启动中/运行中、或 hub 已被丢弃），覆盖它会把别人的会话踩掉。
                engineState = EngineState.STOPPED;
            }
            return 0L;
        }
    }

    /** 停扫窗口的推进：停引擎，然后按真实会话状态决定是否补偿启动（运行在 {@link #TRANSITIONS}）。 */
    private void stopThenResume(long registeredEpoch) {
        long resumedEpoch = finishStopAndMaybeResume(registeredEpoch);
        if (resumedEpoch != 0L) {
            startClaim(resumedEpoch);
        }
    }

    /**
     * 退订。只登记待办：本方法在订阅句柄的监视器里被调用，因此既不调引擎也不分发业务回调，
     * 停扫与可能的补偿启动交给 {@link #TRANSITIONS} 在锁外推进。
     */
    private void detach(@NonNull BleScanObserver observer) {
        long registeredEpoch;
        synchronized (GATE) {
            subscribers.remove(observer);
            boolean stopNeeded = subscribers.isEmpty() && holdsOwnership()
                    && engineState != EngineState.STARTING
                    && engineState != EngineState.STOPPING;
            if (!stopNeeded) {
                return;
            }
            engineState = EngineState.STOPPING;
            // 登记本次停扫所属的代次：收尾时据此确认引擎归属没有交给后来的启动。
            registeredEpoch = startEpoch;
        }
        // 最后一个订阅者离开必须停扫：引擎停止还要放弃适配器恢复现场（见 YoBleScanController）。
        TRANSITIONS.execute(() -> stopThenResume(registeredEpoch));
    }

    /**
     * 丢弃本 hub 的会话归属（调用方持有 GATE）。
     *
     * @return 需要在本锁之外停止的引擎；无活动会话时返回 {@code null}
     */
    @Nullable
    private Engine retireLocked() {
        if (current == this) {
            current = null;
        }
        subscribers.clear();
        boolean live = engineState == EngineState.STARTING || engineState == EngineState.RUNNING;
        engineState = live ? EngineState.STOPPING : EngineState.STOPPED;
        return live ? engine : null;
    }

    /** 当前 hub 是否仍是进程内扫描器的归属者（调用方持有 {@link #GATE}）。 */
    private boolean holdsOwnership() {
        return current == this;
    }

    /**
     * 为一个代次创建回调入口：引擎把该代次的 started / failed / stopped 交回这里，
     * 终态据此判断是否属于当前会话（见类注释「终态归属」）。
     */
    @NonNull
    private BleScanObserver newFanout(long epoch) {
        return new BleScanObserver() {
            @Override
            public void onScanStarted() {
                List<BleScanObserver> targets;
                long currentEpoch;
                synchronized (GATE) {
                    // 判定与分发集合在同一临界区取定：锁内取快照，锁外只投递快照。
                    // 锁外读 subscribers 会把「判定之后才挂上」的订阅者算进这一次开始，
                    // 判定把会话置 STOPPED 之后新订阅已经属于新代次。
                    currentEpoch = startEpoch;
                    boolean live = holdsOwnership() && epoch == startEpoch;
                    targets = live ? List.copyOf(subscribers) : null;
                    if (live && engineState == EngineState.STOPPED && !subscribers.isEmpty()) {
                        // 引擎在失败后自行恢复（例如适配器重新开启）：订阅者未变，恢复 RUNNING。
                        engineState = EngineState.RUNNING;
                    }
                }
                if (targets == null) {
                    YoBtLogger.d(TAG, "absorb onScanStarted of epoch " + epoch
                            + ", current epoch " + currentEpoch);
                    return;
                }
                for (BleScanObserver observer : targets) {
                    observer.onScanStarted();
                }
            }

            @Override
            public void onDeviceFound(@NonNull BleScanResult result) {
                for (BleScanObserver observer : subscribers) {
                    observer.onDeviceFound(result);
                }
            }

            @Override
            public void onBatchScanResults(@NonNull List<BleScanResult> results) {
                for (BleScanObserver observer : subscribers) {
                    observer.onBatchScanResults(results);
                }
            }

            @Override
            public void onScanResultLost(@NonNull BleScanResult result) {
                for (BleScanObserver observer : subscribers) {
                    observer.onScanResultLost(result);
                }
            }

            @Override
            public void onScanFailed(@NonNull BleFailure failure) {
                List<BleScanObserver> targets;
                long currentEpoch;
                synchronized (GATE) {
                    currentEpoch = startEpoch;
                    // 早退按代次判定，不按 engineState：停扫窗口里新代次可能已经在跑，
                    // 平台对它的真实失败必须送达当前订阅者；被作废代次的失败才被吸收。
                    boolean live = holdsOwnership() && epoch == startEpoch;
                    targets = live ? List.copyOf(subscribers) : null;
                    if (live) {
                        // 失败不销毁会话：订阅者与 hub 都保留，引擎恢复后仍向同一批订阅者投递帧。
                        // 本代次记为失败代次：它的启动线程确认时不再重复停扫。
                        failedEpoch = epoch;
                        engineState = EngineState.STOPPED;
                    }
                }
                if (targets == null) {
                    YoBtLogger.d(TAG, "absorb onScanFailed of void epoch " + epoch
                            + ", current epoch " + currentEpoch);
                    return;
                }
                for (BleScanObserver observer : targets) {
                    observer.onScanFailed(failure);
                }
            }

            @Override
            public void onScanStopped() {
                List<BleScanObserver> targets;
                long currentEpoch;
                synchronized (GATE) {
                    currentEpoch = startEpoch;
                    // 终态按发起代次归属：只有这一代仍是当前运行会话时，才允许迁移状态并通知订阅者。
                    // 停扫是异步终态——补偿重启可能已让新代次跑起来——所以这里必须比对代次，
                    // 否则旧会话的结束会把新会话置 STOPPED，并把「扫描已结束」发给停扫窗口内新挂上的订阅者。
                    // 判定置 STOPPED 与分发集合取定必须在同一临界区：置 STOPPED 之后新订阅会开新代次，
                    // 锁外读 subscribers 会把那个新代次里的订阅者算进本次结束，业务据此 end()+onFinished()，
                    // 之后所有帧都被 isCurrent 挡住。
                    boolean endsSession = holdsOwnership()
                            && epoch == startEpoch
                            && engineState == EngineState.RUNNING;
                    targets = endsSession ? List.copyOf(subscribers) : null;
                    if (endsSession) {
                        engineState = EngineState.STOPPED;
                    }
                }
                if (targets == null) {
                    YoBtLogger.d(TAG, "absorb onScanStopped of epoch " + epoch
                            + ", current epoch " + currentEpoch);
                    return;
                }
                for (BleScanObserver observer : targets) {
                    observer.onScanStopped();
                }
            }
        };
    }
}
