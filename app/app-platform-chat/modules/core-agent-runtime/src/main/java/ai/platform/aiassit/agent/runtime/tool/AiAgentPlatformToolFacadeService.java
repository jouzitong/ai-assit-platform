package ai.platform.aiassit.agent.runtime.tool;

import ai.platform.aiassit.db.engine.api.DataPreviewApi;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.web.vo.R;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticated Chat boundary for platform Tools implemented by dbEngine and Render.
 *
 * <p>Calls stay synchronous so Athena's Feign interceptor can propagate the incoming
 * authorization and trace headers to the downstream service.</p>
 */
@Service
@Slf4j
public class AiAgentPlatformToolFacadeService {

    static final String AGENT_CREDENTIAL_PURPOSE = "AI_AGENT_CHILD_PROCESS";
    static final String DATA_PREVIEW_TOOL = "data_preview_query_tool";

    private static final int MAX_PREVIEW_ROWS = 100;
    private static final int MAX_MEASURES = 20;
    private static final int MAX_DIMENSIONS = 20;
    private static final int MAX_FILTERS = 50;
    private static final int MAX_SORTS = 10;
    private static final int MAX_FILTER_VALUES = 100;
    private static final Pattern RUN_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]{0,63}(?:\\.[A-Za-z_][A-Za-z0-9_]{0,63})?"
    );
    private static final Pattern SOURCE_REVISION_PATTERN = Pattern.compile("^(?:virtual-model/)?v([1-9][0-9]*)$");
    private static final Set<String> AGGREGATIONS = Set.of("COUNT", "SUM", "MIN", "MAX", "AVG");
    private static final Set<String> FILTER_OPERATORS = Set.of(
            "EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN",
            "IS_NULL", "IS_NOT_NULL", "LIKE", "STARTS_WITH", "ENDS_WITH"
    );

    private final DataPreviewApi dataPreviewApi;

    public AiAgentPlatformToolFacadeService(DataPreviewApi dataPreviewApi) {
        this.dataPreviewApi = dataPreviewApi;
    }

    public DataPreviewQueryResponse queryDataPreview(String runId,
                                                     String traceId,
                                                     DataPreviewQueryRequest request) {
        long startedAt = System.currentTimeMillis();
        InvocationContext context = null;
        try {
            context = requireInvocationContext(runId, traceId, DATA_PREVIEW_TOOL);
            validateDataPreviewRequest(request);
            DataPreviewQueryResponse response = requireDownstreamData(
                    dataPreviewApi.query(request),
                    DATA_PREVIEW_TOOL
            );
            validateDataPreviewResponse(request, response);
            auditSuccess(context, DATA_PREVIEW_TOOL, "dbEngine", startedAt,
                    response.getRecords().size(), request.getModel());
            return response;
        } catch (BizException exception) {
            auditBizException(context, runId, traceId, DATA_PREVIEW_TOOL,
                    "dbEngine", startedAt, exception);
            throw exception;
        } catch (RuntimeException exception) {
            auditFailed(context, runId, traceId, DATA_PREVIEW_TOOL, "dbEngine", startedAt, exception);
            throw new BizException(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED, exception, DATA_PREVIEW_TOOL);
        }
    }

    private InvocationContext requireInvocationContext(String runId, String traceId, String toolCode) {
        Object current = SystemContext.getUserContext();
        log.debug("requireInvocationContext: {}", current);
        if (!(current instanceof UserContext userContext)
                || userContext.subject() == null
                || userContext.subject().userId() == null) {
            throw BizException.ofStatus(
                    AiChatBizCodeConstant.TOOL_PERMISSION_DENIED,
                    HttpStatus.UNAUTHORIZED.value(),
                    toolCode
            );
        }
        Map<String, Object> attributes = userContext.attributes();
        String purpose = text(attributes == null ? null : attributes.get("credentialPurpose"));
        if (!AGENT_CREDENTIAL_PURPOSE.equals(purpose)) {
            throw BizException.ofStatus(
                    AiChatBizCodeConstant.TOOL_PERMISSION_DENIED,
                    HttpStatus.FORBIDDEN.value(),
                    toolCode
            );
        }
        String normalizedRunId = requireIdentifier(runId, "X-Agent-Run-Id", RUN_ID_PATTERN, true);
        String normalizedTraceId = requireIdentifier(traceId, "X-Trace-Id", TRACE_ID_PATTERN, false);
        String tokenRunId = text(attributes == null ? null : attributes.get("agentRunId"));
        if (!StringUtils.hasText(tokenRunId) || !normalizedRunId.equals(tokenRunId)) {
            throw BizException.ofStatus(
                    AiChatBizCodeConstant.TOOL_PERMISSION_DENIED,
                    HttpStatus.FORBIDDEN.value(),
                    toolCode
            );
        }
        return new InvocationContext(
                userContext.subject().userId(),
                normalizedRunId,
                normalizedTraceId
        );
    }

    private void validateDataPreviewRequest(DataPreviewQueryRequest request) {
        if (request == null) {
            throw invalidInput("data preview request is required");
        }
        requirePattern(request.getModel(), MODEL_PATTERN, "model");
        Matcher sourceRevision = requirePattern(request.getSourceRevision(), SOURCE_REVISION_PATTERN, "sourceRevision");
        long revisionVersion = parseRevisionVersion(sourceRevision);
        if (request.getCatalogVersion() != null
                && (request.getCatalogVersion() < 1 || request.getCatalogVersion() != revisionVersion)) {
            throw invalidInput("catalogVersion must match sourceRevision");
        }
        requireSize(request.getMeasures(), MAX_MEASURES, "measures");
        requireSize(request.getDimensions(), MAX_DIMENSIONS, "dimensions");
        requireSize(request.getFilters(), MAX_FILTERS, "filters");
        requireSize(request.getSorts(), MAX_SORTS, "sorts");
        if (safe(request.getMeasures()).isEmpty() && safe(request.getDimensions()).isEmpty()) {
            throw invalidInput("at least one measure or dimension is required");
        }
        if (request.getLimit() != null && (request.getLimit() < 1 || request.getLimit() > MAX_PREVIEW_ROWS)) {
            throw invalidInput("limit must be between 1 and 100");
        }
        for (DataPreviewQueryRequest.Measure measure : safe(request.getMeasures())) {
            if (measure == null) {
                throw invalidInput("measure must not be null");
            }
            requirePattern(measure.getField(), FIELD_PATTERN, "measure.field");
            if (!AGGREGATIONS.contains(normalizeEnum(measure.getAggregation()))) {
                throw invalidInput("measure.aggregation is unsupported");
            }
        }
        for (DataPreviewQueryRequest.Dimension dimension : safe(request.getDimensions())) {
            if (dimension == null) {
                throw invalidInput("dimension must not be null");
            }
            requirePattern(dimension.getField(), FIELD_PATTERN, "dimension.field");
        }
        for (DataPreviewQueryRequest.Filter filter : safe(request.getFilters())) {
            if (filter == null) {
                throw invalidInput("filter must not be null");
            }
            requirePattern(filter.getField(), FIELD_PATTERN, "filter.field");
            String operator = normalizeEnum(filter.getOperator());
            if (!FILTER_OPERATORS.contains(operator)) {
                throw invalidInput("filter.operator is unsupported");
            }
            requireSize(filter.getValues(), MAX_FILTER_VALUES, "filter.values");
            validateScalar(filter.getValue(), "filter.value");
            for (Object value : safe(filter.getValues())) {
                validateScalar(value, "filter.values");
            }
        }
        for (DataPreviewQueryRequest.Sort sort : safe(request.getSorts())) {
            if (sort == null) {
                throw invalidInput("sort must not be null");
            }
            requirePattern(sort.getField(), FIELD_PATTERN, "sort.field");
            String direction = normalizeEnum(sort.getDirection());
            if (StringUtils.hasText(direction) && !Set.of("ASC", "DESC").contains(direction)) {
                throw invalidInput("sort.direction is unsupported");
            }
        }
        if (request.getTimeRange() != null) {
            requirePattern(request.getTimeRange().getField(), FIELD_PATTERN, "timeRange.field");
            validateScalar(request.getTimeRange().getStart(), "timeRange.start");
            validateScalar(request.getTimeRange().getEnd(), "timeRange.end");
        }
    }

    private void validateDataPreviewResponse(DataPreviewQueryRequest request,
                                             DataPreviewQueryResponse response) {
        if (response == null
                || !request.getModel().trim().equals(response.getModel())
                || !canonicalRevision(request.getSourceRevision()).equals(response.getSourceRevision())
                || response.getCatalogVersion() == null
                || response.getCatalogVersion() < 1
                || response.getCatalogVersion() != revisionVersion(request.getSourceRevision())
                || response.getColumns() == null
                || response.getRecords() == null
                || response.getTotal() == null
                || response.getTruncated() == null) {
            throw downstreamInvalid(DATA_PREVIEW_TOOL);
        }
        String expectedQueryType = safe(request.getMeasures()).isEmpty() ? "LIST" : "AGGREGATE";
        if (!expectedQueryType.equals(response.getQueryType())) {
            throw downstreamInvalid(DATA_PREVIEW_TOOL);
        }
        int requestedLimit = request.getLimit() == null ? 20 : request.getLimit();
        if (response.getRecords().size() > Math.min(requestedLimit, MAX_PREVIEW_ROWS)
                || response.getRecords().stream().anyMatch(row -> row == null)
                || response.getTotal() < response.getRecords().size()) {
            throw downstreamInvalid(DATA_PREVIEW_TOOL);
        }
        Set<String> columnKeys = new HashSet<>();
        for (DataPreviewQueryResponse.Column column : response.getColumns()) {
            if (column == null
                    || !StringUtils.hasText(column.getKey())
                    || !StringUtils.hasText(column.getField())
                    || !columnKeys.add(column.getKey())) {
                throw downstreamInvalid(DATA_PREVIEW_TOOL);
            }
        }
    }

    private void validateScalar(Object value, String label) {
        if (value == null || value instanceof Boolean) {
            return;
        }
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (Double.isFinite(numeric)) {
                return;
            }
        } else if (value instanceof CharSequence sequence && sequence.length() <= 4_096) {
            return;
        }
        throw invalidInput(label + " must be a bounded scalar value");
    }

    private String requireIdentifier(String value,
                                     String label,
                                     Pattern pattern,
                                     boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw invalidInput(label + " is required");
            }
            return null;
        }
        String normalized = value.trim();
        if (!pattern.matcher(normalized).matches()) {
            throw invalidInput(label + " is invalid");
        }
        return normalized;
    }

    private Matcher requirePattern(String value, Pattern pattern, String label) {
        if (!StringUtils.hasText(value)) {
            throw invalidInput(label + " is required");
        }
        Matcher matcher = pattern.matcher(value.trim());
        if (!matcher.matches()) {
            throw invalidInput(label + " is invalid");
        }
        return matcher;
    }

    private void requireSize(List<?> values, int maxSize, String label) {
        if (values != null && values.size() > maxSize) {
            throw invalidInput(label + " exceeds the maximum size of " + maxSize);
        }
    }

    private long parseRevisionVersion(Matcher matcher) {
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw invalidInput("sourceRevision version is out of range");
        }
    }

    private long revisionVersion(String value) {
        Matcher matcher = SOURCE_REVISION_PATTERN.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) {
            throw downstreamInvalid(DATA_PREVIEW_TOOL);
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw downstreamInvalid(DATA_PREVIEW_TOOL);
        }
    }

    private String canonicalRevision(String value) {
        String revision = value.trim();
        return revision.startsWith("virtual-model/") ? revision : "virtual-model/" + revision;
    }

    private String normalizeEnum(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private BizException invalidInput(String detail) {
        return BizException.illegalParam(AiChatBizCodeConstant.INVALID_TOOL_INPUT, detail);
    }

    private BizException downstreamInvalid(String toolCode) {
        return BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                toolCode + " returned an invalid response");
    }

    private <T> T requireDownstreamData(R<T> response, String toolCode) {
        if (response == null || !response.isOk() || response.getData() == null) {
            throw downstreamInvalid(toolCode);
        }
        return response.getData();
    }

    private void auditSuccess(InvocationContext context,
                              String toolCode,
                              String targetService,
                              long startedAt,
                              int resultCount,
                              String resource) {
        log.info("Agent platform Tool invocation completed: toolCode={}, targetService={}, userId={}, "
                        + "runId={}, traceId={}, resource={}, resultCount={}, durationMs={}, result=success",
                toolCode, targetService, context.userId(), context.runId(), context.traceId(),
                resource, resultCount, elapsed(startedAt));
    }

    private void auditRejected(InvocationContext context,
                               String runId,
                               String traceId,
                               String toolCode,
                               String targetService,
                               long startedAt,
                               BizException exception) {
        log.warn("Agent platform Tool invocation rejected: toolCode={}, targetService={}, userId={}, "
                        + "runId={}, traceId={}, durationMs={}, result=rejected, errorCode={}",
                toolCode, targetService, context == null ? currentUserId() : context.userId(),
                context == null ? safeAuditValue(runId) : context.runId(),
                context == null ? safeAuditValue(traceId) : context.traceId(),
                elapsed(startedAt), exception.getCode());
    }

    private void auditBizException(InvocationContext context,
                                   String runId,
                                   String traceId,
                                   String toolCode,
                                   String targetService,
                                   long startedAt,
                                   BizException exception) {
        if (AiChatBizCodeConstant.TOOL_INVOCATION_FAILED.equals(exception.getCode())) {
            log.error("Agent platform Tool invocation failed: toolCode={}, targetService={}, userId={}, "
                            + "runId={}, traceId={}, durationMs={}, result=failed, errorCode={}",
                    toolCode, targetService, context == null ? currentUserId() : context.userId(),
                    context == null ? safeAuditValue(runId) : context.runId(),
                    context == null ? safeAuditValue(traceId) : context.traceId(),
                    elapsed(startedAt), exception.getCode());
            return;
        }
        auditRejected(context, runId, traceId, toolCode, targetService, startedAt, exception);
    }

    private void auditFailed(InvocationContext context,
                             String runId,
                             String traceId,
                             String toolCode,
                             String targetService,
                             long startedAt,
                             RuntimeException exception) {
        log.error("Agent platform Tool invocation failed: toolCode={}, targetService={}, userId={}, "
                        + "runId={}, traceId={}, durationMs={}, result=failed, errorType={}",
                toolCode, targetService, context == null ? currentUserId() : context.userId(),
                context == null ? safeAuditValue(runId) : context.runId(),
                context == null ? safeAuditValue(traceId) : context.traceId(),
                elapsed(startedAt), exception.getClass().getSimpleName(), exception);
    }

    private Long currentUserId() {
        Object current = SystemContext.getUserContext();
        return current instanceof UserContext userContext && userContext.subject() != null
                ? userContext.subject().userId() : null;
    }

    private String safeAuditValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("[\\p{Cntrl}\\r\\n]", "_");
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private long elapsed(long startedAt) {
        return Math.max(0L, System.currentTimeMillis() - startedAt);
    }

    private record InvocationContext(Long userId, String runId, String traceId) {
    }
}
