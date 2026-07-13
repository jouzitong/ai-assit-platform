package ai.platform.aiassit.data.virtualization.core.knowledge;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualRelationEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import ai.platform.aiassit.service.ai.api.AiTextGenerationApi;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationRequest;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationResponse;
import org.athena.framework.web.vo.R;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualDescriptionServiceTest {

    @Mock private VirtualCatalogDataRepository repository;
    @Mock private AiTextGenerationApi textGenerationApi;

    @Test
    void generatesSearchOrientedDescriptionFromVirtualMetadataOnly() {
        VirtualEntityEntity contract = entity(1L, "employee_contract", "员工合同");
        VirtualEntityEntity department = entity(2L, "department", "部门");
        VirtualFieldEntity employeeId = field(11L, 1L, "employeeId", "员工ID", LogicalType.STRING);
        VirtualFieldEntity departmentId = field(12L, 1L, "departmentId", "部门ID", LogicalType.STRING);
        VirtualFieldEntity remoteDepartmentId = field(21L, 2L, "id", "部门ID", LogicalType.STRING);
        VirtualRelationEntity relation = new VirtualRelationEntity();
        relation.setRelationCode("contract_department");
        relation.setRelationName("合同所属部门");
        relation.setSourceEntityId(1L);
        relation.setSourceFieldId(12L);
        relation.setTargetEntityId(2L);
        relation.setTargetFieldId(21L);
        relation.setEnabled(true);

        when(repository.entityById(1L)).thenReturn(contract);
        when(repository.entityById(2L)).thenReturn(department);
        when(repository.fields(1L)).thenReturn(List.of(employeeId, departmentId));
        when(repository.relations(1L)).thenReturn(List.of(relation));
        when(repository.fieldById(12L)).thenReturn(departmentId);
        when(repository.fieldById(21L)).thenReturn(remoteDepartmentId);
        when(textGenerationApi.generate(any())).thenReturn(R.ok(new AiTextGenerationResponse(
                "**业务语义**：员工合同主题数据。\n\n**检索线索**：\n- 员工\n- 部门", "test-model", "req-1")));

        VirtualDescriptionGenerateRequest input = new VirtualDescriptionGenerateRequest();
        input.setEntityId(1L);
        VirtualDescriptionGenerateResponse result = new VirtualDescriptionService(repository, textGenerationApi).generate(input);

        assertThat(result.description()).contains("**业务语义**", "\n- 员工", "部门");
        ArgumentCaptor<AiTextGenerationRequest> requestCaptor = ArgumentCaptor.forClass(AiTextGenerationRequest.class);
        verify(textGenerationApi).generate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getUserPrompt())
                .contains("employee_contract", "employeeId", "contract_department", "department")
                .doesNotContain("虚拟表", "虚拟字段", "虚拟实体")
                .doesNotContain("physical_employee_contract", "sourceKey", "绑定编码");
        assertThat(requestCaptor.getValue().getSystemPrompt())
                .contains("知识库", "语义", "Markdown", "不得编造", "不提及物理表")
                .doesNotContain("虚拟表", "虚拟字段", "虚拟实体", "虚拟对象");
        verify(repository, never()).bindings(anyLong());
    }

    private VirtualEntityEntity entity(Long id, String code, String name) {
        VirtualEntityEntity entity = new VirtualEntityEntity();
        entity.setId(id);
        entity.setEntityCode(code);
        entity.setEntityName(name);
        entity.setEnabled(true);
        return entity;
    }

    private VirtualFieldEntity field(Long id, Long entityId, String code, String name, LogicalType type) {
        VirtualFieldEntity field = new VirtualFieldEntity();
        field.setId(id);
        field.setEntityId(entityId);
        field.setFieldCode(code);
        field.setFieldName(name);
        field.setLogicalType(type);
        field.setNullable(false);
        field.setEnabled(true);
        return field;
    }
}
