package ai.platform.aiassit.chat.agent.control.data.validator;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.support.ControlPlaneReferenceParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Publish-time validation for the complete immutable collaborator graph. */
@Component
public class AgentGraphValidator {

    static final int MAX_AGENT_COUNT = 16;
    static final int MAX_AGENT_DEPTH = 4;

    private final ControlPlaneReferenceParser references;
    private final AgentManifestValidator manifestValidator;

    public AgentGraphValidator(ControlPlaneReferenceParser references,
                               AgentManifestValidator manifestValidator) {
        this.references = references;
        this.manifestValidator = manifestValidator;
    }

    public ValidationReportDTO validate(String rootCode,
                                        Integer rootVersion,
                                        AgentControlDTOs.Manifest rootManifest,
                                        PublishedAgentResolver resolver) {
        ValidationReportDTO report = new ValidationReportDTO();
        if (!StringUtils.hasText(rootCode) || rootVersion == null || rootVersion < 1 || rootManifest == null) {
            report.error("Agent graph root must have a canonical code, positive version and manifest");
            report.finish();
            return report;
        }
        walk(new PublishedAgent(rootCode, rootVersion, rootManifest), 1, new ArrayDeque<>(),
                new LinkedHashSet<>(), new LinkedHashMap<>(), resolver, report);
        report.finish();
        return report;
    }

    private void walk(PublishedAgent current,
                      int depth,
                      Deque<String> path,
                      Set<String> visited,
                      Map<String, Integer> versionsByCode,
                      PublishedAgentResolver resolver,
                      ValidationReportDTO report) {
        String identity = identity(current);
        if (depth > MAX_AGENT_DEPTH) {
            report.error("Agent graph exceeds maximum depth " + MAX_AGENT_DEPTH + ": "
                    + String.join(" -> ", path) + " -> " + identity);
            return;
        }
        if (path.contains(identity)) {
            report.error("Agent graph cycle detected: " + String.join(" -> ", path) + " -> " + identity);
            return;
        }
        Integer existingVersion = versionsByCode.putIfAbsent(current.code(), current.version());
        if (existingVersion != null && !existingVersion.equals(current.version())) {
            report.error("Agent graph must not contain multiple versions of the same Agent: "
                    + current.code() + " (v" + existingVersion + " and v" + current.version() + ")");
            return;
        }
        if (visited.contains(identity)) return;
        if (visited.size() >= MAX_AGENT_COUNT) {
            report.error("Agent graph exceeds maximum size " + MAX_AGENT_COUNT);
            return;
        }
        visited.add(identity);
        path.addLast(identity);
        for (AgentControlDTOs.CollaboratorRef ref : collaborators(current.manifest())) {
            if (ref == null || !StringUtils.hasText(ref.getTargetAgentRef())) continue;
            ControlPlaneReferenceParser.ParsedReference parsed;
            try {
                parsed = references.parse(ref.getTargetAgentRef(), "agent");
            } catch (IllegalArgumentException ex) {
                report.error(ex.getMessage());
                continue;
            }
            PublishedAgent child = resolver.resolve(parsed.code(), parsed.version());
            if (child == null || child.version() == null || child.manifest() == null) {
                report.error("collaborator must resolve to a published enabled Agent: "
                        + ref.getTargetAgentRef());
                continue;
            }
            ValidationReportDTO childReport = manifestValidator.validate(child.code(), child.manifest());
            childReport.getErrors().forEach(error -> report.error(identity(child) + ": " + error));
            childReport.getWarnings().forEach(warning -> report.warn(
                    identity(child) + ": " + warning.message()));
            walk(child, depth + 1, path, visited, versionsByCode, resolver, report);
        }
        path.removeLast();
    }

    private List<AgentControlDTOs.CollaboratorRef> collaborators(AgentControlDTOs.Manifest manifest) {
        if (manifest == null || manifest.getSpec() == null || manifest.getSpec().getCollaboration() == null) {
            return List.of();
        }
        List<AgentControlDTOs.CollaboratorRef> result = new ArrayList<>();
        AgentControlDTOs.Collaboration collaboration = manifest.getSpec().getCollaboration();
        if (collaboration.getAgentTools() != null) result.addAll(collaboration.getAgentTools());
        if (collaboration.getHandoffs() != null) result.addAll(collaboration.getHandoffs());
        return result;
    }

    private String identity(PublishedAgent agent) {
        return agent.code() + "@v" + agent.version();
    }

    @FunctionalInterface
    public interface PublishedAgentResolver {
        PublishedAgent resolve(String code, Integer requestedVersion);
    }

    public record PublishedAgent(String code, Integer version, AgentControlDTOs.Manifest manifest) {
    }
}
