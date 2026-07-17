package ai.platform.aiassit.chat.agent.control.data.service.control;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ToolControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;

import java.util.List;

public interface AiToolControlService {

    List<ToolControlDTOs.Catalog> listCatalogs();

    ToolControlDTOs.Version getTool(String toolCode);

    ToolControlDTOs.Version createDraft(ToolControlDTOs.DraftRequest request);

    ToolControlDTOs.Version createVersion(String toolCode, ToolControlDTOs.DraftRequest request);

    ToolControlDTOs.Version updateTool(String toolCode, ToolControlDTOs.DraftRequest request);

    boolean deleteTool(String toolCode);

    List<ToolControlDTOs.Version> listVersions(String toolCode);

    ToolControlDTOs.Version getVersion(String toolCode, Integer version);

    ValidationReportDTO validateVersion(String toolCode, Integer version);

    ToolControlDTOs.Version publishVersion(String toolCode, Integer version);

    java.util.Map<String, Object> testVersion(String toolCode, Integer version,
                                              java.util.Map<String, Object> payload);
}
