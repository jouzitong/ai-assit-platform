package ai.platform.aiassit.db.engine.virtualization.adapter.service;

import ai.platform.aiassit.data.virtualization.api.exception.VirtualDataRuntimeException;
import ai.platform.aiassit.db.engine.api.constant.DataPreviewErrorCode;

/**
 * 数据预览默认访问策略。
 *
 * <p>当前仓库尚无虚拟实体的行列授权数据源，因此该默认实现只建立“必须有有效主体”的安全边界，
 * 并允许本次请求中已经由已发布虚拟目录校验过的字段。接入真实数据权限后，应替换此 Bean，
 * 返回授权字段集合以及服务端强制行过滤；不得把本实现误认为完整的数据授权。</p>
 */
public class AuthenticatedDataPreviewAccessPolicy implements DataPreviewAccessPolicy {

    @Override
    public AccessDecision authorize(AccessRequest request) {
        if (request == null
                || request.userContext() == null
                || request.userContext().subject() == null
                || request.userContext().subject().userId() == null) {
            throw new VirtualDataRuntimeException(
                    DataPreviewErrorCode.AUTH_REQUIRED,
                    "数据预览需要有效的用户主体"
            );
        }
        return new AccessDecision(request.requestedFields(), null);
    }
}
