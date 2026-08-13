package ai.platform.aiassit.service.ai.api.openai;

import ai.platform.aiassit.service.ai.api.openai.dto.OpenAiChatCompletionRequest;
import ai.platform.aiassit.service.ai.api.openai.dto.OpenAiEmbeddingRequest;
import ai.platform.aiassit.service.ai.api.openai.dto.OpenAiEmbeddingResponse;
import ai.platform.aiassit.service.ai.api.openai.dto.OpenAiModel;
import ai.platform.aiassit.service.ai.api.openai.dto.OpenAiModelListResponse;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 对外提供的 OpenAI v1 兼容协议契约。
 *
 * <p>该接口面向将平台地址配置给 Spring AI OpenAI 客户端或其他 OpenAI 兼容客户端的场景，
 * 因而不使用平台内部的 {@code R<T>} 响应包装，也不接受平台模型编码。调用方传入的
 * {@code model} 必须是 {@code GET /openai/v1/models} 返回的远端模型标识。</p>
 *
 * <p>平台将 {@code /openai} 作为外部协议前缀；Spring AI 1.0.x 调用方应将 Base URL 配置到
 * {@code <Chat 服务根路径>/openai}。该客户端会自行追加 {@code /v1} 与具体资源路径。</p>
 *
 * <p>这里仅定义 HTTP 协议和 JSON 数据结构，不包含 Controller、鉴权、模型路由或 Provider
 * 调用实现。后续实现必须使用 Bearer API Key 鉴权，并在失败时按 OpenAI 错误对象
 * {@code {"error": {...}}} 返回对应的 HTTP 状态码。</p>
 *
 * <p>所有端点均要求 {@code Authorization: Bearer <API_KEY>} 请求头。API Key 仅用于
 * 定位调用方和执行授权，不得写入日志、响应、审计详情或上游模型请求。</p>
 *
 * <p>本期只声明当前平台已有边界可承接的模型发现、聊天补全和向量化能力。图像、音频、
 * Moderation 等能力应在对应领域服务和 Provider SPI 具备后再以同一版本协议扩展。</p>
 */
public interface OpenAiCompatibleApi {

    /**
     * 返回当前凭据可使用的模型目录，符合 OpenAI {@code GET /openai/v1/models} 协议。
     *
     * <p>实现方只可返回调用方有权使用、客户端和模型均处于启用状态的模型。</p>
     */
    @GetMapping(value = "/openai/v1/models", produces = MediaType.APPLICATION_JSON_VALUE)
    @IgnoredResultWrapper
    OpenAiModelListResponse listModels(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );

    /** 返回一个模型的标准描述，符合 OpenAI {@code GET /openai/v1/models/{model}} 协议。 */
    @GetMapping(value = "/openai/v1/models/{model}", produces = MediaType.APPLICATION_JSON_VALUE)
    @IgnoredResultWrapper
    OpenAiModel getModel(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("model") String model
    );

    /**
     * 执行 OpenAI Chat Completions 请求。
     *
     * <p>当 {@code stream} 未传或为 {@code false} 时，响应为
     * {@code OpenAiChatCompletionResponse} JSON；当 {@code stream=true} 时，响应必须为
     * {@code text/event-stream}，每个事件的数据结构为
     * {@code OpenAiChatCompletionChunk}，并以 {@code [DONE]} 结束。返回 {@code Object}
     * 是为了让后续 Web 实现能在同一路由上返回 JSON 或 SSE 流，而不把具体响应式框架泄露到
     * API 模块。</p>
     */
    @PostMapping(
            value = "/openai/v1/chat/completions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE}
    )
    @IgnoredResultWrapper
    Object createChatCompletion(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody OpenAiChatCompletionRequest request
    );

    /** 执行 OpenAI Embeddings 请求，符合 {@code POST /openai/v1/embeddings} 协议。 */
    @PostMapping(
            value = "/openai/v1/embeddings",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @IgnoredResultWrapper
    OpenAiEmbeddingResponse createEmbeddings(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody OpenAiEmbeddingRequest request
    );
}
