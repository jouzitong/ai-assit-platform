package ai.platform.aiassit.file.core.service;

import ai.platform.aiassit.file.api.dto.FileDownloadResponse;
import ai.platform.aiassit.file.api.dto.FileObjectRequest;
import ai.platform.aiassit.file.api.dto.FilePresignRequest;
import ai.platform.aiassit.file.api.dto.FilePresignedUrlResponse;
import ai.platform.aiassit.file.api.dto.FileStoredObjectResponse;
import ai.platform.aiassit.file.api.dto.FileUploadRequest;
import org.athena.framework.minio.model.PresignedUrlResult;
import org.athena.framework.minio.model.PutObjectRequest;
import org.athena.framework.minio.model.StoredObject;
import org.athena.framework.minio.service.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

@Service
public class FileStorageFacade {

    private final ObjectStorageService objectStorageService;

    public FileStorageFacade(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    public FileStoredObjectResponse upload(FileUploadRequest request) {
        Assert.notNull(request, "request cannot be null");
        Assert.hasText(request.getObjectKey(), "objectKey cannot be blank");
        Assert.notNull(request.getContent(), "content cannot be null");

        PutObjectRequest putObjectRequest = new PutObjectRequest();
        putObjectRequest.setBucket(request.getBucket());
        putObjectRequest.setObjectKey(request.getObjectKey());
        putObjectRequest.setContentType(request.getContentType());
        putObjectRequest.setBytes(request.getContent());
        putObjectRequest.setSize((long) request.getContent().length);
        putObjectRequest.setMetadata(request.getMetadata());
        return toStoredObjectResponse(objectStorageService.putObject(putObjectRequest));
    }

    public FileDownloadResponse download(FileObjectRequest request) {
        Assert.notNull(request, "request cannot be null");
        Assert.hasText(request.getObjectKey(), "objectKey cannot be blank");

        StoredObject storedObject = objectStorageService.statObject(request.getBucket(), request.getObjectKey());
        try (InputStream inputStream = objectStorageService.getObject(request.getBucket(), request.getObjectKey())) {
            FileDownloadResponse response = new FileDownloadResponse();
            response.setBucket(storedObject.getBucket());
            response.setObjectKey(storedObject.getObjectKey());
            response.setContentType(storedObject.getContentType());
            response.setSize(storedObject.getSize());
            response.setContent(inputStream.readAllBytes());
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("download file failed", e);
        }
    }

    public FileStoredObjectResponse stat(FileObjectRequest request) {
        Assert.notNull(request, "request cannot be null");
        Assert.hasText(request.getObjectKey(), "objectKey cannot be blank");
        return toStoredObjectResponse(objectStorageService.statObject(request.getBucket(), request.getObjectKey()));
    }

    public boolean exists(FileObjectRequest request) {
        Assert.notNull(request, "request cannot be null");
        Assert.hasText(request.getObjectKey(), "objectKey cannot be blank");
        return objectStorageService.exists(request.getBucket(), request.getObjectKey());
    }

    public boolean delete(FileObjectRequest request) {
        Assert.notNull(request, "request cannot be null");
        Assert.hasText(request.getObjectKey(), "objectKey cannot be blank");
        objectStorageService.removeObject(request.getBucket(), request.getObjectKey());
        return true;
    }

    public FilePresignedUrlResponse presignGet(FilePresignRequest request) {
        Assert.notNull(request, "request cannot be null");
        Assert.hasText(request.getObjectKey(), "objectKey cannot be blank");
        return toPresignedUrlResponse(objectStorageService.getPresignedGetUrl(
                request.getBucket(),
                request.getObjectKey(),
                toExpiry(request.getExpireSeconds())
        ));
    }

    public FilePresignedUrlResponse presignPut(FilePresignRequest request) {
        Assert.notNull(request, "request cannot be null");
        Assert.hasText(request.getObjectKey(), "objectKey cannot be blank");
        return toPresignedUrlResponse(objectStorageService.getPresignedPutUrl(
                request.getBucket(),
                request.getObjectKey(),
                toExpiry(request.getExpireSeconds())
        ));
    }

    private Duration toExpiry(Long expireSeconds) {
        if (expireSeconds == null || expireSeconds <= 0) {
            return null;
        }
        return Duration.ofSeconds(expireSeconds);
    }

    private FileStoredObjectResponse toStoredObjectResponse(StoredObject storedObject) {
        FileStoredObjectResponse response = new FileStoredObjectResponse();
        response.setBucket(storedObject.getBucket());
        response.setObjectKey(storedObject.getObjectKey());
        response.setSize(storedObject.getSize());
        response.setEtag(storedObject.getEtag());
        response.setLastModified(storedObject.getLastModified());
        response.setContentType(storedObject.getContentType());
        return response;
    }

    private FilePresignedUrlResponse toPresignedUrlResponse(PresignedUrlResult result) {
        FilePresignedUrlResponse response = new FilePresignedUrlResponse();
        response.setUrl(result.getUrl());
        response.setMethod(result.getMethod());
        response.setExpireAt(result.getExpireAt());
        return response;
    }
}
