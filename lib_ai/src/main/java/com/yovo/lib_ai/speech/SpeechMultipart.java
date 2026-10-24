package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * 手工拼 {@code multipart/form-data} 请求体。
 *
 * <p>为什么不用第三方 multipart 库：多一个依赖只为拼几行 CRLF 不划算，
 * 而且这里字段固定（file / model / response_format / language / prompt），
 * 手写的字节布局可以直接被单测断言。
 *
 * <p>边界串每次请求随机生成：{@code prompt} 是用户可控的自由文本，
 * 固定边界会让"提示词里恰好含边界串"变成可以打乱分段结构的注入点；
 * 128bit 随机边界让这种碰撞不成立，同时不必去改写用户的提示词。
 */
final class SpeechMultipart {

    private static final String CRLF = "\r\n";
    private static final int BOUNDARY_RANDOM_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SpeechMultipart() {
    }

    /** 生成一次请求的边界串。 */
    @NonNull
    static String boundary() {
        byte[] random = new byte[BOUNDARY_RANDOM_BYTES];
        RANDOM.nextBytes(random);
        StringBuilder builder = new StringBuilder("----YoSpeech").append(Long.toHexString(System.nanoTime()));
        for (byte value : random) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    @NonNull
    static String contentType(@NonNull String boundary) {
        return "multipart/form-data; boundary=" + boundary;
    }

    /**
     * 拼出完整请求体。
     *
     * <p>字段顺序固定：file → model → response_format → language → prompt。
     * 服务端不关心顺序，但固定顺序让抓包与单测的断言稳定。
     */
    @NonNull
    static byte[] encode(@NonNull YoTranscriptionRequest request, @NonNull String boundary) {
        byte[] payload = request.payload();
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 512);

        write(out, "--" + boundary + CRLF);
        write(out, "Content-Disposition: form-data; name=\"file\"; filename=\""
                + YoTranscriptionRequest.FILENAME + "\"" + CRLF);
        write(out, "Content-Type: " + request.fileContentType() + CRLF);
        write(out, CRLF);
        out.writeBytes(payload);
        write(out, CRLF);

        field(out, boundary, "model", request.model());
        // 解码器只认 JSON；显式声明比依赖网关默认值更可靠（各家默认值不一致）。
        field(out, boundary, "response_format", "json");
        if (request.language() != null) {
            field(out, boundary, "language", request.language());
        }
        if (request.prompt() != null) {
            field(out, boundary, "prompt", request.prompt());
        }

        write(out, "--" + boundary + "--" + CRLF);
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) {
        write(out, "--" + boundary + CRLF);
        write(out, "Content-Disposition: form-data; name=\"" + name + "\"" + CRLF);
        write(out, CRLF);
        write(out, value);
        write(out, CRLF);
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
