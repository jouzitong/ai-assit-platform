package ai.platform.aiassit.data.virtualization.api.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.ConsistencyLevel;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VirtualQueryRequest {
    private String entityCode;
    private Long catalogVersion;
    private QueryType queryType = QueryType.LIST;
    private List<String> fields = new ArrayList<>();
    private FilterNode filter;
    private List<String> relationCodes = new ArrayList<>();
    private List<VirtualRelationRequest> relations = new ArrayList<>();
    private List<VirtualAggregate> aggregates = new ArrayList<>();
    private List<String> groupBy = new ArrayList<>();
    private List<VirtualGroupBy> groupings = new ArrayList<>();
    private FilterNode having;
    private List<VirtualSort> sorts = new ArrayList<>();
    private VirtualPage page = new VirtualPage();
    private Boolean exactTotal = false;
    private String traceLabel;
    private ConsistencyLevel consistency = ConsistencyLevel.STRONG;
    private QueryHints hints = new QueryHints();
}
