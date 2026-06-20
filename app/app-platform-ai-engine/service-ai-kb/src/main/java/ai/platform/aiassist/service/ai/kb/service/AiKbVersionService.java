package ai.platform.aiassist.service.ai.kb.service;

import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbVersionDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

public interface AiKbVersionService extends IMapperService<AiKbVersionDTO> {

    AiKbVersionDTO getDraftVersion(String kbCode);

    AiKbVersionDTO getCurrentVersion(String kbCode);

    AiKbVersionDTO getVersion(String kbCode, Integer versionNo);

    AiKbVersionDTO getVersion(Long kbVersionId);

    List<AiKbVersionDTO> listDraftVersions();

    Integer getMaxVersionNo(String kbCode);
}
