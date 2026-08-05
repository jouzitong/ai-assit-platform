package ai.platform.aiassit.user.errcode.data.controller;

import ai.platform.aiassit.user.api.ErrCodeQueryApi;
import ai.platform.aiassit.user.api.dto.ErrCodeQueryRequest;
import ai.platform.aiassit.user.api.dto.ErrCodeQueryResponse;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeResolveDTO;
import ai.platform.aiassit.user.errcode.data.service.ErrCodeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台内部错误码解析接口。
 *
 * <p>供其他服务将稳定错误码转换为 HTTP 状态、目标语言消息模板和描述，避免调用方自行复制错误文案或本地化规则。</p>
 */
@RestController
@RequestMapping("/internal/v1/err-code")
public class ErrCodeInternalController implements ErrCodeQueryApi {

    private final ErrCodeService service;

    public ErrCodeInternalController(ErrCodeService service) {
        this.service = service;
    }

    /**
     * 按错误码和语言查询可返回给调用方的错误语义。
     *
     * @param request 错误码查询请求体，包含错误码和可选语言标识
     * @return 解析结果，包含 HTTP 状态、实际语言、消息模板和说明
     */
    @Override
    public ErrCodeQueryResponse queryErrCode(ErrCodeQueryRequest request) {
        ErrCodeResolveDTO resolved = service.resolve(
                request == null ? null : request.getCode(),
                request == null ? null : request.getLocale()
        );

        ErrCodeQueryResponse response = new ErrCodeQueryResponse();
        response.setCode(resolved.getCode());
        response.setHttpStatus(resolved.getHttpStatus());
        response.setLocale(resolved.getLocale());
        response.setMessageTemplate(resolved.getMessageTemplate());
        response.setDescription(resolved.getDescription());
        return response;
    }
}
