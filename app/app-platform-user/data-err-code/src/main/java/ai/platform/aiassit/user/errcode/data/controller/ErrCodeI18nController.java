package ai.platform.aiassit.user.errcode.data.controller;

import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeI18nDTO;
import ai.platform.aiassit.user.errcode.data.entity.req.ErrCodeI18nQueryRequest;
import ai.platform.aiassit.user.errcode.data.service.ErrCodeI18nService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/err-code-i18n")
public class ErrCodeI18nController
        extends BaseController<ErrCodeI18nDTO, ErrCodeI18nQueryRequest, ErrCodeI18nService> {

    private final ErrCodeI18nService service;

    public ErrCodeI18nController(ErrCodeI18nService service) {
        this.service = service;
    }

    @Override
    protected ErrCodeI18nService service() {
        return service;
    }
}
