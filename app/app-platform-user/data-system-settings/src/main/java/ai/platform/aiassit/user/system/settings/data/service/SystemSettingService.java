package ai.platform.aiassit.user.system.settings.data.service;

import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingDTO;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingImportResultDTO;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingTransferDocument;
import ai.platform.aiassit.user.system.settings.data.entity.req.SystemSettingExportRequest;
import org.athena.framework.data.jdbc.serivce.IMapperService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface SystemSettingService extends IMapperService<SystemSettingDTO> {

    String queryValueByKey(String key);

    SystemSettingImportResultDTO importJsonFile(MultipartFile file) throws IOException;

    List<SystemSettingTransferDocument> exportJson(SystemSettingExportRequest request);
}
