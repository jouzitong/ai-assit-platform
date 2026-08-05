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

/**
 * 平台内部文件存储操作接口。
 *
 * <p>供其他服务通过稳定的内部契约上传、下载、查询、删除文件或生成预签名 URL，具体存储桶和对象处理统一委托文件存储门面。</p>
 */
@RestController
public class FileInternalController implements FileInternalApi {

    private final FileStorageFacade fileStorageFacade;

    public FileInternalController(FileStorageFacade fileStorageFacade) {
        this.fileStorageFacade = fileStorageFacade;
    }

    /**
     * 将文件内容写入指定或默认存储桶。
     *
     * @param request 上传请求体，包含对象标识、内容类型和文件二进制内容
     * @return 已存储对象的元信息，包含存储桶、对象键和大小
     */
    @Override
    public FileStoredObjectResponse upload(FileUploadRequest request) {
        return fileStorageFacade.upload(request);
    }

    /**
     * 读取文件对象的二进制内容和存储元信息。
     *
     * @param request 文件对象请求体，包含存储桶和对象键
     * @return 下载结果，包含内容字节和内容类型等元信息
     */
    @Override
    public FileDownloadResponse download(FileObjectRequest request) {
        return fileStorageFacade.download(request);
    }

    /**
     * 查询文件对象的元信息但不读取其内容。
     *
     * @param request 文件对象请求体，包含存储桶和对象键
     * @return 存储对象元信息，包含大小、内容类型和对象定位信息
     */
    @Override
    public FileStoredObjectResponse stat(FileObjectRequest request) {
        return fileStorageFacade.stat(request);
    }

    /**
     * 判断文件对象是否存在。
     *
     * @param request 文件对象请求体，包含存储桶和对象键
     * @return 对象存在时为 {@code true}，否则为 {@code false}
     */
    @Override
    public Boolean exists(FileObjectRequest request) {
        return fileStorageFacade.exists(request);
    }

    /**
     * 删除指定文件对象。
     *
     * @param request 文件对象请求体，包含存储桶和对象键
     * @return 是否成功删除对象
     */
    @Override
    public Boolean delete(FileObjectRequest request) {
        return fileStorageFacade.delete(request);
    }

    /**
     * 为文件下载生成有限时效的预签名 URL。
     *
     * @param request 预签名请求体，包含对象定位和有效期
     * @return 可直接下载的预签名地址及其过期信息
     */
    @Override
    public FilePresignedUrlResponse presignGet(FilePresignRequest request) {
        return fileStorageFacade.presignGet(request);
    }

    /**
     * 为文件上传生成有限时效的预签名 URL。
     *
     * @param request 预签名请求体，包含目标对象定位和有效期
     * @return 可直接上传到目标对象的预签名地址及其过期信息
     */
    @Override
    public FilePresignedUrlResponse presignPut(FilePresignRequest request) {
        return fileStorageFacade.presignPut(request);
    }
}
