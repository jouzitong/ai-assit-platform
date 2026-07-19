package ai.platform.aiassit.db.engine.virtualization.adapter.service;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCatalogDescriptor;
import org.athena.framework.security.api.model.UserContext;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据预览授权扩展点。
 *
 * <p>实现可以收窄允许访问的虚拟字段，并返回必须与用户条件合并的行级过滤树。</p>
 */
public interface DataPreviewAccessPolicy {

    AccessDecision authorize(AccessRequest request);

    record AccessRequest(
            UserContext userContext,
            VirtualCatalogDescriptor catalog,
            Set<String> requestedFields
    ) {
        public AccessRequest {
            requestedFields = requestedFields == null
                    ? Set.of() : Set.copyOf(new LinkedHashSet<>(requestedFields));
        }
    }

    record AccessDecision(Set<String> allowedFields, FilterNode enforcedRowFilter) {
        public AccessDecision {
            allowedFields = allowedFields == null
                    ? Set.of() : Set.copyOf(new LinkedHashSet<>(allowedFields));
        }
    }
}
