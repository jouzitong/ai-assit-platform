package ai.platform.aiassit.user.errcode.data.entity.dto;

import lombok.Data;

@Data
public class ErrCodeUpsertResultDTO {

    private int received;

    private int errCodeUpserted;

    private int i18nUpserted;

    private int skipped;
}
