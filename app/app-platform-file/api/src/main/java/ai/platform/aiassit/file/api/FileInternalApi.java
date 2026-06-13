package ai.platform.aiassit.file.api;

import ai.platform.aiassit.file.api.dto.FileDownloadResponse;
import ai.platform.aiassit.file.api.dto.FileObjectRequest;
import ai.platform.aiassit.file.api.dto.FilePresignRequest;
import ai.platform.aiassit.file.api.dto.FilePresignedUrlResponse;
import ai.platform.aiassit.file.api.dto.FileStoredObjectResponse;
import ai.platform.aiassit.file.api.dto.FileUploadRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "file",
        contextId = "platformFileClient",
        path = "/file"
)
public interface FileInternalApi {

    @PostMapping("/internal/v1/file/upload")
    FileStoredObjectResponse upload(@RequestBody FileUploadRequest request);

    @PostMapping("/internal/v1/file/download")
    FileDownloadResponse download(@RequestBody FileObjectRequest request);

    @PostMapping("/internal/v1/file/stat")
    FileStoredObjectResponse stat(@RequestBody FileObjectRequest request);

    @PostMapping("/internal/v1/file/exists")
    Boolean exists(@RequestBody FileObjectRequest request);

    @PostMapping("/internal/v1/file/delete")
    Boolean delete(@RequestBody FileObjectRequest request);

    @PostMapping("/internal/v1/file/presign/get")
    FilePresignedUrlResponse presignGet(@RequestBody FilePresignRequest request);

    @PostMapping("/internal/v1/file/presign/put")
    FilePresignedUrlResponse presignPut(@RequestBody FilePresignRequest request);
}
