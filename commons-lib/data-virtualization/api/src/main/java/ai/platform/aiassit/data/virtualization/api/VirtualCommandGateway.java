package ai.platform.aiassit.data.virtualization.api;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualCommandRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCommandResponse;

/** 进程内虚拟写入用例入口，不承载 Feign 或 HTTP 语义。 */
public interface VirtualCommandGateway {

    VirtualCommandResponse command(VirtualCommandRequest request);
}
