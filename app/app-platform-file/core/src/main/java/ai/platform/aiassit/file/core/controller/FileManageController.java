package ai.platform.aiassit.file.core.controller;

import ai.platform.aiassit.file.api.dto.FileObjectRequest;
import ai.platform.aiassit.file.api.dto.FilePresignRequest;
import ai.platform.aiassit.file.api.dto.FilePresignedUrlResponse;
import ai.platform.aiassit.file.api.dto.FileStoredObjectResponse;
import ai.platform.aiassit.file.api.dto.FileUploadRequest;
import ai.platform.aiassit.file.core.service.FileStorageFacade;
import org.athena.framework.web.vo.R;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.io.ByteArrayInputStream;

/**
 * 文件管理控制器。
 *
 * <p>对外提供文件上传、下载、删除、状态查询、存在性检查以及预签名 URL 生成等接口。
 * 该控制器只负责接收 Web 请求、组装请求参数，并将具体文件存储操作委托给 {@link FileStorageFacade} 处理。</p>
 */

@RestController
@RequestMapping("/api/v1/file")
public class FileManageController {

    private final FileStorageFacade fileStorageFacade;

    /**
     * 构造文件管理控制器。
     *
     * @param fileStorageFacade 文件存储门面服务，用于执行具体的文件存储操作
     */
    public FileManageController(FileStorageFacade fileStorageFacade) {
        this.fileStorageFacade = fileStorageFacade;
    }

    /**
     * 上传文件。
     *
     * <p>支持通过 multipart/form-data 上传文件，可选指定存储桶和对象 Key。
     * 如果未指定对象 Key，则默认使用上传文件的原始文件名。</p>
     *
     * @param file      待上传的文件
     * @param bucket    存储桶名称，为空时使用默认存储桶
     * @param objectKey 文件对象 Key，为空时使用原始文件名
     * @return 文件存储结果信息
     * @throws IOException 读取上传文件内容失败时抛出
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<FileStoredObjectResponse> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "bucket", required = false) String bucket,
                                              @RequestParam(value = "objectKey", required = false) String objectKey)
            throws IOException {
        FileUploadRequest request = new FileUploadRequest();
        request.setBucket(bucket);
        request.setObjectKey(StringUtils.hasText(objectKey) ? objectKey : file.getOriginalFilename());
        request.setContentType(file.getContentType());
        request.setContent(file.getBytes());
        return R.ok(fileStorageFacade.upload(request));
    }

    /**
     * 下载文件。
     *
     * <p>根据对象 Key 和可选存储桶名称读取文件内容，并以附件形式返回给客户端。</p>
     *
     * @param objectKey 文件对象 Key
     * @param bucket    存储桶名称，为空时使用默认存储桶
     * @return 文件下载响应体
     * @throws IOException 下载文件失败时抛出
     */
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestParam("objectKey") String objectKey,
                                                        @RequestParam(value = "bucket", required = false) String bucket)
            throws IOException {
        var response = fileStorageFacade.download(toObjectRequest(bucket, objectKey));
        byte[] content = response.getContent() == null ? new byte[0] : response.getContent();
        InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(objectKey).build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(content.length)
                .contentType(StringUtils.hasText(response.getContentType())
                        ? MediaType.parseMediaType(response.getContentType())
                        : MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 删除文件。
     *
     * @param objectKey 文件对象 Key
     * @param bucket    存储桶名称，为空时使用默认存储桶
     * @return 是否删除成功
     */
    @DeleteMapping("/delete")
    public R<Boolean> delete(@RequestParam("objectKey") String objectKey,
                             @RequestParam(value = "bucket", required = false) String bucket) {
        return R.ok(fileStorageFacade.delete(toObjectRequest(bucket, objectKey)));
    }

    /**
     * 查询文件元信息。
     *
     * <p>用于获取文件对象的存储信息，例如对象 Key、存储桶、大小、内容类型等。</p>
     *
     * @param objectKey 文件对象 Key
     * @param bucket    存储桶名称，为空时使用默认存储桶
     * @return 文件存储对象信息
     */
    @GetMapping("/stat")
    public R<FileStoredObjectResponse> stat(@RequestParam("objectKey") String objectKey,
                                            @RequestParam(value = "bucket", required = false) String bucket) {
        return R.ok(fileStorageFacade.stat(toObjectRequest(bucket, objectKey)));
    }

    /**
     * 检查文件是否存在。
     *
     * @param objectKey 文件对象 Key
     * @param bucket    存储桶名称，为空时使用默认存储桶
     * @return 文件是否存在
     */
    @GetMapping("/exists")
    public R<Boolean> exists(@RequestParam("objectKey") String objectKey,
                             @RequestParam(value = "bucket", required = false) String bucket) {
        return R.ok(fileStorageFacade.exists(toObjectRequest(bucket, objectKey)));
    }

    /**
     * 生成文件下载预签名 URL。
     *
     * <p>客户端可在有效期内通过该 URL 直接下载指定文件。</p>
     *
     * @param objectKey     文件对象 Key
     * @param bucket        存储桶名称，为空时使用默认存储桶
     * @param expireSeconds URL 有效期，单位为秒；为空时使用默认有效期
     * @return 预签名 URL 信息
     */
    @GetMapping("/presign/get")
    public R<FilePresignedUrlResponse> presignGet(@RequestParam("objectKey") String objectKey,
                                                  @RequestParam(value = "bucket", required = false) String bucket,
                                                  @RequestParam(value = "expireSeconds", required = false) Long expireSeconds) {
        return R.ok(fileStorageFacade.presignGet(toPresignRequest(bucket, objectKey, expireSeconds)));
    }

    /**
     * 生成文件上传预签名 URL。
     *
     * <p>客户端可在有效期内通过该 URL 直接上传文件到指定对象 Key。</p>
     *
     * @param objectKey     文件对象 Key
     * @param bucket        存储桶名称，为空时使用默认存储桶
     * @param expireSeconds URL 有效期，单位为秒；为空时使用默认有效期
     * @return 预签名 URL 信息
     */
    @GetMapping("/presign/put")
    public R<FilePresignedUrlResponse> presignPut(@RequestParam("objectKey") String objectKey,
                                                  @RequestParam(value = "bucket", required = false) String bucket,
                                                  @RequestParam(value = "expireSeconds", required = false) Long expireSeconds) {
        return R.ok(fileStorageFacade.presignPut(toPresignRequest(bucket, objectKey, expireSeconds)));
    }

    /**
     * 构建文件对象请求参数。
     *
     * @param bucket    存储桶名称
     * @param objectKey 文件对象 Key
     * @return 文件对象请求参数
     */
    private FileObjectRequest toObjectRequest(String bucket, String objectKey) {
        FileObjectRequest request = new FileObjectRequest();
        request.setBucket(bucket);
        request.setObjectKey(objectKey);
        return request;
    }

    /**
     * 构建预签名 URL 请求参数。
     *
     * @param bucket        存储桶名称
     * @param objectKey     文件对象 Key
     * @param expireSeconds URL 有效期，单位为秒
     * @return 预签名 URL 请求参数
     */
    private FilePresignRequest toPresignRequest(String bucket, String objectKey, Long expireSeconds) {
        FilePresignRequest request = new FilePresignRequest();
        request.setBucket(bucket);
        request.setObjectKey(objectKey);
        request.setExpireSeconds(expireSeconds);
        return request;
    }
}
