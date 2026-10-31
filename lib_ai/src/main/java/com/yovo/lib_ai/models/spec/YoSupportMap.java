package com.yovo.lib_ai.models.spec;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 一组枚举项的"支持不支持"账本：每一项一条 {@link YoFact}，没记过的就是 {@link YoSupport#UNKNOWN}。
 * 输入模态、输出模态、服务端能力共用这一个实现，出处规则只写一次。
 *
 * <p><b>覆盖规则</b>：{@link #with} 按出处优先级决定新说法能不能改旧结论——
 * 平台上报盖过推断，用户盖过平台，低出处的话改不动高出处的结论；同一级里后写的为准。
 * 所以几层来源按"推断 → 上报 → 用户"的顺序依次写进来，结果与写入顺序无关地稳定。</p>
 *
 * <p>不可变：每次写入返回新账本。</p>
 */
public final class YoSupportMap<K extends Enum<K>> {

    private final Class<K> type;
    private final EnumMap<K, YoFact> facts;

    private YoSupportMap(Class<K> type, EnumMap<K, YoFact> facts) {
        this.type = type;
        this.facts = facts;
    }

    @NonNull
    public static <K extends Enum<K>> YoSupportMap<K> empty(@NonNull Class<K> type) {
        return new YoSupportMap<>(Objects.requireNonNull(type, "type"), new EnumMap<>(type));
    }

    /**
     * 这一项的事实；没人说过返回 {@link YoFact#UNKNOWN}。
     */
    @NonNull
    public YoFact fact(@NonNull K key) {
        YoFact fact = facts.get(key);
        return fact == null ? YoFact.UNKNOWN : fact;
    }

    /** 明确支持（不论出处）。 */
    public boolean has(@NonNull K key) {
        return fact(key).yes();
    }

    /** 明确支持，并且出处可信。 */
    public boolean confirmed(@NonNull K key) {
        return fact(key).confirmed();
    }

    /** 全部明确支持的项，不可变。 */
    @NonNull
    public Set<K> yes() {
        EnumSet<K> set = EnumSet.noneOf(type);
        for (Map.Entry<K, YoFact> entry : facts.entrySet()) {
            if (entry.getValue().yes()) {
                set.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(set);
    }

    /** 明确支持且出处可信的项，不可变。 */
    @NonNull
    public Set<K> confirmedYes() {
        EnumSet<K> set = EnumSet.noneOf(type);
        for (Map.Entry<K, YoFact> entry : facts.entrySet()) {
            if (entry.getValue().confirmed()) {
                set.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(set);
    }

    /** 一条都没记。 */
    public boolean isEmpty() {
        return facts.isEmpty();
    }

    /**
     * 写一条说法。{@link YoSupport#UNKNOWN} 等于没说，不改任何东西；
     * 出处低于已有结论的出处时同样不生效。
     */
    @NonNull
    public YoSupportMap<K> with(@NonNull K key, @NonNull YoSupport support, @NonNull YoSpecSource source) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(source, "source");
        if (support == YoSupport.UNKNOWN) {
            return this;
        }
        YoFact current = facts.get(key);
        if (current != null && !source.overrides(current.source())) {
            return this;
        }
        EnumMap<K, YoFact> next = new EnumMap<>(facts);
        next.put(key, new YoFact(support, source));
        return new YoSupportMap<>(type, next);
    }

    /**
     * 写一份"完整清单"：列出的全是支持，枚举里其余的项全是不支持。
     * 只用于语义上就是穷举的字段，例如平台的 {@code input_modalities}——它列的就是全部可接受的输入。
     * 开放式的能力清单（"支持的特性"）不要用这个，漏列不代表不支持。
     */
    @NonNull
    public YoSupportMap<K> withExhaustive(@NonNull Collection<K> listed, @NonNull YoSpecSource source) {
        YoSupportMap<K> next = this;
        for (K key : type.getEnumConstants()) {
            next = next.with(key, YoSupport.of(listed.contains(key)), source);
        }
        return next;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof YoSupportMap<?> map && type == map.type && facts.equals(map.facts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, facts);
    }

    @NonNull
    @Override
    public String toString() {
        return facts.toString();
    }
}
