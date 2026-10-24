package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 一条消息里的一段内容。为什么要有这一层：多模态请求（文本 + 图片 + 音频）在三种协议里的写法完全不同，
 * 但"一条消息由几段内容组成"这件事是共同的，所以领域按段描述，翻译成方言是 lib_ai 的事。
 *
 * <p>本轮只有 {@link Text} 会被真正创建：{@link Image} 是给"通话中插入照片"预留的类型，
 * 目前没有任何代码路径会产生它；{@link Audio} 只出现在通话链路的评审代码里。
 * 没有创建者不代表可以先不留——类型缺席时，后面的多模态改造要动消息模型本身。</p>
 */
public sealed interface AiContentPart {

    /** 纯文本。绝大多数消息只有这一段。 */
    record Text(@NonNull String text) implements AiContentPart {
        public Text {
            text = text == null ? "" : text;
        }
    }

    /**
     * 图片。{@code ref} 只放引用（本地文件路径或已上传后的 URL），不放字节：
     * 领域对象会被整份写进历史文档，把图片塞进去会让一次对话变成几十兆。
     */
    record Image(@NonNull String ref, @Nullable String mimeType) implements AiContentPart {
    }

    /** 音频。同理只放引用与时长，不放采样数据。 */
    record Audio(@NonNull String ref, int durationMillis) implements AiContentPart {
    }

    /** 把若干段折成纯文本：老接口（只认字符串）和统计都靠它，缺图缺音频时也不会丢文字。 */
    @NonNull
    static String flatten(@NonNull List<AiContentPart> parts) {
        StringBuilder text = new StringBuilder();
        for (AiContentPart part : parts) {
            if (part instanceof Text plain) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(plain.text());
            }
        }
        return text.toString();
    }
}
