package ai.platform.aiassit.chat.agent.control.data.entity.dto.control;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Machine-readable validation result persisted with each draft. */
@Data
public class ValidationReportDTO {

    private boolean valid;
    private boolean compatible;
    private List<String> errors = new ArrayList<>();
    private List<Issue> issues = new ArrayList<>();
    private List<Issue> warnings = new ArrayList<>();
    private String message;

    public void error(String message) {
        errors.add(message);
        issues.add(new Issue(null, null, message, "ERROR"));
        valid = false;
    }

    public void warn(String message) {
        warnings.add(new Issue(null, null, message, "WARNING"));
    }

    public void finish() {
        valid = errors.isEmpty();
        compatible = valid;
        message = valid ? "validation passed" : errors.get(0);
    }

    public record Issue(String code, String path, String message, String severity) {
    }
}
