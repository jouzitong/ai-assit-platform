package ai.platform.aiassit.gateway.core.security;

import ai.platform.aiassit.user.api.dto.UserPermissionQueryResponse;

/**
 * 网关权限查询结果。
 */
public record GatewayPermissionSnapshot(UserPermissionQueryResponse response, boolean granted) {
}
