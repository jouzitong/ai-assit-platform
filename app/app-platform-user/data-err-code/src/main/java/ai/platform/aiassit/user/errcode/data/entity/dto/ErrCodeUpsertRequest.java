package ai.platform.aiassit.user.errcode.data.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrCodeUpsertRequest {

    private Integer code;

    private Integer httpStatus;

    private String description;

    private String tags;

    private List<ErrCodeUpsertValue> value;
}
