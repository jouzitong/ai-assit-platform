package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.api.exception.VirtualDataRuntimeException;
import ai.platform.aiassit.db.engine.virtualization.adapter.compat.LegacyQueryCompatibilityException;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.athena.framework.web.vo.R;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将虚拟内核和旧协议转换错误统一映射为平台业务响应。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "ai.platform.aiassit.db.engine.virtualization.adapter.controller")
public class VirtualDataExceptionAdvice {

    @ExceptionHandler(VirtualDataRuntimeException.class)
    public R<Void> virtualDataException(VirtualDataRuntimeException exception) {
        log.warn("virtual data request failed: category={}", exception.getCode(), exception);
        return failure(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(LegacyQueryCompatibilityException.class)
    public R<Void> legacyCompatibilityException(LegacyQueryCompatibilityException exception) {
        log.warn("legacy db query request failed: category={}", exception.getCode(), exception);
        return failure(exception.getCode(), exception.getMessage());
    }

    private R<Void> failure(String code, String message) {
        return R.fail(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR, code + ": " + message);
    }
}
