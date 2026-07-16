package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionStore;
import ai.platform.aiassit.service.ai.spi.agent.AgentEntrySummary;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType;
import ai.platform.aiassit.service.ai.spi.agent.StoredAgentDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves published references and freezes a complete runtime snapshot. */
@Service
public class AgentSnapshotResolver {

    private static final int MAX_AGENT_COUNT = 16;
    private static final int MAX_AGENT_DEPTH = 4;
    private static final Pattern AGENT_REF = Pattern.compile("^agent://([^/]+)/v?(\\d+)$");

    private final List<AgentDefinitionStore> stores;
    private final AgentManifestValidator validator;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;

    public AgentSnapshotResolver(List<AgentDefinitionStore> stores,
                                 AgentManifestValidator validator,
                                 ObjectMapper objectMapper) {
        this.stores = stores == null ? List.of() : List.copyOf(stores);
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public AgentDefinitionSnapshot resolve(AgentTarget target) {
        AgentTarget requested = target == null ? AgentTarget.homeChat() : target;
        StoredAgentDefinition root = requested.explicit()
                ? resolveDefinition(requested.agentCode(), requested.agentVersion())
                : resolveEntry(StringUtils.hasText(requested.entryCode()) ? requested.entryCode() : "HOME_CHAT");

        Map<String, Map<String, Object>> graph = new LinkedHashMap<>();
        Map<String, StoredAgentDefinition> definitions = new LinkedHashMap<>();
        Map<String, Integer> versionsByCode = new LinkedHashMap<>();
        Deque<String> path = new ArrayDeque<>();
        collect(root, 1, path, graph, definitions, versionsByCode);

        Map<String, Object> rootManifest = graph.remove(identity(root));
        AgentDefinitionSnapshot snapshot = new AgentDefinitionSnapshot();
        snapshot.setAgentCode(root.getAgentCode());
        snapshot.setAgentVersion(root.getAgentVersion());
        snapshot.setRuntimeType(root.getRuntimeType() == null
                ? AgentRuntimeType.OPENAI_AGENTS_PYTHON : root.getRuntimeType());
        snapshot.setSdkVersion(root.getSdkVersion());
        snapshot.setRootAgent(rootManifest);
        snapshot.setAgentGraph(new ArrayList<>(graph.values()));
        snapshot.setResolvedCapabilities(mergeCapabilities(definitions.values(), rootManifest));
        snapshot.setWorkflowSnapshot(resolveJsonObject(root.getWorkflowSnapshotJson(),
                deriveWorkflow(rootManifest)));
        snapshot.setSnapshotHash(hash(snapshot));
        return snapshot;
    }

    public List<AgentEntrySummary> available(String entryCode) {
        Map<String, AgentEntrySummary> result = new LinkedHashMap<>();
        for (AgentDefinitionStore store : stores) {
            List<AgentEntrySummary> values = store.listAvailable(entryCode);
            if (values == null) {
                continue;
            }
            for (AgentEntrySummary value : values) {
                if (value != null && StringUtils.hasText(value.getCode())) {
                    result.putIfAbsent(value.getCode(), value);
                }
            }
        }
        return List.copyOf(result.values());
    }

    private void collect(StoredAgentDefinition definition,
                         int depth,
                         Deque<String> path,
                         Map<String, Map<String, Object>> graph,
                         Map<String, StoredAgentDefinition> definitions,
                         Map<String, Integer> versionsByCode) {
        if (definition == null) {
            throw invalid("Agent definition is required");
        }
        if (depth > MAX_AGENT_DEPTH) {
            throw invalid("Agent graph exceeds maximum depth " + MAX_AGENT_DEPTH);
        }
        String identity = identity(definition);
        if (path.contains(identity)) {
            throw invalid("Agent graph cycle detected: " + String.join(" -> ", path)
                    + " -> " + identity);
        }
        if (graph.containsKey(identity)) {
            return;
        }
        Integer existingVersion = versionsByCode.putIfAbsent(
                definition.getAgentCode(), definition.getAgentVersion());
        if (existingVersion != null && !existingVersion.equals(definition.getAgentVersion())) {
            throw invalid("Agent graph must not contain multiple versions of the same Agent: "
                    + definition.getAgentCode());
        }
        if (graph.size() >= MAX_AGENT_COUNT) {
            throw invalid("Agent graph exceeds maximum size " + MAX_AGENT_COUNT);
        }

        JsonNode manifestNode = parse(definition.getManifestJson(), definition.getAgentCode());
        try {
            validator.validate(manifestNode, definition.getAgentCode());
        } catch (IllegalArgumentException ex) {
            throw invalid(ex.getMessage());
        }
        Map<String, Object> manifest = objectMapper.convertValue(manifestNode, LinkedHashMap.class);
        graph.put(identity, manifest);
        definitions.put(identity, definition);
        path.addLast(identity);
        for (AgentRef ref : collaboratorRefs(manifestNode)) {
            collect(resolveDefinition(ref.code(), ref.version()), depth + 1, path,
                    graph, definitions, versionsByCode);
        }
        path.removeLast();
    }

    private String identity(StoredAgentDefinition definition) {
        return definition.getAgentCode() + "@" + definition.getAgentVersion();
    }

    private List<AgentRef> collaboratorRefs(JsonNode manifest) {
        List<AgentRef> refs = new ArrayList<>();
        appendRefs(manifest.at("/spec/collaboration/agentTools"), refs);
        appendRefs(manifest.at("/spec/collaboration/handoffs"), refs);
        return refs;
    }

    private void appendRefs(JsonNode entries, List<AgentRef> refs) {
        if (entries == null || !entries.isArray()) {
            return;
        }
        for (JsonNode entry : entries) {
            String raw = entry.path("targetAgentRef").asText();
            Matcher matcher = AGENT_REF.matcher(raw);
            if (!matcher.matches()) {
                throw invalid("Invalid targetAgentRef: " + raw);
            }
            refs.add(new AgentRef(matcher.group(1), Integer.parseInt(matcher.group(2))));
        }
    }

    private StoredAgentDefinition resolveDefinition(String code, Integer version) {
        for (AgentDefinitionStore store : stores) {
            Optional<StoredAgentDefinition> result = store.resolve(code, version);
            if (result.isPresent()) {
                return result.get();
            }
        }
        throw invalid("Published Agent not found: " + code + (version == null ? "" : "/v" + version));
    }

    private StoredAgentDefinition resolveEntry(String entryCode) {
        for (AgentDefinitionStore store : stores) {
            Optional<StoredAgentDefinition> result = store.resolveEntry(entryCode);
            if (result.isPresent()) {
                return result.get();
            }
        }
        throw invalid("Enabled Agent entry not found: " + entryCode);
    }

    private JsonNode parse(String json, String code) {
        if (!StringUtils.hasText(json)) {
            throw invalid("Published Agent manifest is empty: " + code);
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw invalid("Published Agent manifest is invalid JSON: " + code);
        }
    }

    private Map<String, Object> resolveJsonObject(String json, Map<String, Object> fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isObject() && !node.isEmpty()) {
                return objectMapper.convertValue(node, LinkedHashMap.class);
            }
        } catch (JsonProcessingException ignored) {
            // Published manifest content remains the source of truth for the fallback projection.
        }
        return fallback;
    }

    private Map<String, Object> deriveCapabilities(Map<String, Object> manifest) {
        Object specValue = manifest == null ? null : manifest.get("spec");
        if (!(specValue instanceof Map<?, ?> spec)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("toolRefs", "skillRefs", "knowledgeRefs", "mcpRefs", "guardrails")) {
            if (spec.containsKey(key)) {
                result.put(key, spec.get(key));
            }
        }
        return result;
    }

    private Map<String, Object> mergeCapabilities(Iterable<StoredAgentDefinition> definitions,
                                                  Map<String, Object> rootManifest) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (StoredAgentDefinition definition : definitions) {
            Map<String, Object> resolved = resolveJsonObject(definition.getResolvedCapabilitiesJson(), Map.of());
            mergeCapabilityMap(merged, resolved);
        }
        if (merged.isEmpty()) {
            return deriveCapabilities(rootManifest);
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private void mergeCapabilityMap(Map<String, Object> target, Map<String, Object> source) {
        source.forEach((key, value) -> {
            if (value instanceof List<?> values) {
                List<Object> accumulated = (List<Object>) target.computeIfAbsent(key, ignored -> new ArrayList<>());
                Set<String> identities = new LinkedHashSet<>();
                for (Object existing : accumulated) identities.add(capabilityIdentity(existing));
                for (Object item : values) {
                    if (identities.add(capabilityIdentity(item))) accumulated.add(item);
                }
            } else if (value instanceof Map<?, ?> values) {
                Map<String, Object> accumulated = (Map<String, Object>) target.computeIfAbsent(
                        key, ignored -> new LinkedHashMap<>());
                values.forEach((nestedKey, nestedValue) ->
                        accumulated.putIfAbsent(String.valueOf(nestedKey), nestedValue));
            } else if (value != null) {
                target.putIfAbsent(key, value);
            }
        });
    }

    private String capabilityIdentity(Object value) {
        if (value instanceof Map<?, ?> item) {
            Object code = item.get("code");
            Object version = item.get("version");
            Object ref = item.get("ref");
            if (code != null) return String.valueOf(code) + "@" + String.valueOf(version);
            if (ref != null) return String.valueOf(ref);
        }
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> deriveWorkflow(Map<String, Object> manifest) {
        Object specValue = manifest == null ? null : manifest.get("spec");
        if (!(specValue instanceof Map<?, ?> spec) || !(spec.get("output") instanceof Map<?, ?> output)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        output.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String hash(AgentDefinitionSnapshot snapshot) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("protocolVersion", snapshot.getProtocolVersion());
        canonical.put("agentCode", snapshot.getAgentCode());
        canonical.put("agentVersion", snapshot.getAgentVersion());
        canonical.put("runtimeType", snapshot.getRuntimeType());
        canonical.put("sdkVersion", snapshot.getSdkVersion());
        canonical.put("rootAgent", snapshot.getRootAgent());
        canonical.put("agentGraph", snapshot.getAgentGraph());
        canonical.put("resolvedCapabilities", snapshot.getResolvedCapabilities());
        canonical.put("workflowSnapshot", snapshot.getWorkflowSnapshot());
        try {
            byte[] bytes = canonicalMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash Agent snapshot", ex);
        }
    }

    private BizException invalid(String message) {
        return BizException.of(AiChatBizCodeConstant.AGENT_EXECUTION_FAILED, message);
    }

    private record AgentRef(String code, Integer version) {
    }
}
