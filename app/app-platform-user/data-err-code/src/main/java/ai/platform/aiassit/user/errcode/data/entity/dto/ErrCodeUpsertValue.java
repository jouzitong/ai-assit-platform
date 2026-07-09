package ai.platform.aiassit.user.errcode.data.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrCodeUpsertValue {

    private String locale;

    private String messageTemplate;

    private String description;
}
