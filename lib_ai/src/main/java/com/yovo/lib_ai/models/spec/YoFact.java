package com.yovo.lib_ai.models.spec;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 一条"支持不支持"的事实：结论加出处。界面显示"待确认"、路由分档，都读这一个值。
 *
 * @param support 结论
 * @param source  结论是谁给的；结论为 {@link YoSupport#UNKNOWN} 时没有意义
 */
public record YoFact(@NonNull YoSupport support, @NonNull YoSpecSource source) {

    /** 没人说过。 */
    public static final YoFact UNKNOWN = new YoFact(YoSupport.UNKNOWN, YoSpecSource.INFERRED);

    public YoFact {
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(source, "source");
    }

    public boolean yes() {
        return support == YoSupport.YES;
    }

    public boolean no() {
        return support == YoSupport.NO;
    }

    /** 有人明确表过态（支持或不支持）。 */
    public boolean known() {
        return support != YoSupport.UNKNOWN;
    }

    /** 支持，并且出处可信：界面可以直接断言，路由可以放心用。 */
    public boolean confirmed() {
        return yes() && source.trusted();
    }

    /** 只是名字上看起来支持，还没有人确认过。 */
    public boolean unconfirmed() {
        return yes() && !source.trusted();
    }
}
