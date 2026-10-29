package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SaveChatPreferences;
import com.ggec.earbuds.domain.ai.usecase.SaveVoiceSettings;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.List;
import java.util.Objects;

/**
 * 设置总览页的 ViewModel：只经用例读写设置，把结果交给 {@link AiSettingsUiMapper} 变成界面状态。
 *
 * <p>它不认识 Context、不认识视图、也不认识仓库实现，所有调用都是同步的用例调用——
 * 设置本身很小，读一次磁盘不值得再开线程。</p>
 */
public final class AiSettingsViewModel extends ViewModel {

    private final LoadAiSettings loadSettings;
    private final ObserveAiSettings observeSettings;
    private final SaveChatPreferences savePreferences;
    private final SaveVoiceSettings saveVoice;
    private final LoadPlatformTemplates loadTemplates;
    private final AiSettingsUiMapper mapper;

    private final MutableLiveData<AiSettingsUiState> uiState = new MutableLiveData<>(AiSettingsUiState.empty());
    private final OneShotLiveData<AiSettingsEvent> events = new OneShotLiveData<>();

    private volatile AiSettings latest = AiSettings.EMPTY;
    private volatile List<AiPlatformTemplate> templates = List.of();
    private Subscription subscription;
    private boolean started;

    public AiSettingsViewModel(@NonNull LoadAiSettings loadSettings,
                               @NonNull ObserveAiSettings observeSettings,
                               @NonNull SaveChatPreferences savePreferences,
                               @NonNull SaveVoiceSettings saveVoice,
                               @NonNull LoadPlatformTemplates loadTemplates,
                               @NonNull AiSettingsUiMapper mapper) {
        this.loadSettings = Objects.requireNonNull(loadSettings, "loadSettings");
        this.observeSettings = Objects.requireNonNull(observeSettings, "observeSettings");
        this.savePreferences = Objects.requireNonNull(savePreferences, "savePreferences");
        this.saveVoice = Objects.requireNonNull(saveVoice, "saveVoice");
        this.loadTemplates = Objects.requireNonNull(loadTemplates, "loadTemplates");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @NonNull
    public LiveData<AiSettingsUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<AiSettingsEvent> events() {
        return events;
    }

    /** 可以添加的平台模板；「添加平台」对话框按它列候选。 */
    @NonNull
    public List<AiPlatformTemplate> templates() {
        return templates;
    }

    /**
     * 进页面时调一次。配置页在返回后可能被重建，所以这里幂等：已经开始了就什么都不做。
     */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        try {
            templates = loadTemplates.execute();
        } catch (RuntimeException unreadable) {
            // 模板表是编译期固定的，读不出来就没有可添加的平台；设置本身照常显示。
            templates = List.of();
        }
        onSettings(loadSettings.execute());
        subscription = observeSettings.execute(this::onSettings);
    }

    public void selectMode(@NonNull AiMode mode) {
        guard(() -> saveVoice.withMode(mode));
    }

    /** 回答风格现在只有这一项可改：提示词写什么，模型就按什么口径回答。 */
    public void saveSystemPrompt(@NonNull String systemPrompt) {
        guard(() -> savePreferences.update(preferences -> preferences.withSystemPrompt(systemPrompt)));
    }

    private void onSettings(AiSettings settings) {
        latest = settings;
        publish();
    }

    private void publish() {
        uiState.postValue(mapper.map(latest));
    }

    /**
     * 用例把"输入不合法"和"平台返回失败"分开报，界面也分开显示：
     * 前者是这个字段没填对，后者是网络或账号那边的事。
     */
    private void guard(@NonNull Runnable change) {
        try {
            change.run();
        } catch (AiValidationException invalid) {
            events.postValue(AiSettingsEvent.toast(AiMessages.validation(invalid.error())));
        } catch (AiFailure failure) {
            events.postValue(AiSettingsEvent.toast(AiMessages.failure(failure.kind())));
        } catch (IllegalArgumentException invalid) {
            events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
        }
    }

    @Override
    protected void onCleared() {
        if (subscription != null) {
            subscription.cancel();
        }
    }
}
