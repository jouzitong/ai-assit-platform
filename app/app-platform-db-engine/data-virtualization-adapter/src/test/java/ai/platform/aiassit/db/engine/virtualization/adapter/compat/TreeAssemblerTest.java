package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TreeAssemblerTest {

    private final TreeAssembler assembler = new TreeAssembler();

    @Test
    void buildsTreeInInputOrderAndTreatsOrphansAsRoots() {
        DbQueryTreeRequest request = request(null);
        request.setFields(List.of("department.name"));
        VirtualQueryResponse source = response(
                row(1, 0, "root", "HQ"),
                row(2, 1, "child", "R&D"),
                row(3, 999, "orphan", "External")
        );

        DbQueryTreeResponse response = assembler.assemble(source, request);

        assertEquals(List.of(1, 3), response.getRecords().stream().map(node -> node.getId()).toList());
        assertEquals(2, response.getRecords().get(0).getChildren().get(0).getId());
        @SuppressWarnings("unchecked")
        Map<String, Object> department = (Map<String, Object>) response.getRecords().get(0).getData().get("department");
        assertEquals("HQ", department.get("name"));
    }

    @Test
    void rejectsDuplicateIds() {
        LegacyQueryCompatibilityException exception = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> assembler.assemble(response(row(1, 0, "a", null), row(1, 0, "b", null)), request(null))
        );

        assertEquals(TreeAssembler.DUPLICATE_ID, exception.getCode());
    }

    @Test
    void rejectsCyclesIncludingComponentsWithoutRoots() {
        LegacyQueryCompatibilityException exception = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> assembler.assemble(response(row(1, 2, "a", null), row(2, 1, "b", null)), request(null))
        );

        assertEquals(TreeAssembler.CYCLE, exception.getCode());
    }

    @Test
    void enforcesConfiguredMaxDepth() {
        LegacyQueryCompatibilityException exception = assertThrows(
                LegacyQueryCompatibilityException.class,
                () -> assembler.assemble(
                        response(row(1, 0, "a", null), row(2, 1, "b", null), row(3, 2, "c", null)),
                        request(2)
                )
        );

        assertEquals(TreeAssembler.MAX_DEPTH_EXCEEDED, exception.getCode());
    }

    private DbQueryTreeRequest request(Integer maxDepth) {
        DbQueryTreeExt ext = new DbQueryTreeExt();
        ext.setMaxDepth(maxDepth);
        DbQueryTreeRequest request = new DbQueryTreeRequest();
        request.setExt(ext);
        return request;
    }

    @SafeVarargs
    private final VirtualQueryResponse response(Map<String, Object>... rows) {
        VirtualQueryResponse response = new VirtualQueryResponse();
        response.setRecords(List.of(rows));
        return response;
    }

    private Map<String, Object> row(Object id, Object parentId, String name, Object departmentName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("parent_id", parentId);
        row.put("name", name);
        row.put("department.name", departmentName);
        return row;
    }
}
