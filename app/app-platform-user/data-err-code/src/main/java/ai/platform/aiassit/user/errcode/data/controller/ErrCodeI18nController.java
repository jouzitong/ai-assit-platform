package ai.platform.aiassit.user.errcode.data.controller;

import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeI18nDTO;
import ai.platform.aiassit.user.errcode.data.entity.req.ErrCodeI18nQueryRequest;
import ai.platform.aiassit.user.errcode.data.service.ErrCodeI18nService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错误码多语言文案的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 维护同一错误码在不同语言下的消息模板和描述；请求体使用 {@link ErrCodeI18nDTO}，查询条件使用
 * {@link ErrCodeI18nQueryRequest}。</p>
 */
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
