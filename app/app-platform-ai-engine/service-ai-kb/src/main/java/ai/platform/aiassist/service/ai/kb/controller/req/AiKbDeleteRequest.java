package ai.platform.aiassist.service.ai.kb.controller.req;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiKbDeleteRequest implements Serializable {

    private String kbCode;

    private List<String> documentCodes = new ArrayList<>();
}
