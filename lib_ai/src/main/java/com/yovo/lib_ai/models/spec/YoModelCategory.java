package com.yovo.lib_ai.models.spec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 模型属于哪一类。类别决定它走哪种服务、典型地收什么产出什么，是路由的第一道筛。
 *
 * <p>三类<b>对话模型</b>共用 Chat Completions / Messages / Responses 三条协议，按输入输出的模态逐级放宽：
 * {@link #TEXT} ⊂ {@link #VISION} ⊂ {@link #OMNI}（见 {@link #canServe}）。其余类别各自有专用服务，
 * 彼此不能互相顶替。</p>
 *
 * <p>类别回答"它是哪种东西"，具体收哪些模态、有哪些服务端能力、上下文多长，是同一份规格里的另外几根轴，
 * 不再塞进类别里。一个"全模态"模型是 {@link #OMNI} 加上一组实际生效的模态，而不是一个特殊的能力位。</p>
 */
public enum YoModelCategory {

    /** 文本：文字进、文字出。 */
    TEXT("text", "文本", true,
            EnumSet.of(YoModality.TEXT), EnumSet.of(YoModality.TEXT)),
    /** 视觉：在文本之外还能看图（或视频），产出仍是文字。 */
    VISION("vision", "视觉", true,
            EnumSet.of(YoModality.TEXT, YoModality.IMAGE), EnumSet.of(YoModality.TEXT)),
    /** 全模态：文本、图片、音频、视频都能收，产出文字也能产出语音。仍是一问一答，不是全双工。 */
    OMNI("omni", "全模态", true,
            EnumSet.of(YoModality.TEXT, YoModality.IMAGE, YoModality.AUDIO, YoModality.VIDEO),
            EnumSet.of(YoModality.TEXT, YoModality.AUDIO)),

    /** 语音识别：音频进、文字出。 */
    SPEECH_RECOGNITION("speech_recognition", "语音识别", false,
            EnumSet.of(YoModality.AUDIO), EnumSet.of(YoModality.TEXT)),
    /** 语音合成：文字进、音频出。 */
    SPEECH_SYNTHESIS("speech_synthesis", "语音合成", false,
            EnumSet.of(YoModality.TEXT), EnumSet.of(YoModality.AUDIO)),
    /** 向量：把内容映射成向量。 */
    EMBEDDING("embedding", "向量", false,
            EnumSet.of(YoModality.TEXT), EnumSet.of(YoModality.VECTOR)),
    /** 实时：一条 WebSocket 同时上下行音频，服务端自己断句，是全双工而不是一问一答。 */
    REALTIME("realtime", "实时", false,
            EnumSet.of(YoModality.TEXT, YoModality.AUDIO), EnumSet.of(YoModality.TEXT, YoModality.AUDIO)),

    /** 图像生成。SDK 暂时没有对应服务，只做识别，免得它混进对话模型列表。 */
    IMAGE_GENERATION("image_generation", "图像生成", false,
            EnumSet.of(YoModality.TEXT), EnumSet.of(YoModality.IMAGE)),
    /** 视频生成。同上，只做识别。 */
    VIDEO_GENERATION("video_generation", "视频生成", false,
            EnumSet.of(YoModality.TEXT), EnumSet.of(YoModality.VIDEO)),
    /** 重排序：给一批候选打相关性分数，产物是分数而不是某种模态。同上，只做识别。 */
    RERANK("rerank", "重排序", false,
            EnumSet.of(YoModality.TEXT), EnumSet.noneOf(YoModality.class)),
    /** 内容审核：产物是判定而不是某种模态。同上，只做识别。 */
    MODERATION("moderation", "内容审核", false,
            EnumSet.of(YoModality.TEXT, YoModality.IMAGE), EnumSet.noneOf(YoModality.class)),
    /** 认得出不是对话模型，但说不清是哪一类（旧的文本补全模型等）。 */
    OTHER("other", "其他", false,
            EnumSet.noneOf(YoModality.class), EnumSet.noneOf(YoModality.class));

    private final String id;
    private final String label;
    private final boolean conversational;
    private final Set<YoModality> typicalInputs;
    private final Set<YoModality> typicalOutputs;

    YoModelCategory(String id, String label, boolean conversational,
                    EnumSet<YoModality> typicalInputs, EnumSet<YoModality> typicalOutputs) {
        this.id = id;
        this.label = label;
        this.conversational = conversational;
        this.typicalInputs = Collections.unmodifiableSet(typicalInputs);
        this.typicalOutputs = Collections.unmodifiableSet(typicalOutputs);
    }

    /**
     * 稳定的持久化标识，不随枚举名重命名而变。
     */
    @NonNull
    public String id() {
        return id;
    }

    @Nullable
    public static YoModelCategory fromId(@Nullable String id) {
        for (YoModelCategory category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return null;
    }

    /**
     * 拼错误提示和界面标签用的人话名。
     */
    @NonNull
    public String label() {
        return label;
    }

    /**
     * 走三条对话协议（文本、视觉、全模态）。其余类别各有专用服务，或者 SDK 暂时没有服务。
     */
    public boolean conversational() {
        return conversational;
    }

    /**
     * 语音识别与语音合成统称"语音"。它们的输入输出方向相反、服务端点也不同，所以是两个类别。
     */
    public boolean speech() {
        return this == SPEECH_RECOGNITION || this == SPEECH_SYNTHESIS;
    }

    /**
     * 这一类典型收的模态。规格里没有人明确表态时，拿它当"推断级"的底，不是事实。
     */
    @NonNull
    public Set<YoModality> typicalInputs() {
        return typicalInputs;
    }

    /**
     * 这一类典型产出的模态。含义同 {@link #typicalInputs()}。
     */
    @NonNull
    public Set<YoModality> typicalOutputs() {
        return typicalOutputs;
    }

    /**
     * 这个类别的模型能不能当作 {@code required} 来用。
     *
     * <p>对话三类向下兼容：视觉模型当然能做纯文本对话，全模态模型也能看图。
     * 反过来不行。非对话类别只认自己。</p>
     */
    public boolean canServe(@NonNull YoModelCategory required) {
        if (this == required) {
            return true;
        }
        return switch (this) {
            case VISION -> required == TEXT;
            case OMNI -> required == TEXT || required == VISION;
            default -> false;
        };
    }
}
