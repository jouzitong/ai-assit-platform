package ai.platform.aiassit.knowledge.manage.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

/** 知识库发布/同步任务查询条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbPublishTaskQueryRequest extends BaseRequest {

    private String taskCode;
}
