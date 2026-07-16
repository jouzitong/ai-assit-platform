package ai.platform.aiassit.chat.workflow.data.service.control;

import ai.platform.aiassit.chat.workflow.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.workflow.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.service.ai.spi.agent.AgentEntrySummary;

import java.util.List;
import java.util.Map;

public interface AiAgentControlService {

    List<AgentControlDTOs.Catalog> listAgents();

    AgentControlDTOs.Version getAgent(String agentCode);

    AgentControlDTOs.Version createAgent(AgentControlDTOs.CreateRequest request);

    AgentControlDTOs.Version updateAgent(String agentCode, AgentControlDTOs.UpdateRequest request);

    boolean deleteAgent(String agentCode);

    AgentControlDTOs.Version createVersion(String agentCode, AgentControlDTOs.VersionCreateRequest request);

    List<AgentControlDTOs.Version> listVersions(String agentCode);

    AgentControlDTOs.Version getVersion(String agentCode, Integer version);

    ValidationReportDTO validateVersion(String agentCode, Integer version);

    ValidationReportDTO compatibility(String agentCode, Integer version);

    Map<String, Object> testVersion(String agentCode, Integer version, Map<String, Object> input);

    AgentControlDTOs.Version publishVersion(String agentCode, Integer version);

    AgentControlDTOs.EntryBinding upsertEntryBinding(String entryCode,
                                                     AgentControlDTOs.EntryBindingRequest request);

    List<AgentControlDTOs.EntryBinding> listEntryBindings(String entryCode);

    List<AgentControlDTOs.EntrySelection> listEntrySelections();

    AgentControlDTOs.EntrySelection updateEntrySelection(String entryCode,
                                                         AgentControlDTOs.EntrySelectionRequest request);

    List<AgentEntrySummary> listAvailable(String entryCode);

    boolean deleteEntryBinding(Long bindingId);
}
