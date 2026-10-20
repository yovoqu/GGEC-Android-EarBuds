package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SaveChatPreferences;
import com.ggec.earbuds.domain.ai.usecase.SavePlatform;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.ggec.earbuds.ui.ai.common.AiSettingsFeed;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * AI 设置总览页：当前模型、平台列表、对话参数。表单输入先在这里同步校验，
 * 校验通过再丢到后台写入，这样对话框可以当场把错误显示在输入框上。
 */
public final class AiSettingsViewModel extends ViewModel {

    private final SavePlatform savePlatform;
    private final SaveChatPreferences savePreferences;
    private final AiChatSession session;
    private final AiApiKeyPolicy keyPolicy;
    private final AiBaseUrlPolicy urlPolicy;
    private final AiSettingsUiMapper mapper;
    private final Executor io;
    private final List<AiPlatformTemplate> templates;
    private final AiSettingsFeed feed;

    private final MutableLiveData<AiSettingsUiState> uiState = new MutableLiveData<>();
    private final OneShotLiveData<AiSettingsEvent> events = new OneShotLiveData<>();

    public AiSettingsViewModel(
            @NonNull LoadAiSettings loadSettings,
            @NonNull ObserveAiSettings observeSettings,
            @NonNull LoadPlatformTemplates loadTemplates,
            @NonNull SavePlatform savePlatform,
            @NonNull SaveChatPreferences savePreferences,
            @NonNull AiChatSession session,
            @NonNull AiApiKeyPolicy keyPolicy,
            @NonNull AiBaseUrlPolicy urlPolicy,
            @NonNull AiSettingsUiMapper mapper,
            @NonNull Executor io) {
        this.savePlatform = Objects.requireNonNull(savePlatform, "savePlatform");
        this.savePreferences = Objects.requireNonNull(savePreferences, "savePreferences");
        this.session = Objects.requireNonNull(session, "session");
        this.keyPolicy = Objects.requireNonNull(keyPolicy, "keyPolicy");
        this.urlPolicy = Objects.requireNonNull(urlPolicy, "urlPolicy");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.io = Objects.requireNonNull(io, "io");
        this.templates = Objects.requireNonNull(loadTemplates, "loadTemplates").execute();
        this.feed = new AiSettingsFeed(loadSettings, observeSettings, io, this::publish, this::onLoadFailed);
    }

    @NonNull
    public LiveData<AiSettingsUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<AiSettingsEvent> events() {
        return events;
    }

    /** 可新增的自定义平台模板（OpenAI 兼容、Anthropic 兼容）。 */
    @NonNull
    public List<AiPlatformTemplate> customTemplates() {
        return templates.stream().filter(AiPlatformTemplate::custom).toList();
    }

    /**
     * 同步校验并在通过后异步创建自定义平台，让对话框可以把错误直接显示在输入框上。
     *
     * @return 校验错误，通过则为空；创建成功后会发出打开该平台详情页的事件
     */
    @Nullable
    public AiValidationError addCustomPlatform(
            @NonNull String templateId, @NonNull String name, @NonNull String baseUrl, @NonNull String apiKey) {
        try {
            if (name.isBlank()) {
                return AiValidationError.NAME_EMPTY;
            }
            urlPolicy.normalize(baseUrl, true);
            keyPolicy.normalize(apiKey);
        } catch (AiValidationException invalid) {
            return invalid.error();
        }
        background(() -> {
            AiPlatformConfig created = savePlatform.execute(
                    new SavePlatform.Command(null, templateId, name, apiKey, baseUrl, null));
            events.postValue(AiSettingsEvent.openPlatform(created.id()));
        });
        return null;
    }

    /**
     * @return 系统提示词超长时返回错误且不保存
     */
    @Nullable
    public AiValidationError setSystemPrompt(@NonNull String prompt) {
        String value = prompt.strip();
        if (value.length() > AiChatPreferences.MAX_SYSTEM_PROMPT_LENGTH) {
            return AiValidationError.PROMPT_TOO_LONG;
        }
        update(preferences -> preferences.withSystemPrompt(value));
        return null;
    }

    public void restoreSystemPrompt() {
        update(preferences -> preferences.withSystemPrompt(AiChatPreferences.DEFAULT_SYSTEM_PROMPT));
    }

    /** 空表示沿用平台默认；界面给的值超出范围时收拢到 0 到 2 之间，不让错误数据进到仓库。 */
    public void setTemperature(@Nullable Double temperature) {
        Double value = temperature == null || temperature.isNaN()
                ? null
                : Math.max(0.0, Math.min(2.0, temperature));
        update(preferences -> preferences.withTemperature(value));
    }

    /** 空或不是正数表示沿用平台默认。 */
    public void setMaxOutputTokens(@Nullable Integer maxOutputTokens) {
        Integer value = maxOutputTokens == null || maxOutputTokens < 1 ? null : maxOutputTokens;
        update(preferences -> preferences.withMaxOutputTokens(value));
    }

    /** 0 表示带上全部历史。 */
    public void setContextMessages(int contextMessages) {
        int value = Math.max(0, contextMessages);
        update(preferences -> preferences.withContextMessages(value));
    }

    public void setShowReasoning(boolean show) {
        update(preferences -> preferences.withShowReasoning(show));
    }

    public void clearHistory() {
        session.clearAll();
        events.postValue(AiSettingsEvent.toast(R.string.ai_pref_cleared));
    }

    private void update(UnaryOperator<AiChatPreferences> change) {
        background(() -> savePreferences.update(change));
    }

    /** 后台执行写入；输入被拒绝或存储失败时给一句提示，而不是让线程带着异常退出。 */
    private void background(Runnable task) {
        io.execute(() -> {
            try {
                task.run();
            } catch (AiValidationException rejected) {
                events.postValue(AiSettingsEvent.toast(AiMessages.validation(rejected.error())));
            } catch (AiFailure failure) {
                events.postValue(AiSettingsEvent.toast(AiMessages.failure(failure.kind())));
            }
        });
    }

    private void publish(AiSettings settings) {
        uiState.postValue(mapper.map(settings, templates));
    }

    /** 读不出已保存的设置时，先按空设置显示出页面，并说明原因；之后写入成功了页面会跟着更新。 */
    private void onLoadFailed(AiFailure failure) {
        publish(AiSettings.EMPTY);
        events.postValue(AiSettingsEvent.toast(AiMessages.failure(failure.kind())));
    }

    @Override
    protected void onCleared() {
        feed.close();
    }
}
