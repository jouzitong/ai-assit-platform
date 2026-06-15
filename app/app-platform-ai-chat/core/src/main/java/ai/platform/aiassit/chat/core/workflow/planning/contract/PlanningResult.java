package ai.platform.aiassit.chat.core.workflow.planning.contract;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询规划结果。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Data
public class PlanningResult {

    private String sessionTitle;

    private String userGoal;

    private String analysisSummary;

    private List<String> analysisDimensions = new ArrayList<>();

    private List<String> requiredContext = new ArrayList<>();

    private List<String> sqlFocus = new ArrayList<>();

    private List<String> risks = new ArrayList<>();

    private Boolean needClarification;
}
