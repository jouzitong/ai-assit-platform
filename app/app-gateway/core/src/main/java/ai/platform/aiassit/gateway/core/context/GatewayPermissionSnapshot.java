package ai.platform.aiassit.gateway.core.context;

import ai.platform.aiassit.user.api.dto.UserPermissionQueryResponse;

/**
 * 网关权限查询结果。
 */
public record GatewayPermissionSnapshot(UserPermissionQueryResponse response, boolean granted) {
}
