package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiProtocol;

import java.util.List;

/**
 * 平台详情页的界面状态：连接信息、模型列表、拉取和测速的进度。
 * 模型列表已经按搜索词过滤、按排序方式排好，页面照着显示即可。
 */
public record AiPlatformUiState(
        boolean loaded,
        boolean missing,
        String platformId,
        String name,
        boolean custom,
        boolean configured,
        boolean active,
        String maskedKey,
        String baseUrl,
        boolean defaultBaseUrl,
        AiProtocol protocol,
        List<AiProtocol> protocols,
        @Nullable String consoleUrl,
        Models models) {

    public AiPlatformUiState {
        protocols = List.copyOf(protocols);
    }

    public static AiPlatformUiState loading(String platformId) {
        return placeholder(platformId, false);
    }

    public static AiPlatformUiState missing(String platformId) {
        return placeholder(platformId, true);
    }

    /** 加载中还没有数据；平台不存在时数据已经"加载完"，只是什么都没有。 */
    private static AiPlatformUiState placeholder(String platformId, boolean missing) {
        return new AiPlatformUiState(missing, missing, platformId, "", false, false, false,
                "", "", true, AiProtocol.CHAT_COMPLETIONS, List.of(), null, Models.EMPTY);
    }

    public record Models(
            List<ModelRow> rows,
            int totalCount,
            boolean searchable,
            boolean hasSpeedResults,
            Fetch fetch,
            long fetchedAtMillis,
            SpeedRun speed,
            @Nullable Fastest fastest) {

        public static final Models EMPTY = new Models(List.of(), 0, false, false,
                Fetch.IDLE, 0L, SpeedRun.IDLE, null);

        public Models {
            rows = List.copyOf(rows);
        }
    }

    /**
     * @param contextWindow 0 表示平台没有提供
     */
    public record ModelRow(
            String id,
            String name,
            int contextWindow,
            boolean reasoning,
            boolean vision,
            boolean manual,
            boolean selected,
            Speed speed) {
    }

    public record Speed(State state, long firstTokenMillis, @Nullable AiFailureKind failureKind) {

        public enum State {
            NONE,
            QUEUED,
            RUNNING,
            OK,
            FAILED,
            TIMEOUT
        }

        public static final Speed NONE = new Speed(State.NONE, -1L, null);
        public static final Speed QUEUED = new Speed(State.QUEUED, -1L, null);
        public static final Speed RUNNING = new Speed(State.RUNNING, -1L, null);
    }

    public record Fetch(Status status, @Nullable AiFailureKind failureKind, String detail) {

        public enum Status {
            IDLE,
            RUNNING,
            FAILED
        }

        public static final Fetch IDLE = new Fetch(Status.IDLE, null, "");
        public static final Fetch RUNNING = new Fetch(Status.RUNNING, null, "");

        public static Fetch failed(AiFailureKind kind, String detail) {
            return new Fetch(Status.FAILED, kind, detail == null ? "" : detail);
        }
    }

    public record SpeedRun(boolean active, int done, int total) {
        public static final SpeedRun IDLE = new SpeedRun(false, 0, 0);
    }

    /** 测速里最快的模型，页面据此显示"使用最快"。已经选中它或正在测速时为空。 */
    public record Fastest(String modelId, String modelName, long firstTokenMillis, long totalMillis) {
    }
}
