package ai.platform.aiassit.chat.workflow.data.validator;

import ai.platform.aiassit.chat.workflow.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.control.WorkflowControlDTOs;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Validates artifact contracts and deliberately rejects executable Node/edge semantics. */
@Component
public class WorkflowSpecificationValidator {

    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "ERROR");
    private static final Set<String> CHECKER_TYPES = Set.of("JSON_SCHEMA", "TOOL", "AGENT");
    private static final Set<String> EXHAUSTED_ACTIONS = Set.of("INPUT_REQUIRED", "FAILED");

    public ValidationReportDTO validate(WorkflowControlDTOs.Manifest manifest) {
        ValidationReportDTO report = new ValidationReportDTO();
        if (manifest == null) {
            report.error("Workflow manifest is required");
            report.finish();
            return report;
        }
        if (!StringUtils.hasText(manifest.getApiVersion())) {
            report.error("apiVersion is required");
        }
        if (!"ArtifactWorkflow".equals(manifest.getKind())) {
            report.error("kind must be ArtifactWorkflow");
        }
        if (manifest.getMetadata() == null || !StringUtils.hasText(manifest.getMetadata().getCode())) {
            report.error("metadata.code is required");
        }
        validateSpec(manifest.getSpec(), report);
        report.finish();
        return report;
    }

    public ValidationReportDTO validate(WorkflowControlDTOs.Spec specification) {
        ValidationReportDTO report = new ValidationReportDTO();
        validateSpec(specification, report);
        report.finish();
        return report;
    }

    private void validateSpec(WorkflowControlDTOs.Spec specification, ValidationReportDTO report) {
        if (specification == null) {
            report.error("spec is required");
            return;
        }
        Set<String> artifactCodes = new HashSet<>();
        if (specification.getArtifacts() == null || specification.getArtifacts().isEmpty()) {
            report.error("spec.artifacts must contain at least one artifact contract");
        } else {
            for (int index = 0; index < specification.getArtifacts().size(); index++) {
                WorkflowControlDTOs.Artifact item = specification.getArtifacts().get(index);
                if (item == null || !StringUtils.hasText(item.getCode())) {
                    report.error("spec.artifacts[" + index + "].code is required");
                    continue;
                }
                String normalized = item.getCode().trim().toLowerCase(Locale.ROOT);
                if (!artifactCodes.add(normalized)) {
                    report.error("duplicate artifact code: " + item.getCode());
                }
                if (!StringUtils.hasText(item.getArtifactType())) {
                    report.error("artifactType is required: " + item.getCode());
                }
                if (StringUtils.hasText(item.getSchemaRef()) && item.getInlineSchema() != null
                        && !item.getInlineSchema().isEmpty()) {
                    report.error("artifact cannot define both schemaRef and inlineSchema: " + item.getCode());
                }
                if (StringUtils.hasText(item.getTemplateRef()) && StringUtils.hasText(item.getInlineTemplate())) {
                    report.error("artifact cannot define both templateRef and inlineTemplate: " + item.getCode());
                }
            }
        }

        Set<String> checkCodes = new HashSet<>();
        if (specification.getChecks() != null) {
            for (int index = 0; index < specification.getChecks().size(); index++) {
                WorkflowControlDTOs.Check check = specification.getChecks().get(index);
                if (check == null || !StringUtils.hasText(check.getCode())) {
                    report.error("spec.checks[" + index + "].code is required");
                    continue;
                }
                if (!checkCodes.add(check.getCode().trim().toLowerCase(Locale.ROOT))) {
                    report.error("duplicate check code: " + check.getCode());
                }
                String target = normalize(check.getTargetArtifact());
                if (!artifactCodes.contains(target)) {
                    report.error("check targetArtifact does not exist: " + check.getCode());
                }
                String checkerType = upper(check.getCheckerType());
                if (!CHECKER_TYPES.contains(checkerType)) {
                    report.error("checkerType must be JSON_SCHEMA, TOOL or AGENT: " + check.getCode());
                }
                if (("TOOL".equals(checkerType) || "AGENT".equals(checkerType))
                        && !StringUtils.hasText(check.getCheckerRef())) {
                    report.error("checkerRef is required for " + checkerType + ": " + check.getCode());
                }
                String severity = upper(check.getSeverity());
                if (!SEVERITIES.contains(severity)) {
                    report.error("check severity must be INFO, WARNING or ERROR: " + check.getCode());
                }
            }
        }

        WorkflowControlDTOs.RepairPolicy repair = specification.getRepairPolicy();
        if (repair != null) {
            if (repair.getMaxRepairAttempts() != null
                    && (repair.getMaxRepairAttempts() < 0 || repair.getMaxRepairAttempts() > 5)) {
                report.error("repairPolicy.maxRepairAttempts must be between 0 and 5");
            }
            if (!EXHAUSTED_ACTIONS.contains(upper(repair.getOnExhausted()))) {
                report.error("repairPolicy.onExhausted must be INPUT_REQUIRED or FAILED");
            }
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
