package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestListener;
import com.ggec.earbuds.domain.ai.usecase.SavePlatform;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.ggec.earbuds.ui.ai.common.AiSettingsFeed;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * 单个平台的详情页：改密钥和地址、拉取模型、测速、选模型。
 * <p>
 * 状态分两块：持久的设置来自仓库，临时的搜索词、拉取和测速进度由这里持有，两块合起来由
 * {@link AiPlatformUiMapper} 算出界面状态。所有可变状态都在 {@link #lock} 下改，
 * 并且在锁里发布，保证后发布的永远是更新的状态；用例（会读写磁盘、访问网络）一律在锁外执行。
 */
public final class AiPlatformViewModel extends ViewModel {

    /** 一次测速最多测多少个模型，避免对上百个模型的平台一口气发出上百个请求。 */
    public static final int MAX_SPEED_TEST_MODELS = 24;

    private final String platformId;
    private final AiPlatformServices services;
    private final AiPlatformUiMapper mapper;
    private final Executor io;
    private final List<AiPlatformTemplate> templates;
    private final AiSettingsFeed feed;

    private final MutableLiveData<AiPlatformUiState> uiState;
    private final OneShotLiveData<AiPlatformEvent> events = new OneShotLiveData<>();

    private final Object lock = new Object();
    private AiSettings settings = AiSettings.EMPTY;
    private boolean settingsKnown;
    private AiPlatformLocal local = AiPlatformLocal.initial();
    private boolean autoFetch = true;
    private AiCancellable speedHandle;
    private int speedGeneration;
    private boolean speedStopRequested;
    private boolean cleared;

    public AiPlatformViewModel(
            @NonNull String platformId,
            @NonNull AiPlatformServices services,
            @NonNull AiPlatformUiMapper mapper,
            @NonNull Executor io) {
        this.platformId = Objects.requireNonNull(platformId, "platformId");
        this.services = Objects.requireNonNull(services, "services");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.io = Objects.requireNonNull(io, "io");
        this.templates = services.loadTemplates().execute();
        this.uiState = new MutableLiveData<>(AiPlatformUiState.loading(platformId));
        this.feed = new AiSettingsFeed(
                services.loadSettings(), services.observeSettings(), io, this::onSettings, this::onLoadFailed);
    }

    @NonNull
    public LiveData<AiPlatformUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<AiPlatformEvent> events() {
        return events;
    }

    // ---- 连接信息 ----

    /**
     * 保存 API Key。第一次设置、或者换了新的密钥后，如果还没有模型就自动拉取一次，
     * 顺便验证密钥是否可用。
     *
     * @return 输入不合格时返回错误，对话框据此在输入框上提示；通过则为空
     */
    @Nullable
    public AiValidationError saveKey(@NonNull String raw) {
        String key;
        try {
            key = services.keyPolicy().normalize(raw);
        } catch (AiValidationException invalid) {
            return invalid.error();
        }
        Draft draft = draft();
        if (draft == null) {
            return AiValidationError.PLATFORM_UNKNOWN;
        }
        commit(draft.withKey(key), R.string.ai_key_saved);
        return null;
    }

    /**
     * @param raw 内置平台传空串表示恢复默认地址
     */
    @Nullable
    public AiValidationError saveBaseUrl(@NonNull String raw) {
        Draft draft = draft();
        if (draft == null) {
            return AiValidationError.PLATFORM_UNKNOWN;
        }
        if (draft.apiKey.isEmpty()) {
            return AiValidationError.API_KEY_EMPTY;
        }
        String baseUrl;
        try {
            baseUrl = services.urlPolicy().normalize(raw, draft.template.custom());
        } catch (AiValidationException invalid) {
            return invalid.error();
        }
        commit(draft.withBaseUrl(baseUrl), R.string.ai_saved);
        return null;
    }

    /** 只有自定义平台可以改名。 */
    @Nullable
    public AiValidationError saveName(@NonNull String raw) {
        Draft draft = draft();
        if (draft == null) {
            return AiValidationError.PLATFORM_UNKNOWN;
        }
        if (draft.apiKey.isEmpty()) {
            return AiValidationError.API_KEY_EMPTY;
        }
        String name = raw.strip();
        if (name.isEmpty()) {
            return AiValidationError.NAME_EMPTY;
        }
        commit(draft.withName(name), R.string.ai_saved);
        return null;
    }

    @Nullable
    public AiValidationError setProtocol(@NonNull AiProtocol protocol) {
        Draft draft = draft();
        if (draft == null) {
            return AiValidationError.PLATFORM_UNKNOWN;
        }
        if (draft.apiKey.isEmpty()) {
            return AiValidationError.API_KEY_EMPTY;
        }
        if (!draft.template.protocols().contains(protocol)) {
            return AiValidationError.PROTOCOL_UNSUPPORTED;
        }
        if (protocol != draft.protocol) {
            commit(draft.withProtocol(protocol), R.string.ai_saved);
        }
        return null;
    }

    public void removePlatform() {
        cancelSpeedTest();
        background(() -> {
            services.removePlatform().execute(platformId);
            events.postValue(AiPlatformEvent.finish());
        });
    }

    // ---- 模型列表 ----

    public void setQuery(@NonNull String query) {
        mutate(current -> current.withQuery(query));
    }

    public void setSortBySpeed(boolean sortBySpeed) {
        mutate(current -> current.withSortBySpeed(sortBySpeed));
    }

    public void fetchModels() {
        synchronized (lock) {
            if (cleared || local.fetch().status() == AiPlatformUiState.Fetch.Status.RUNNING) {
                return;
            }
            local = local.withFetch(AiPlatformUiState.Fetch.RUNNING);
            publishLocked();
        }
        io.execute(this::runFetch);
    }

    /**
     * @return 模型名不合格，或平台还没有设置密钥时返回错误；通过则为空
     */
    @Nullable
    public AiValidationError addModel(@NonNull String raw) {
        String modelId;
        try {
            modelId = services.modelIdPolicy().normalize(raw);
        } catch (AiValidationException invalid) {
            return invalid.error();
        }
        synchronized (lock) {
            Optional<AiPlatformConfig> platform = settings.platform(platformId);
            if (platform.isEmpty() || !platform.get().configured()) {
                return AiValidationError.PLATFORM_NOT_CONFIGURED;
            }
        }
        background(() -> services.addManualModel().execute(platformId, modelId));
        return null;
    }

    public void removeModel(@NonNull String modelId) {
        background(() -> services.removeModel().execute(platformId, modelId));
    }

    /** 把这个平台的这个模型设为对话使用的模型。 */
    public void selectModel(@NonNull String modelId) {
        String displayName;
        synchronized (lock) {
            displayName = settings.platform(platformId)
                    .flatMap(platform -> platform.model(modelId))
                    .map(AiModel::name)
                    .orElse(modelId);
        }
        background(() -> {
            services.selectModel().execute(platformId, modelId);
            events.postValue(AiPlatformEvent.toast(R.string.ai_model_switched, displayName));
        });
    }

    /** 选用最近一次测速里最快的模型。 */
    public void useFastest() {
        Optional<String> fastest;
        synchronized (lock) {
            fastest = mapper.fastestModelId(settings, platformId);
        }
        fastest.ifPresent(this::selectModel);
    }

    // ---- 测速 ----

    /**
     * 对当前列表（已按搜索词过滤、按排序方式排好）里的前 {@value #MAX_SPEED_TEST_MODELS} 个模型测速。
     */
    public void startSpeedTest() {
        List<String> targets;
        int generation;
        boolean capped;
        synchronized (lock) {
            if (cleared || local.run().active()) {
                return;
            }
            Optional<AiPlatformConfig> platform = settings.platform(platformId);
            if (platform.isEmpty() || !platform.get().configured()) {
                events.postValue(AiPlatformEvent.toast(R.string.ai_fetch_need_key));
                return;
            }
            List<String> visible = mapper.visibleModelIds(settings, platformId, local);
            if (visible.isEmpty()) {
                events.postValue(AiPlatformEvent.toast(R.string.ai_speed_need_models));
                return;
            }
            capped = visible.size() > MAX_SPEED_TEST_MODELS;
            targets = List.copyOf(visible.subList(0, Math.min(visible.size(), MAX_SPEED_TEST_MODELS)));
            generation = ++speedGeneration;
            speedStopRequested = false;
            speedHandle = null;
            local = local.withRun(AiPlatformLocal.Run.started(targets));
            publishLocked();
        }
        if (capped) {
            events.postValue(AiPlatformEvent.toast(R.string.ai_speed_capped, String.valueOf(MAX_SPEED_TEST_MODELS)));
        }
        List<String> run = targets;
        int token = generation;
        io.execute(() -> launchSpeedTest(token, run));
    }

    public void stopSpeedTest() {
        cancelSpeedTest();
    }

    // ---- 内部 ----

    private void onSettings(AiSettings next) {
        boolean fetchNow = false;
        synchronized (lock) {
            settings = next;
            settingsKnown = true;
            if (autoFetchDue()) {
                autoFetch = false;
                local = local.withFetch(AiPlatformUiState.Fetch.RUNNING);
                fetchNow = true;
            }
            publishLocked();
        }
        if (fetchNow) {
            io.execute(this::runFetch);
        }
    }

    /** 配置好了密钥、却从没拉取过模型：打开页面或保存密钥后自动拉一次。 */
    private boolean autoFetchDue() {
        if (!autoFetch || cleared || local.fetch().status() == AiPlatformUiState.Fetch.Status.RUNNING) {
            return false;
        }
        Optional<AiPlatformConfig> platform = settings.platform(platformId);
        return platform.isPresent()
                && platform.get().configured()
                && platform.get().models().isEmpty()
                && platform.get().modelsFetchedAtMillis() == 0L;
    }

    private void runFetch() {
        try {
            AiPlatformConfig updated = services.fetchModels().execute(platformId);
            setFetch(AiPlatformUiState.Fetch.IDLE);
            if (updated.models().isEmpty()) {
                events.postValue(AiPlatformEvent.toast(R.string.ai_fetch_empty));
            } else {
                events.postValue(AiPlatformEvent.toast(R.string.ai_fetch_done,
                        String.valueOf(updated.models().size())));
            }
        } catch (AiFailure failure) {
            setFetch(AiPlatformUiState.Fetch.failed(failure.kind(), failure.getMessage()));
        } catch (AiValidationException rejected) {
            setFetch(AiPlatformUiState.Fetch.IDLE);
            events.postValue(AiPlatformEvent.toast(AiMessages.validation(rejected.error())));
        } catch (RuntimeException unexpected) {
            setFetch(AiPlatformUiState.Fetch.failed(AiFailureKind.UNKNOWN, ""));
        }
    }

    private void setFetch(AiPlatformUiState.Fetch fetch) {
        mutate(current -> current.withFetch(fetch));
    }

    private void launchSpeedTest(int generation, List<String> targets) {
        AiCancellable handle;
        try {
            handle = services.runSpeedTest().execute(platformId, targets, new SpeedObserver(generation));
        } catch (AiValidationException rejected) {
            endSpeedTest(generation);
            events.postValue(AiPlatformEvent.toast(AiMessages.validation(rejected.error())));
            return;
        } catch (AiFailure failure) {
            endSpeedTest(generation);
            events.postValue(AiPlatformEvent.toast(AiMessages.failure(failure.kind())));
            return;
        }
        boolean stopNow;
        synchronized (lock) {
            if (generation != speedGeneration || !local.run().active()) {
                stopNow = true;
            } else {
                speedHandle = handle;
                stopNow = speedStopRequested;
            }
        }
        if (stopNow) {
            handle.cancel();
        }
    }

    private void cancelSpeedTest() {
        AiCancellable handle;
        synchronized (lock) {
            if (!local.run().active()) {
                return;
            }
            speedStopRequested = true;
            handle = speedHandle;
        }
        if (handle != null) {
            handle.cancel();
        }
    }

    private void endSpeedTest(int generation) {
        synchronized (lock) {
            if (generation == speedGeneration) {
                local = local.withRun(AiPlatformLocal.Run.IDLE);
                speedHandle = null;
                publishLocked();
            }
        }
    }

    private void commit(Draft draft, @StringRes int savedMessage) {
        synchronized (lock) {
            autoFetch = true;
        }
        background(() -> {
            services.savePlatform().execute(new SavePlatform.Command(
                    draft.exists ? platformId : null,
                    draft.template.id(),
                    draft.name,
                    draft.apiKey,
                    draft.baseUrl,
                    draft.protocol));
            events.postValue(AiPlatformEvent.toast(savedMessage));
        });
    }

    /** 后台执行写入；输入被拒绝或存储失败时给一句提示，而不是让线程带着异常退出。 */
    private void background(Runnable task) {
        io.execute(() -> {
            try {
                task.run();
            } catch (AiValidationException rejected) {
                events.postValue(AiPlatformEvent.toast(AiMessages.validation(rejected.error())));
            } catch (AiFailure failure) {
                events.postValue(AiPlatformEvent.toast(AiMessages.failure(failure.kind())));
            }
        });
    }

    /** 读不出已保存的设置时，先按空设置显示出页面，并说明原因。 */
    private void onLoadFailed(AiFailure failure) {
        onSettings(AiSettings.EMPTY);
        events.postValue(AiPlatformEvent.toast(AiMessages.failure(failure.kind())));
    }

    /** 取出当前已保存的连接信息，修改其中一项后整体提交；平台不存在时为空。 */
    @Nullable
    private Draft draft() {
        synchronized (lock) {
            Optional<AiPlatformConfig> stored = settings.platform(platformId);
            AiPlatformTemplate template = templateOf(stored);
            if (template == null || (stored.isEmpty() && template.custom())) {
                return null;
            }
            if (stored.isPresent()) {
                AiPlatformConfig config = stored.get();
                return new Draft(true, template, config.name(), config.apiKey(), config.baseUrl(), config.protocol());
            }
            return new Draft(false, template, template.name(), "", null, template.defaultProtocol());
        }
    }

    @Nullable
    private AiPlatformTemplate templateOf(Optional<AiPlatformConfig> stored) {
        String templateId = stored.map(AiPlatformConfig::templateId).orElse(platformId);
        for (AiPlatformTemplate template : templates) {
            if (template.id().equals(templateId)) {
                return template;
            }
        }
        return null;
    }

    private void mutate(UnaryOperator<AiPlatformLocal> change) {
        synchronized (lock) {
            local = change.apply(local);
            publishLocked();
        }
    }

    private void publishLocked() {
        if (settingsKnown) {
            uiState.postValue(mapper.map(settings, platformId, templates, local));
        }
    }

    @Override
    protected void onCleared() {
        AiCancellable handle;
        synchronized (lock) {
            cleared = true;
            speedGeneration++;
            handle = speedHandle;
            speedHandle = null;
        }
        feed.close();
        if (handle != null) {
            handle.cancel();
        }
    }

    /** 测速网关回调的接收者。{@code generation} 过期说明这次测速已被放弃，回调直接忽略。 */
    private final class SpeedObserver implements AiSpeedTestListener {

        private final int generation;

        SpeedObserver(int generation) {
            this.generation = generation;
        }

        @Override
        public void onStarted(String modelId) {
            synchronized (lock) {
                if (generation == speedGeneration) {
                    local = local.withRun(local.run().began(modelId));
                    publishLocked();
                }
            }
        }

        @Override
        public void onResult(AiSpeedResult result) {
            synchronized (lock) {
                if (generation == speedGeneration) {
                    local = local.withRun(local.run().finished(result.modelId()));
                    publishLocked();
                }
            }
        }

        @Override
        public void onComplete(boolean cancelled) {
            endSpeedTest(generation);
        }
    }

    /** 已保存连接信息的快照，每个 {@code with} 方法返回改了一项的新快照。 */
    private static final class Draft {
        final boolean exists;
        final AiPlatformTemplate template;
        final String name;
        final String apiKey;
        @Nullable
        final String baseUrl;
        final AiProtocol protocol;

        Draft(boolean exists, AiPlatformTemplate template, String name, String apiKey,
              @Nullable String baseUrl, AiProtocol protocol) {
            this.exists = exists;
            this.template = template;
            this.name = name;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.protocol = protocol;
        }

        Draft withKey(String key) {
            return new Draft(exists, template, name, key, baseUrl, protocol);
        }

        Draft withBaseUrl(@Nullable String url) {
            return new Draft(exists, template, name, apiKey, url, protocol);
        }

        Draft withName(String value) {
            return new Draft(exists, template, value, apiKey, baseUrl, protocol);
        }

        Draft withProtocol(AiProtocol value) {
            return new Draft(exists, template, name, apiKey, baseUrl, value);
        }
    }
}
