package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.retrieval.YoFileRef;
import com.yovo.lib_ai.retrieval.YoFileUploadRequest;

import java.util.List;

/**
 * 文件上传、查询、删除与临时 URL。覆盖 OpenAI / Anthropic / DeepSeek 兼容的 {@code /v1/files}
 * 与 DashScope 原生 {@code /api/v1/files}。
 *
 * <p>multipart 由 SDK 内部用 OkHttp 组装；调用在调用线程阻塞，不要在主线程调用。</p>
 */
public interface YoFiles {

    /** 上传一个文件，返回可被后续请求引用的 {@link YoFileRef}。 */
    @NonNull
    YoFileRef upload(@NonNull YoFileUploadRequest request);

    /** 列出当前账号的全部文件（内部走完分页）。 */
    @NonNull
    List<YoFileRef> list();

    /** 按 id 查询单个文件。 */
    @NonNull
    YoFileRef get(@NonNull String fileId);

    /** 删除一个文件；重复删除视为成功（幂等）。 */
    boolean delete(@NonNull String fileId);

    /**
     * 换取一个短期可用的下载/引用 URL。并非所有厂商都提供；不支持时抛
     * {@link UnsupportedOperationException}。
     */
    @NonNull
    String temporaryUrl(@NonNull String fileId);
}
