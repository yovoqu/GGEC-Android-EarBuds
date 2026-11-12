package com.yovo.biz_ggec_hs.internal.link;





import com.yovo.biz_ggec_hs.api.HsControl;
/**
 * 会话连接态：外部能看到的状态 + 结束原因（失败分类，正常收尾为 {@code null}）。
 *
 * <p>不可变。迁移只由会话实现从连接事件处发起，读侧永远看到一份自洽的
 * 「状态 + 原因」快照，不会出现状态已变、原因还是上一次的残值。</p>
 */
public final class HsSessionState {

    private static final HsSessionState IDLE = new HsSessionState(HsControl.State.IDLE, null);
    private static final HsSessionState CONNECTING =
            new HsSessionState(HsControl.State.CONNECTING, null);
    private static final HsSessionState READY = new HsSessionState(HsControl.State.READY, null);

    private final HsControl.State connection;
    private final HsControl.Failure failure;

    private HsSessionState(HsControl.State connection, HsControl.Failure failure) {
        this.connection = connection;
        this.failure = failure;
    }

    static HsSessionState idle() {
        return IDLE;
    }

    static HsSessionState connecting() {
        return CONNECTING;
    }

    static HsSessionState ready() {
        return READY;
    }

    static HsSessionState disconnected(HsControl.Failure failure) {
        return new HsSessionState(HsControl.State.DISCONNECTED, failure);
    }

    public HsControl.State connection() {
        return connection;
    }

    HsControl.Failure failure() {
        return failure;
    }
}
