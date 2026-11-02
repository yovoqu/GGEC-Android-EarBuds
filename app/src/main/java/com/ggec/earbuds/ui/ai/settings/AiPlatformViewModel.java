package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestListener;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.FetchPlatformModels;
import com.ggec.earbuds.domain.ai.usecase.AddManualModel;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.RemoveModel;
import com.ggec.earbuds.domain.ai.usecase.RemovePlatform;
import com.ggec.earbuds.domain.ai.usecase.RunSpeedTest;
import com.ggec.earbuds.domain.ai.usecase.SavePlatform;
import com.ggec.earbuds.domain.ai.usecase.SelectModel;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 平台详情页的 ViewModel（MVVM 的 VM）。
 *
 * <p>职责边界：这里是<b>唯一</b>可变状态的地方。它把用例的结果压成
 * {@link AiPlatformUiState}，页面只订阅、只画。页面不持有任何业务判断，
 * 因此"通不通""哪个快""能不能点"这类规则不会漏到 View 里。</p>
 *
 * <p>测速的生命周期由这里持有（不是在页面里）：离开页面再回来、转屏，
 * 结果都在；已测完的写回配置，未测完的随取消结束。</p>
 */
public final class AiPlatformViewModel extends ViewModel {

    private final LoadAiSettings loadSettings;
    private final ObserveAiSettings observeSettings;
    private final LoadPlatformTemplates loadTemplates;
    private final SavePlatform savePlatform;
    private final FetchPlatformModels fetchModels;
    private final AddManualModel addManualModel;
    private final RemoveModel removeModel;
    private final SelectModel selectModel;
    private final RunSpeedTest runSpeedTest;
    private final RemovePlatform removePlatform;
    private final Executor io;
    private final AiPlatformUiMapper mapper;

    private final MutableLiveData<AiPlatformUiState> state =
            new MutableLiveData<>(AiPlatformUiState.empty());
    private final OneShotLiveData<AiPlatformEvent> events = new OneShotLiveData<>();
    private final PlatformSpeedTestState speed = new PlatformSpeedTestState();

    @Nullable
    private String platformId;
    @Nullable
    private String creatingTemplateId;
    private AiSettings settings = AiSettings.EMPTY;
    private List<AiPlatformTemplate> templates = List.of();
    private boolean advancedExpanded;
    /** 搜索词：只影响"哪些行可见"，不进存储、不发网络。 */
    private volatile String searchQuery = "";
    private volatile long platformGeneration;
    @Nullable
    private Subscription subscription;
    private boolean started;

    public AiPlatformViewModel(@NonNull LoadAiSettings loadSettings,
                               @NonNull ObserveAiSettings observeSettings,
                               @NonNull LoadPlatformTemplates loadTemplates,
                               @NonNull SavePlatform savePlatform,
                               @NonNull FetchPlatformModels fetchPlatformModels,
                               @NonNull AddManualModel addManualModel,
                               @NonNull RemoveModel removeModel,
                               @NonNull SelectModel selectModel,
                               @NonNull RunSpeedTest runSpeedTest,
                               @NonNull RemovePlatform removePlatform,
                               @NonNull Executor io,
                               @NonNull AiPlatformUiMapper mapper) {
        this.loadSettings = Objects.requireNonNull(loadSettings, "loadSettings");
        this.observeSettings = Objects.requireNonNull(observeSettings, "observeSettings");
        this.loadTemplates = Objects.requireNonNull(loadTemplates, "loadTemplates");
        this.savePlatform = Objects.requireNonNull(savePlatform, "savePlatform");
        this.fetchModels = Objects.requireNonNull(fetchPlatformModels, "fetchPlatformModels");
        this.addManualModel = Objects.requireNonNull(addManualModel, "addManualModel");
        this.removeModel = Objects.requireNonNull(removeModel, "removeModel");
        this.selectModel = Objects.requireNonNull(selectModel, "selectModel");
        this.runSpeedTest = Objects.requireNonNull(runSpeedTest, "runSpeedTest");
        this.removePlatform = Objects.requireNonNull(removePlatform, "removePlatform");
        this.io = Objects.requireNonNull(io, "io");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @NonNull
    public LiveData<AiPlatformUiState> state() {
        return state;
    }

    @NonNull
    public LiveData<AiPlatformEvent> events() {
        return events;
    }

    /** 绑定一个已有平台。 */
    public void open(@NonNull String platformId) {
        platformGeneration++;
        this.platformId = platformId;
        this.creatingTemplateId = null;
        start();
    }

    /** 新建：还没落盘，只有模板与一份空配置。 */
    public void create(@NonNull String templateId) {
        platformGeneration++;
        this.creatingTemplateId = templateId;
        this.platformId = null;
        start();
    }

    private void start() {
        if (started) {
            publish();
            return;
        }
        started = true;
        io.execute(() -> loadInitialState());
    }

    private void loadInitialState() {
        try {
            settings = loadSettings.executeAsync().join();
            templates = loadTemplates.execute();
            publish();
            subscription = observeSettings.execute(next -> {
                settings = next;
                publish();
            });
        } catch (RuntimeException unreadable) {
            events.postValue(AiPlatformEvent.toast(
                    com.ggec.earbuds.R.string.ai_failure_storage));
        }
    }

    private void publish() {
        AiPlatformConfig platform = platform();
        if (platform == null && creatingTemplateId == null) {
            if (started) {
                publishState(AiPlatformUiState.empty());
            }
            return;
        }
        AiPlatformUiState next = mapper.map(settings, platform, template(),
                speed.testing(), speed.running(), advancedExpanded, searchQuery);
        publishState(next);
    }

    private void publishState(@NonNull AiPlatformUiState next) {
        if (Objects.equals(state.getValue(), next)) {
            return;
        }
        state.postValue(next);
    }

    @Nullable
    private AiPlatformConfig platform() {
        String id = platformId;
        return id == null ? null : settings.platform(id).orElse(null);
    }

    @Nullable
    private AiPlatformTemplate template() {
        String templateId = creatingTemplateId;
        if (templateId == null) {
            AiPlatformConfig platform = platform();
            templateId = platform == null ? null : platform.templateId();
        }
        if (templateId == null) {
            return null;
        }
        for (AiPlatformTemplate candidate : templates) {
            if (candidate.id().equals(templateId)) {
                return candidate;
            }
        }
        return null;
    }

    // ---- 连接 ----

    /** 测试连接 = 只测当前选中模型，等价于一次真实的最小请求。 */
    @androidx.annotation.Nullable
    public void testConnection() {
        AiPlatformConfig platform = platform();
        if (platform == null || speed.running()) {
            return;
        }
        String selected = AiPlatformPolicy.selectedModelOf(settings, platform);
        if (selected == null) {
            events.postValue(AiPlatformEvent.toast(AiMessages.validation(
                    com.ggec.earbuds.domain.ai.model.AiValidationError.MODEL_UNKNOWN)));
            return;
        }
        runSpeed(List.of(selected));
    }

    // ---- 模型 ----

    public void selectModel(@NonNull String modelId) {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        io.execute(() -> {
            selectModel.execute(platform.id(), modelId);
            publish();
        });
    }

    public void testModel(@NonNull String modelId) {
        runSpeed(List.of(modelId));
    }

    /**
     * 搜索词。过滤是纯展示逻辑（匹配名称或 id），不重读磁盘、不发请求；
     * 具体匹配规则在 {@link AiPlatformUiMapper#filter} 里，可单测。
     */
    public void setSearchQuery(@NonNull String query) {
        String next = AiPlatformPolicy.normalizeSearch(query);
        if (next.equals(searchQuery)) {
            return;
        }
        searchQuery = next;
        publish();
    }

    public void runAllSpeed() {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        // 只测能用于文本对话的模型：实时模型不在这条链路上，测了也没有意义。
        List<String> ids = AiPlatformPolicy.chatModelIds(platform);
        if (ids.isEmpty()) {
            events.postValue(AiPlatformEvent.toast(com.ggec.earbuds.R.string.ai_settings_models_empty));
            return;
        }
        runSpeed(ids);
    }

    public void toggleAllSpeed() {
        if (speed.running()) {
            cancelSpeed();
        } else {
            runAllSpeed();
        }
    }

    public void cancelSpeed() {
        AiCancellable running = speed.detach();
        speed.invalidate();
        if (running != null) {
            running.cancel();
        }
        publish();
    }

    private void runSpeed(@NonNull List<String> modelIds) {
        AiPlatformConfig platform = platform();
        if (platform == null || speed.running()) {
            return;
        }
        speed.begin(modelIds);
        publish();
        try {
            long generation = speed.generation();
            speed.attach(runSpeedTest.execute(platform.id(), modelIds, new Listener(generation)));
        } catch (AiFailure failure) {
            fail(failure);
        } catch (AiValidationException invalid) {
            speed.finish();
            publish();
            events.postValue(AiPlatformEvent.toast(AiMessages.validation(invalid.error())));
        }
    }

    private void fail(@NonNull AiFailure failure) {
        speed.finish();
        publish();
        events.postValue(AiPlatformEvent.toast(AiMessages.failure(failure.kind())));
    }

    public void fetchModels() {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        events.postValue(AiPlatformEvent.toast(com.ggec.earbuds.R.string.ai_settings_fetching));
        long generation = platformGeneration;
        io.execute(() -> {
            try {
                fetchModels.execute(platform.id());
            } catch (AiFailure failure) {
                events.postValue(AiPlatformEvent.toast(AiMessages.failure(failure.kind())));
            }
            if (generation == platformGeneration) {
                publish();
            }
        });
    }

    /** 手动添加模型：输入校验和持久化仍由领域用例完成。 */
    public void addManualModel(@NonNull String rawId) {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        long generation = platformGeneration;
        io.execute(() -> {
            try {
                addManualModel.execute(platform.id(), rawId);
            } catch (AiValidationException invalid) {
                events.postValue(AiPlatformEvent.toast(AiMessages.validation(invalid.error())));
            }
            if (generation == platformGeneration) {
                publish();
            }
        });
    }

    public void deleteModel(@NonNull String modelId) {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        long generation = platformGeneration;
        io.execute(() -> {
            removeModel.execute(platform.id(), modelId);
            if (generation == platformGeneration) {
                publish();
            }
        });
    }

    // ---- 高级 / 危险区 ----

    public void toggleAdvanced() {
        advancedExpanded = !advancedExpanded;
        publish();
    }

    public void deletePlatform() {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        platformGeneration++;
        long generation = platformGeneration;
        io.execute(() -> {
            speed.invalidate();
            removePlatform.execute(platform.id());
            if (generation == platformGeneration) {
                events.postValue(AiPlatformEvent.closed(
                        com.ggec.earbuds.R.string.ai_settings_platform_removed));
            }
        });
    }

    @Override
    protected void onCleared() {
        Subscription current = subscription;
        subscription = null;
        if (current != null) {
            current.cancel();
        }
        cancelSpeed();
    }

    private final class Listener implements AiSpeedTestListener {
        private final long generation;

        Listener(long generation) {
            this.generation = generation;
        }
        @Override
        public void onStarted(@NonNull String modelId) {
            // 已提前置为 TESTING，无需再刷新。
        }

        @Override
        public void onResult(@NonNull AiSpeedResult result) {
            if (generation != speed.generation()) {
                return;
            }
            speed.result(result.modelId());
            publish();
        }

        @Override
        public void onComplete(boolean cancelled) {
            if (generation != speed.generation()) {
                return;
            }
            speed.finish();
            publish();
            if (cancelled) {
                events.postValue(AiPlatformEvent.toast(com.ggec.earbuds.R.string.ai_settings_speed_cancelled));
            }
        }
    }
}
