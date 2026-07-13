package ai.platform.aiassit.data.virtualization.api.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CommandType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransactionMode;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class VirtualCommandRequest {
    private String entityCode;
    private Long catalogVersion;
    private CommandType commandType;
    private List<Map<String, Object>> records = new ArrayList<>();
    private FilterNode filter;
    private TransactionMode transactionMode = TransactionMode.LOCAL;
    private String idempotencyKey;

    public Map<String, Object> firstRecord() {
        return records == null || records.isEmpty() ? new LinkedHashMap<>() : records.get(0);
    }
}
