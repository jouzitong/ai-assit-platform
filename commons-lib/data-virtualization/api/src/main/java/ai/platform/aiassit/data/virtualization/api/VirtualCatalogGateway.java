package ai.platform.aiassit.data.virtualization.api;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualCatalogDescriptor;

/** 供进程内应用层解析已发布虚拟目录的只读入口。 */
public interface VirtualCatalogGateway {

    VirtualCatalogDescriptor describePublished(String entityCode, Long catalogVersion);
}
