package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.retrieval.YoFileUploadRequest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * 手工拼 {@code multipart/form-data}。字段固定为 file → purpose，边界每次随机，
 * 避免文件名或用途文本里恰好含边界串而打乱分段结构。
 */
final class MultipartForm {

    private static final String CRLF = "\r\n";
    private static final SecureRandom RANDOM = new SecureRandom();

    final String boundary;
    final byte[] body;

    private MultipartForm(String boundary, byte[] body) {
        this.boundary = boundary;
        this.body = body;
    }

    @NonNull
    static MultipartForm of(@NonNull YoFileUploadRequest request) {
        String boundary = boundary();
        ByteArrayOutputStream out = new ByteArrayOutputStream(request.dataLength() + 512);

        write(out, "--" + boundary + CRLF);
        write(out, "Content-Disposition: form-data; name=\"file\"; filename=\""
                + request.filename() + "\"" + CRLF);
        write(out, "Content-Type: " + request.contentType() + CRLF);
        write(out, CRLF);
        out.writeBytes(request.dataUnsafe());
        write(out, CRLF);

        if (request.purpose() != null) {
            field(out, boundary, "purpose", request.purpose());
        }

        write(out, "--" + boundary + "--" + CRLF);
        return new MultipartForm(boundary, out.toByteArray());
    }

    @NonNull
    String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) {
        write(out, "--" + boundary + CRLF);
        write(out, "Content-Disposition: form-data; name=\"" + name + "\"" + CRLF);
        write(out, CRLF);
        write(out, value);
        write(out, CRLF);
    }

    @NonNull
    private static String boundary() {
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        StringBuilder builder = new StringBuilder("----YoFiles").append(Long.toHexString(System.nanoTime()));
        for (byte value : random) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
