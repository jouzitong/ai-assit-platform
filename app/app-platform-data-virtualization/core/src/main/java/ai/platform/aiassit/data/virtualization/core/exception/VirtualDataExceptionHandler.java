package ai.platform.aiassit.data.virtualization.core.exception;

import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.athena.framework.web.vo.R;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "ai.platform.aiassit.data.virtualization.core.controller")
public class VirtualDataExceptionHandler {
    @ExceptionHandler(VirtualDataException.class)
    public R<Void> virtualDataException(VirtualDataException exception) {
        log.warn("virtual data request failed: category={}", exception.getCode(), exception);
        return R.fail(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR,
                exception.getCode() + ": " + exception.getMessage());
    }
}
