package ai.platform.aiassit.db.engine.api;

import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaDeleteRequest;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaQueryRequest;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "dbEngine",
        contextId = "platformDbTableFieldMetaClient",
        path = "/dbEngine/internal/v1/access/field"
)
public interface DbTableFieldMetaApi {

    @PostMapping("/list")
    @IgnoredResultWrapper
    List<DbTableFieldMetaDTO> list(@RequestBody DbTableFieldMetaQueryRequest request);

    @PostMapping("/get")
    @IgnoredResultWrapper
    DbTableFieldMetaDTO get(@RequestBody DbTableFieldMetaQueryRequest request);

    @PostMapping("/create")
    @IgnoredResultWrapper
    DbTableFieldMetaDTO create(@RequestBody DbTableFieldMetaDTO dto);

    @PostMapping("/update")
    @IgnoredResultWrapper
    DbTableFieldMetaDTO update(@RequestBody DbTableFieldMetaDTO dto);

    @PostMapping("/delete")
    @IgnoredResultWrapper
    Boolean delete(@RequestBody DbTableFieldMetaDeleteRequest request);
}
