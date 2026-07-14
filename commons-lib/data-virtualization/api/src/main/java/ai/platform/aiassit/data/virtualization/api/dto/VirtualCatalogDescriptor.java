package ai.platform.aiassit.data.virtualization.api.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 不包含物理表信息的已发布虚拟目录描述。 */
public record VirtualCatalogDescriptor(
        String entityCode,
        long catalogVersion,
        List<Field> fields,
        List<String> relationCodes,
        List<Relation> relations
) {
    public VirtualCatalogDescriptor {
        fields = fields == null ? List.of() : List.copyOf(fields);
        relationCodes = relationCodes == null ? List.of() : List.copyOf(relationCodes);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    public VirtualCatalogDescriptor(
            String entityCode,
            long catalogVersion,
            List<Field> fields,
            List<String> relationCodes
    ) {
        this(entityCode, catalogVersion, fields, relationCodes, List.of());
    }

    public List<Field> primaryKeys() {
        return fields.stream().filter(Field::enabled).filter(Field::primaryKey).toList();
    }

    public record Field(String code, boolean primaryKey, boolean enabled) {
    }

    /** 已发布关系的虚拟身份与字段映射，不暴露任何物理表信息。 */
    public record Relation(
            String code,
            String targetEntityCode,
            Map<String, String> localToRemoteFields,
            RelationResultMode resultMode
    ) {
        public Relation {
            localToRemoteFields = localToRemoteFields == null
                    ? Map.of() : Map.copyOf(new LinkedHashMap<>(localToRemoteFields));
            resultMode = resultMode == null ? RelationResultMode.OBJECT : resultMode;
        }

        public Relation(String code, String targetEntityCode, Map<String, String> localToRemoteFields) {
            this(code, targetEntityCode, localToRemoteFields, RelationResultMode.OBJECT);
        }
    }
}
