package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeNode;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 将虚拟 LIST 的扁平记录安全组装为旧树形响应。 */
public class TreeAssembler {

    static final String INVALID_TREE = "LEGACY_TREE_INVALID";
    static final String DUPLICATE_ID = "LEGACY_TREE_DUPLICATE_ID";
    static final String CYCLE = "LEGACY_TREE_CYCLE";
    static final String MAX_DEPTH_EXCEEDED = "LEGACY_TREE_MAX_DEPTH_EXCEEDED";
    static final int DEFAULT_MAX_DEPTH = 64;

    private final LegacyResponseAssembler responseAssembler;

    public TreeAssembler() {
        this(new LegacyResponseAssembler());
    }

    TreeAssembler(LegacyResponseAssembler responseAssembler) {
        this.responseAssembler = responseAssembler;
    }

    public DbQueryTreeResponse assemble(VirtualQueryResponse source, DbQueryTreeRequest request) {
        Collection<String> outputFields = request == null ? List.of() : request.getFields();
        return assemble(source, request, outputFields);
    }

    public DbQueryTreeResponse assemble(
            VirtualQueryResponse source,
            DbQueryTreeRequest request,
            Collection<String> translatedOutputFields
    ) {
        DbQueryTreeExt ext = request == null || request.getExt() == null ? new DbQueryTreeExt() : request.getExt();
        String idField = valueOrDefault(ext.getIdField(), "id");
        String parentField = valueOrDefault(ext.getParentField(), "parent_id");
        String labelField = valueOrDefault(ext.getLabelField(), "name");
        int maxDepth = ext.getMaxDepth() == null ? DEFAULT_MAX_DEPTH : ext.getMaxDepth();
        if (maxDepth < 1) {
            throw error(INVALID_TREE, "maxDepth 必须大于 0");
        }

        Set<String> outputFields = new LinkedHashSet<>();
        outputFields.add(idField);
        outputFields.add(parentField);
        outputFields.add(labelField);
        if (translatedOutputFields != null) {
            outputFields.addAll(translatedOutputFields);
        }

        List<Map<String, Object>> rows = source == null || source.getRecords() == null
                ? List.of() : source.getRecords();
        Map<Object, DbQueryTreeNode> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                throw error(INVALID_TREE, "树形查询结果不能包含 null 记录");
            }
            Object id = row.get(idField);
            if (id == null) {
                throw error(INVALID_TREE, "树节点 id 不能为空，字段: " + idField);
            }
            if (nodes.containsKey(id)) {
                throw error(DUPLICATE_ID, "树节点 id 重复: " + id);
            }
            DbQueryTreeNode node = new DbQueryTreeNode();
            node.setId(id);
            node.setParentId(row.get(parentField));
            Object label = row.get(labelField);
            node.setLabel(label == null ? null : String.valueOf(label));
            node.setData(responseAssembler.detailRecord(row, outputFields));
            nodes.put(id, node);
        }

        validateDepthAndCycles(nodes, ext.getRootValue(), maxDepth);

        List<DbQueryTreeNode> roots = new ArrayList<>();
        for (DbQueryTreeNode node : nodes.values()) {
            Object parentId = node.getParentId();
            if (isRoot(parentId, ext.getRootValue()) || !nodes.containsKey(parentId)) {
                roots.add(node);
            } else {
                nodes.get(parentId).getChildren().add(node);
            }
        }

        DbQueryTreeResponse response = new DbQueryTreeResponse();
        response.setRecords(roots);
        response.setSummary(new LinkedHashMap<>());
        return response;
    }

    private void validateDepthAndCycles(Map<Object, DbQueryTreeNode> nodes, Object rootValue, int maxDepth) {
        Map<Object, Integer> knownDepths = new LinkedHashMap<>();
        for (Object startId : nodes.keySet()) {
            if (knownDepths.containsKey(startId)) {
                continue;
            }
            List<Object> path = new ArrayList<>();
            Map<Object, Integer> pathIndexes = new LinkedHashMap<>();
            Object currentId = startId;
            int baseDepth = 0;
            while (currentId != null) {
                Integer known = knownDepths.get(currentId);
                if (known != null) {
                    baseDepth = known;
                    break;
                }
                if (pathIndexes.containsKey(currentId)) {
                    throw error(CYCLE, "树节点存在循环关系，节点: " + currentId);
                }
                DbQueryTreeNode current = nodes.get(currentId);
                if (current == null) {
                    break;
                }
                pathIndexes.put(currentId, path.size());
                path.add(currentId);
                Object parentId = current.getParentId();
                if (isRoot(parentId, rootValue) || !nodes.containsKey(parentId)) {
                    break;
                }
                currentId = parentId;
            }

            int depth = baseDepth;
            for (int index = path.size() - 1; index >= 0; index--) {
                depth++;
                Object id = path.get(index);
                if (depth > maxDepth) {
                    throw error(MAX_DEPTH_EXCEEDED, "树深度超过 maxDepth=" + maxDepth + "，节点: " + id);
                }
                knownDepths.put(id, depth);
            }
        }
    }

    private boolean isRoot(Object parentId, Object rootValue) {
        if (rootValue != null) {
            return Objects.equals(parentId, rootValue);
        }
        return parentId == null || "".equals(parentId) || "0".equals(String.valueOf(parentId));
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private LegacyQueryCompatibilityException error(String code, String message) {
        return new LegacyQueryCompatibilityException(code, message);
    }
}
