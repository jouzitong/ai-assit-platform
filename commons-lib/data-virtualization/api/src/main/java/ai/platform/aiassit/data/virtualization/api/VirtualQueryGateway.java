package ai.platform.aiassit.data.virtualization.api;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualExplainResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;

/** 进程内虚拟查询用例入口，不承载 Feign 或 HTTP 语义。 */
public interface VirtualQueryGateway {

    VirtualQueryResponse query(VirtualQueryRequest request);

    VirtualExplainResponse explain(VirtualQueryRequest request);
}
