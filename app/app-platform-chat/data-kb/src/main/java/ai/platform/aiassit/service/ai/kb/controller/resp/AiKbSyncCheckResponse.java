package ai.platform.aiassit.service.ai.kb.controller.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiKbSyncCheckResponse implements Serializable {

    private Integer totalCount = 0;

    private Integer matchedCount = 0;

    private Integer changedCount = 0;

    private Integer notSyncedCount = 0;

    private Integer missingSnapshotCount = 0;

    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {

        private String kbCode;

        private String documentCode;

        private String documentName;

        private String providerDocumentId;

        private String status;

        private String message;
    }
}
