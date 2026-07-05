package ai.platform.aiassit.knowledge.manage.req;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiKbSyncCheckRequest implements Serializable {

    private String kbCode;

    private List<String> documentCodes = new ArrayList<>();
}
