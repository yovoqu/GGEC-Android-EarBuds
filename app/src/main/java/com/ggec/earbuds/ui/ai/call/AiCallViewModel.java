package com.ggec.earbuds.ui.ai.call;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;

import java.util.Objects;

/**
 * 通话页的 ViewModel：把会话的快照搬给页面、把用户操作搬回会话、挂断时释放会话。
 *
 * <p>这里**不做任何状态推导**。{@link AiVoiceSnapshot} 已经是"页面该画什么"的完整答案，
 * 发言记录、电平、失败原因都在里面；再推导一层就会出现两份状态，动画和文案开始互相打脸。</p>
 *
 * <p>会话由 {@link AiVoiceSessionFactory} 造，所以这一层只知道"有一路会话"，不知道
 * 它背后是 WebSocket 还是别的传输——链路选择属于装配根，不属于界面。</p>
 *
 * <p>生命周期跟着 ViewModel 走而不是 Activity：转屏或深色模式切换会重建页面，
 * 但通话不该断。真正的挂断由 {@link #hangUp()} 和 {@link #onCleared()} 两处触发。</p>
 */
public final class AiCallViewModel extends ViewModel {

    private final AiVoiceSessionFactory factory;
    private final AiCallUiMapper mapper;
    private final MutableLiveData<AiCallUiState> uiState =
            new MutableLiveData<>(AiCallUiState.connecting());

    @Nullable
    private AiVoiceSession session;
    private boolean speakerOn = true;

    public AiCallViewModel(@NonNull AiVoiceSessionFactory factory, @NonNull AiCallUiMapper mapper) {
        this.factory = factory;
        this.mapper = mapper;
    }

    /** 页面唯一的真相来源；{@link #start} 之前是连接中。 */
    @NonNull
    public LiveData<AiCallUiState> uiState() {
        return uiState;
    }

    /**
     * 起一路通话。重复调用（例如从后台回来）不会另起一条会话。
     *
     * <p>这里在主线程上调用会话，会话契约要求实现方自己把采集与网络的初始化投到 IO 执行器；
     * 页面不等它，先画"正在连接"。</p>
     *
     * <p>用哪个语音模型由会话自己去设置里读：页面不传模型，也就不会出现
     * "界面记的选择和设置里已经变了"这种两份状态。缺 Key 时会话会报失败快照。</p>
     */
    public void start() {
        if (session != null) {
            return;
        }
        // 先给页面一个确定的起点：没收到第一帧之前它也能画出"正在连接"。
        uiState.setValue(AiCallUiState.connecting().withSpeakerOn(speakerOn));
        AiVoiceSession created = factory.create();
        session = created;
        created.start(snapshot -> publish(mapper.map(snapshot).withSpeakerOn(speakerOn)));
        created.setSpeakerphone(speakerOn);
    }

    /** 当前是否已经有一条通话会话；页面重建后用来避免重复 start。 */
    public boolean isStarted() {
        return session != null;
    }

    /** 权限拒绝由 View 报告，状态仍只在这里发布。 */
    public void permissionDenied() {
        uiState.setValue(AiCallUiState.permissionDenied().withSpeakerOn(speakerOn));
    }

    /** 静音只停上行；会话用 MUTED 快照确认，页面不自己改按钮状态。 */
    public void setMuted(boolean muted) {
        AiVoiceSession current = session;
        if (current != null) {
            current.setMuted(muted);
        }
    }

    /** 扬声器 / 听筒路由。 */
    public void setSpeakerphone(boolean speaker) {
        speakerOn = speaker;
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withSpeakerOn(speaker));
        }
        AiVoiceSession running = session;
        if (running != null) {
            running.setSpeakerphone(speaker);
        }
    }

    /** 用户插话：停掉正在生成的回答，回到聆听。 */
    public void interrupt() {
        AiVoiceSession current = session;
        if (current != null) {
            current.interrupt();
        }
    }

    /** 挂断。幂等：页面点挂断、以及 ViewModel 被清掉时都会走到这里。 */
    public void hangUp() {
        release();
    }

    @Override
    protected void onCleared() {
        release();
    }

    private void release() {
        AiVoiceSession current = session;
        session = null;
        if (current != null) {
            current.stop();
        }
    }

    private void publish(@NonNull AiCallUiState next) {
        if (Objects.equals(uiState.getValue(), next)) {
            return;
        }
        uiState.postValue(next);
    }
}
