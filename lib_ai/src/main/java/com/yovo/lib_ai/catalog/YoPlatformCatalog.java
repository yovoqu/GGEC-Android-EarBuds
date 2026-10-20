package com.yovo.lib_ai.catalog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.platform.YoPlatform;
import com.yovo.lib_ai.platform.anthropic.AnthropicPlatform;
import com.yovo.lib_ai.platform.deepseek.DeepSeekPlatform;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;
import com.yovo.lib_ai.platform.sensenova.SenseNovaPlatform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * lib_ai 能创建的平台目录。调用方只靠模板标识和纯数据设置创建平台，不必认识具体的平台类，
 * 新增平台时这里加一行注册即可。
 */
public final class YoPlatformCatalog {

    public static final String OPENAI_COMPATIBLE = "openai-compatible";
    public static final String ANTHROPIC_COMPATIBLE = "anthropic-compatible";

    private static final YoPlatformCatalog STANDARD = standardCatalog();

    private final Map<String, Entry> entries;

    private YoPlatformCatalog(Map<String, Entry> entries) {
        this.entries = entries;
    }

    @NonNull
    public static YoPlatformCatalog standard() {
        return STANDARD;
    }

    @NonNull
    public List<YoPlatformDescriptor> descriptors() {
        List<YoPlatformDescriptor> descriptors = new ArrayList<>();
        for (Entry entry : entries.values()) {
            descriptors.add(entry.descriptor);
        }
        return List.copyOf(descriptors);
    }

    @Nullable
    public YoPlatformDescriptor find(@NonNull String templateId) {
        Entry entry = entries.get(templateId);
        return entry == null ? null : entry.descriptor;
    }

    /**
     * 按设置创建平台。模板未知、自定义模板缺少主机地址、地址不合法时抛 {@link IllegalArgumentException}。
     */
    @NonNull
    public YoPlatform create(@NonNull YoPlatformSettings settings) {
        Entry entry = entries.get(settings.templateId());
        if (entry == null) {
            throw new IllegalArgumentException("Unknown platform template: " + settings.templateId());
        }
        String baseUrl = settings.baseUrl();
        if (baseUrl == null && entry.descriptor.custom()) {
            throw new IllegalArgumentException("baseUrl is required for " + settings.templateId());
        }
        return entry.factory.apply(new Resolved(
                settings.instanceId(),
                settings.apiKey(),
                baseUrl == null ? entry.descriptor.defaultBaseUrl() : normalizeBaseUrl(baseUrl)));
    }

    /**
     * 把用户粘贴的地址整理成主机基址。协议路径由 lib_ai 追加，所以要去掉用户顺手带上的
     * {@code /v1} 和完整端点后缀，否则请求会变成 {@code /v1/v1/chat/completions}。
     */
    @NonNull
    public static String normalizeBaseUrl(@NonNull String raw) {
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        int schemeEnd;
        if (lower.startsWith("https://")) {
            schemeEnd = "https://".length();
        } else if (lower.startsWith("http://")) {
            schemeEnd = "http://".length();
        } else {
            throw new IllegalArgumentException("baseUrl must start with https:// or http://");
        }
        int cut = firstIndexOf(value, '?', '#');
        if (cut >= 0) {
            value = value.substring(0, cut);
        }
        value = trimSlashes(value, schemeEnd);
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            String lowered = value.toLowerCase(Locale.ROOT);
            for (String suffix : STRIPPED_SUFFIXES) {
                if (lowered.endsWith(suffix) && value.length() - suffix.length() > schemeEnd) {
                    value = trimSlashes(value.substring(0, value.length() - suffix.length()), schemeEnd);
                    stripped = true;
                    break;
                }
            }
        }
        String authority = value.substring(schemeEnd);
        int slash = authority.indexOf('/');
        String host = slash < 0 ? authority : authority.substring(0, slash);
        if (host.isBlank() || host.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("baseUrl must contain a host");
        }
        return value;
    }

    private static final String[] STRIPPED_SUFFIXES = {
            "/v1/chat/completions", "/chat/completions", "/v1/responses", "/responses",
            "/v1/messages", "/messages", "/v1/models", "/models", "/v1"
    };

    private static String trimSlashes(String value, int floor) {
        String result = value;
        while (result.length() > floor && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static int firstIndexOf(String value, char first, char second) {
        int a = value.indexOf(first);
        int b = value.indexOf(second);
        if (a < 0) {
            return b;
        }
        return b < 0 ? a : Math.min(a, b);
    }

    private static YoPlatformCatalog standardCatalog() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        register(entries, new YoPlatformDescriptor(
                OpenAiPlatform.ID, "OpenAI", "https://api.openai.com",
                List.of(YoProtocol.CHAT_COMPLETIONS, YoProtocol.RESPONSES), false, "sk-…",
                "https://platform.openai.com/api-keys"),
                r -> OpenAiPlatform.builder().id(r.instanceId).baseUrl(r.baseUrl).apiKey(r.apiKey).build());
        register(entries, new YoPlatformDescriptor(
                AnthropicPlatform.ID, "Anthropic", "https://api.anthropic.com",
                List.of(YoProtocol.MESSAGES), false, "sk-ant-…",
                "https://console.anthropic.com/settings/keys"),
                r -> AnthropicPlatform.builder().id(r.instanceId).baseUrl(r.baseUrl).apiKey(r.apiKey).build());
        register(entries, new YoPlatformDescriptor(
                DeepSeekPlatform.ID, "DeepSeek", "https://api.deepseek.com",
                List.of(YoProtocol.CHAT_COMPLETIONS, YoProtocol.MESSAGES), false, "sk-…",
                "https://platform.deepseek.com/api_keys"),
                r -> DeepSeekPlatform.builder().id(r.instanceId).baseUrl(r.baseUrl).apiKey(r.apiKey).build());
        register(entries, new YoPlatformDescriptor(
                SenseNovaPlatform.ID, "SenseNova", "https://token.sensenova.cn",
                List.of(YoProtocol.CHAT_COMPLETIONS, YoProtocol.RESPONSES, YoProtocol.MESSAGES), false, "sk-…",
                null),
                r -> SenseNovaPlatform.builder().id(r.instanceId).baseUrl(r.baseUrl).apiKey(r.apiKey).build());
        register(entries, new YoPlatformDescriptor(
                OPENAI_COMPATIBLE, "OpenAI Compatible", "",
                List.of(YoProtocol.CHAT_COMPLETIONS, YoProtocol.RESPONSES), true, "sk-…", null),
                r -> OpenAiPlatform.builder().id(r.instanceId).baseUrl(r.baseUrl).apiKey(r.apiKey).build());
        register(entries, new YoPlatformDescriptor(
                ANTHROPIC_COMPATIBLE, "Anthropic Compatible", "",
                List.of(YoProtocol.MESSAGES), true, "sk-…", null),
                r -> AnthropicPlatform.builder().id(r.instanceId).baseUrl(r.baseUrl).apiKey(r.apiKey).build());
        return new YoPlatformCatalog(Collections.unmodifiableMap(entries));
    }

    private static void register(
            Map<String, Entry> entries,
            YoPlatformDescriptor descriptor,
            Function<Resolved, YoPlatform> factory) {
        entries.put(descriptor.templateId(), new Entry(descriptor, factory));
    }

    private static final class Entry {
        final YoPlatformDescriptor descriptor;
        final Function<Resolved, YoPlatform> factory;

        Entry(YoPlatformDescriptor descriptor, Function<Resolved, YoPlatform> factory) {
            this.descriptor = descriptor;
            this.factory = factory;
        }
    }

    private static final class Resolved {
        final String instanceId;
        final String apiKey;
        final String baseUrl;

        Resolved(String instanceId, String apiKey, String baseUrl) {
            this.instanceId = instanceId;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
        }
    }
}
