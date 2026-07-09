package ai.platform.aiassit.user.errcode.data.controller;

import ai.platform.aiassit.user.api.ErrCodeQueryApi;
import ai.platform.aiassit.user.api.dto.ErrCodeQueryRequest;
import ai.platform.aiassit.user.api.dto.ErrCodeQueryResponse;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeResolveDTO;
import ai.platform.aiassit.user.errcode.data.service.ErrCodeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/err-code")
public class ErrCodeInternalController implements ErrCodeQueryApi {

    private final ErrCodeService service;

    public ErrCodeInternalController(ErrCodeService service) {
        this.service = service;
    }

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
