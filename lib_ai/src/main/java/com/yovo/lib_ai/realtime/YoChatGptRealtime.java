package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.models.YoModelInfo;
import com.yovo.lib_ai.models.YoModelRequirement;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatGPT Realtime 能交给 APP 的能力，不是 APP 的选择。
 *
 * <p>这里只回答三件事：官方主机是哪一台、这条产品线能说哪几种方言、
 * 一份模型清单里哪些模型接得上实时会话。用哪台地址、选哪一个模型、用哪一种方言，
 * 由 APP 决定并展示。</p>
 */
public final class YoChatGptRealtime {

    /** 官方 OpenAI 主机。APP 可以改成兼容网关，不改时用这个。 */
    public static final String OFFICIAL_BASE_URL = "https://api.openai.com";

    private static final List<YoRealtimeDialect> DIALECTS = List.of(
            YoRealtimeDialect.OPENAI_BETA,
            YoRealtimeDialect.OPENAI_GA);

    private YoChatGptRealtime() {
    }

    @NonNull
    public static String officialBaseUrl() {
        return OFFICIAL_BASE_URL;
    }

    /**
     * ChatGPT 这条线能说的方言。通义不在里面：那是另一家产品的线路。
     * 顺序就是建议的展示顺序，第一项是默认。
     */
    @NonNull
    public static List<YoRealtimeDialect> dialects() {
        return DIALECTS;
    }

    @NonNull
    public static YoRealtimeDialect defaultDialect() {
        return YoRealtimeDialect.OPENAI_BETA;
    }

    /**
     * 这份清单里接得上 ChatGPT Realtime 的模型。文本、向量、图片会被拿掉。
     * 名字里带 {@code realtime}、平台没另报类别时，按推断也算能接——否则官方
     * {@code /v1/models} 只给 id 的实时模型会从列表里消失。
     */
    @NonNull
    public static List<YoModelInfo> offered(@NonNull List<YoModelInfo> models) {
        YoModelRequirement requirement = YoModelRequirement.realtime();
        List<YoModelInfo> offered = new ArrayList<>();
        for (YoModelInfo model : models) {
            if (model != null && requirement.fit(model.spec()).accepted()) {
                offered.add(model);
            }
        }
        return List.copyOf(offered);
    }
}
