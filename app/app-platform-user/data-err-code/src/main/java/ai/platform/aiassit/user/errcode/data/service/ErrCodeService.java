package ai.platform.aiassit.user.errcode.data.service;

import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeDTO;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeResolveDTO;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeUpsertRequest;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeUpsertResultDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ErrCodeService extends IMapperService<ErrCodeDTO> {

    ErrCodeResolveDTO resolve(Integer code, String locale);

    ErrCodeUpsertResultDTO upsertJson(List<ErrCodeUpsertRequest> documents);

    ErrCodeUpsertResultDTO importJsonFile(MultipartFile file) throws IOException;

    List<ErrCodeUpsertRequest> exportJson();
}
