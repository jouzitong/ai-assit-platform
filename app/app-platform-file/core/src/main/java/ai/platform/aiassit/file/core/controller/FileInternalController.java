package ai.platform.aiassit.file.core.controller;

import ai.platform.aiassit.file.api.FileInternalApi;
import ai.platform.aiassit.file.api.dto.FileDownloadResponse;
import ai.platform.aiassit.file.api.dto.FileObjectRequest;
import ai.platform.aiassit.file.api.dto.FilePresignRequest;
import ai.platform.aiassit.file.api.dto.FilePresignedUrlResponse;
import ai.platform.aiassit.file.api.dto.FileStoredObjectResponse;
import ai.platform.aiassit.file.api.dto.FileUploadRequest;
import ai.platform.aiassit.file.core.service.FileStorageFacade;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileInternalController implements FileInternalApi {

    private final FileStorageFacade fileStorageFacade;

    public FileInternalController(FileStorageFacade fileStorageFacade) {
        this.fileStorageFacade = fileStorageFacade;
    }

    @Override
    public FileStoredObjectResponse upload(FileUploadRequest request) {
        return fileStorageFacade.upload(request);
    }

    @Override
    public FileDownloadResponse download(FileObjectRequest request) {
        return fileStorageFacade.download(request);
    }

    @Override
    public FileStoredObjectResponse stat(FileObjectRequest request) {
        return fileStorageFacade.stat(request);
    }

    @Override
    public Boolean exists(FileObjectRequest request) {
        return fileStorageFacade.exists(request);
    }

    @Override
    public Boolean delete(FileObjectRequest request) {
        return fileStorageFacade.delete(request);
    }

    @Override
    public FilePresignedUrlResponse presignGet(FilePresignRequest request) {
        return fileStorageFacade.presignGet(request);
    }

    @Override
    public FilePresignedUrlResponse presignPut(FilePresignRequest request) {
        return fileStorageFacade.presignPut(request);
    }
}
