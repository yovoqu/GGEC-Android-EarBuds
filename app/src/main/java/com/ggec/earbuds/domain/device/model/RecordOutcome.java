package com.ggec.earbuds.domain.device.model;

import com.ggec.earbuds.domain.device.record.RecordFailure;

import java.util.Objects;

/**
 * 一次录音类调用的终态：成功，或带分类的失败。
 *
 * <p>取代原来的 {@code Consumer<Boolean>} 回调：成功与否之外还能说清「为什么没成」，
 * 且不把任何自由文本带出 provider。分类本身单点在
 * {@link RecordFailure}（录音特性自己的那一份）。</p>
 */
public sealed interface RecordOutcome {

    /** 调用被设备接受并完成。 */
    record Ok() implements RecordOutcome {
    }

    /** 调用没有完成，{@code failure} 是分类。 */
    record Failed(RecordFailure failure) implements RecordOutcome {

        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    static RecordOutcome ok() {
        return new Ok();
    }

    static RecordOutcome failed(RecordFailure failure) {
        return new Failed(failure);
    }
}
