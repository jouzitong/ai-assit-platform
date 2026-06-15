package ai.platform.aiassist.service.ai.provider.client;

import ai.platform.aiassist.service.ai.api.dto.KbDocument;
import ai.platform.aiassist.service.ai.api.dto.KbSearchItem;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.provider.config.QwenProperties;
import com.aliyun.bailian20231229.Client;
import com.aliyun.bailian20231229.models.AddFileRequest;
import com.aliyun.bailian20231229.models.ApplyFileUploadLeaseRequest;
import com.aliyun.bailian20231229.models.ApplyFileUploadLeaseResponse;
import com.aliyun.bailian20231229.models.CreateIndexRequest;
import com.aliyun.bailian20231229.models.CreateIndexResponse;
import com.aliyun.bailian20231229.models.DeleteIndexDocumentRequest;
import com.aliyun.bailian20231229.models.GetIndexJobStatusRequest;
import com.aliyun.bailian20231229.models.GetIndexJobStatusResponse;
import com.aliyun.bailian20231229.models.RetrieveRequest;
import com.aliyun.bailian20231229.models.RetrieveResponse;
import com.aliyun.bailian20231229.models.SubmitIndexAddDocumentsJobRequest;
import com.aliyun.bailian20231229.models.SubmitIndexAddDocumentsJobResponse;
import com.aliyun.bailian20231229.models.SubmitIndexJobRequest;
import com.aliyun.bailian20231229.models.SubmitIndexJobResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.utils.JacksonJsonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 通义百炼知识库客户端。
 *
 * <p>该类用于封装阿里云百炼知识库相关操作，包括：</p>
 * <ul>
 *     <li>将业务文档上传到百炼文件空间；</li>
 *     <li>创建知识库索引或向已有知识库追加文档；</li>
 *     <li>等待索引任务完成，确保文档可被检索；</li>
 *     <li>删除知识库文档；</li>
 *     <li>根据用户问题从知识库中召回相关内容。</li>
 * </ul>
 *
 * <p>这里的知识库 ID 对应百炼侧的 IndexId，业务侧可将其理解为 kbId。</p>
 */
@Slf4j
@Component
public class QwenKnowledgeBaseClient {

    private final Client bailianClient;
    private final QwenProperties properties;

    /**
     * 创建通义百炼知识库客户端。
     *
     * @param bailianClient 百炼 SDK 客户端，由 Spring 容器注入
     * @param properties    通义千问/百炼相关配置，包括 workspaceId、categoryId、parser、索引类型等
     */
    public QwenKnowledgeBaseClient(Client bailianClient, QwenProperties properties) {
        this.bailianClient = bailianClient;
        this.properties = properties;
    }

    /**
     * 新增或更新知识库文档。
     *
     * <p>处理逻辑：</p>
     * <ol>
     *     <li>遍历待导入文档；</li>
     *     <li>跳过空文档或内容为空的文档，并记录失败文档 ID；</li>
     *     <li>将每个有效文档先上传到百炼文件空间；</li>
     *     <li>如果没有传入 kbId，则使用首个成功上传的文件创建新的知识库索引；</li>
     *     <li>如果传入了 kbId，则将文件追加到已有知识库索引中；</li>
     *     <li>每个索引任务提交后都会等待任务完成，确保文档导入结果明确。</li>
     * </ol>
     *
     * @param workspaceId 百炼工作空间 ID
     * @param kbId        知识库 ID；为空时会自动创建新的知识库
     * @param documents   需要导入知识库的业务文档列表
     * @param meta        请求上下文扩展信息，可携带 kbName、sourceType、sinkType 等配置
     * @return 导入结果，包括最终知识库 ID、成功接收数量、失败文档 ID 列表
     * @throws Exception 上传文件、创建索引、追加索引或等待任务过程中发生异常时抛出
     */
    public UpsertResult upsert(String workspaceId, String kbId, List<KbDocument> documents, RequestMeta meta) throws Exception {
        log.debug("qwen kb upsert start, workspaceId={}, kbId={}, documentCount={}",
                workspaceId, kbId, documents == null ? 0 : documents.size());
        // targetKbId 用于保存最终操作的知识库 ID；如果入参 kbId 为空，后续会在创建索引后赋值。
        String targetKbId = kbId;
        // accepted 统计成功提交并完成索引任务的文档数量。
        int accepted = 0;
        for (int i = 0; i < documents.size(); i++) {
            // 逐条处理文档，单个文档失败不会影响后续文档继续导入。
            KbDocument document = documents.get(i);
            // 文档对象为空或正文内容为空时，无法生成有效知识片段，直接记录为失败。
            if (document == null || !StringUtils.hasText(document.getContent())) {
                String documentId = resolveDocumentId(document, i);
                throw new IllegalArgumentException("kbUpsert document content must not be empty, docId=" + documentId);
            }
            String documentId = resolveDocumentId(document, i);
            // 先把业务文档写入临时文件，再通过百炼文件上传流程得到 fileId。
            UploadedDocument uploaded = uploadDocument(workspaceId, document, i);
            // 如果当前没有知识库 ID，则说明需要基于首个有效文件创建新的知识库索引。
            if (!StringUtils.hasText(targetKbId)) {
                // 创建索引后会返回新的知识库 ID，后续文档会继续追加到该知识库中。
                targetKbId = createIndex(workspaceId, uploaded.fileId(), document, meta);
            } else {
                // 已存在知识库时，直接提交追加文档任务。
                submitAddDocumentsJob(workspaceId, targetKbId, uploaded.fileId());
            }
            log.debug("qwen kb upsert document finished, workspaceId={}, kbId={}, docId={}, acceptedIndex={}",
                    workspaceId, targetKbId, documentId, accepted + 1);
            // 能执行到这里表示上传和索引任务均已完成，计入成功数量。
            accepted++;
        }
        // 如果没有创建出知识库，且没有任何文档成功导入，则整体导入视为失败。
        if (!StringUtils.hasText(targetKbId) && accepted == 0) {
            throw new IllegalStateException("kbUpsert failed: no document imported");
        }
        log.debug("qwen kb upsert finished, workspaceId={}, kbId={}, accepted={}, failed={}",
                workspaceId, targetKbId, accepted, 0);
        return new UpsertResult(targetKbId, accepted, Collections.emptyList());
    }

    /**
     * 从知识库索引中删除指定文档。
     *
     * @param workspaceId 百炼工作空间 ID
     * @param kbId        知识库 ID，即百炼 IndexId
     * @param documentIds 需要删除的文档 ID 列表
     * @return 删除请求中包含的文档数量
     * @throws Exception 调用百炼删除接口失败时抛出
     */
    public int delete(String workspaceId, String kbId, List<String> documentIds) throws Exception {
        log.debug("qwen kb delete start, workspaceId={}, kbId={}, documentCount={}",
                workspaceId, kbId, documentIds == null ? 0 : documentIds.size());
        // 构造删除索引文档请求，指定知识库 ID 和待删除的文档 ID 集合。
        DeleteIndexDocumentRequest request = new DeleteIndexDocumentRequest();
        request.setIndexId(kbId);
        request.setDocumentIds(documentIds);
        // 调用百炼接口删除索引中的文档记录。
        bailianClient.deleteIndexDocumentWithOptions(workspaceId, request, new HashMap<>(), new RuntimeOptions());
        log.debug("qwen kb delete finished, workspaceId={}, kbId={}, deleted={}",
                workspaceId, kbId, documentIds.size());
        return documentIds.size();
    }

    /**
     * 检索知识库内容。
     *
     * <p>该方法调用百炼 Retrieve 接口，根据用户 query 从指定知识库中召回相关片段，
     * 并将百炼返回的节点结构转换为业务统一的 {@link KbSearchItem}。</p>
     *
     * @param workspaceId 百炼工作空间 ID
     * @param kbId        知识库 ID，即百炼 IndexId
     * @param query       用户检索问题或关键词
     * @param topK        最大返回数量；为空或小于等于 0 时返回全部召回结果
     * @return 知识库召回结果列表
     * @throws Exception 调用百炼检索接口失败时抛出
     */
    public List<KbSearchItem> search(String workspaceId, String kbId, String query, Integer topK) throws Exception {
        log.debug("qwen kb search start, workspaceId={}, kbId={}, topK={}, query={}",
                workspaceId, kbId, topK, query);
        // 构造知识库检索请求，指定知识库 ID 和用户查询内容。
        RetrieveRequest request = new RetrieveRequest();
        request.setIndexId(kbId);
        request.setQuery(query);
        // 调用百炼检索接口获取原始召回结果。
        RetrieveResponse response =
                bailianClient.retrieveWithOptions(workspaceId, request, new HashMap<>(), new RuntimeOptions());
        // 从 SDK 返回对象中提取 Nodes 列表，兼容 Map 和 Java Bean 两种数据结构。
        List<?> nodes = extractNodes(response);
        // topK 未指定时默认返回全部召回节点。
        int limit = topK == null || topK <= 0 ? nodes.size() : topK;
        List<KbSearchItem> items = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, nodes.size()); i++) {
            // 兼容 SDK 强类型节点和 Map 节点。
            Object node = nodes.get(i);
            items.add(toKbSearchItem(node));
        }
        log.debug("qwen kb search finished, workspaceId={}, kbId={}, hitCount={}",
                workspaceId, kbId, items.size());
        return items;
    }

    /**
     * 解析百炼工作空间 ID。
     *
     * <p>优先从请求扩展参数 meta.ext.workspaceId 中获取；
     * 如果请求未指定，则使用配置文件中的默认 workspaceId。</p>
     *
     * @param meta 请求上下文扩展信息
     * @return 可用的百炼工作空间 ID
     * @throws IllegalStateException 当请求和配置中都没有 workspaceId 时抛出
     */
    public String resolveWorkspaceId(RequestMeta meta) {
        // 请求级 workspaceId 优先级最高，便于不同租户或不同业务动态指定工作空间。
        Object workspaceId = metaExt(meta, "workspaceId");
        if (workspaceId instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        // 请求未指定时，退回到系统默认配置。
        if (StringUtils.hasText(properties.getWorkspaceId())) {
            return properties.getWorkspaceId();
        }
        throw new IllegalStateException("qwen bailian workspaceId is required");
    }

    /**
     * 上传业务文档到百炼文件空间。
     *
     * <p>百炼文件上传流程分为三步：</p>
     * <ol>
     *     <li>将业务文档内容写入本地临时文件；</li>
     *     <li>申请文件上传租约，获取上传 URL 和请求头；</li>
     *     <li>通过上传 URL 执行 PUT 上传，然后调用 addFile 生成百炼 fileId。</li>
     * </ol>
     *
     * @param workspaceId 百炼工作空间 ID
     * @param document    业务文档
     * @param index       当前文档在列表中的位置，用于兜底生成文档 ID
     * @return 上传后的文件信息，主要包含百炼 fileId
     * @throws Exception 文件创建、租约申请、文件上传或 addFile 失败时抛出
     */
    private UploadedDocument uploadDocument(String workspaceId, KbDocument document, int index) throws Exception {
        String documentId = resolveDocumentId(document, index);
        log.debug("qwen kb upload document start, workspaceId={}, documentId={}", workspaceId, documentId);
        // 将业务文档内容落到临时 txt 文件，方便按百炼文件上传协议处理。
        Path tempFile = createDocumentTempFile(document, index);
        try {
            // categoryId 表示百炼文件类目，优先从文档元数据获取，未指定时使用默认配置。
            String categoryId = resolveCategoryId(document);
            // 申请上传租约，百炼会返回实际文件上传地址和所需请求头。
            ApplyFileUploadLeaseResponse leaseResponse = applyLease(workspaceId, categoryId, tempFile);
            log.debug("qwen kb apply lease response, workspaceId={}, documentId={}, categoryId={}, response={}",
                    workspaceId, documentId, categoryId, JacksonJsonUtils.toStr(leaseResponse));
            String leaseId = Objects.toString(leaseResponse.getBody().getData().getFileUploadLeaseId(), null);
            Map<String, String> uploadHeaders = toStringMap(leaseResponse.getBody().getData().getParam().getHeaders());
            String uploadUrl = Objects.toString(leaseResponse.getBody().getData().getParam().getUrl(), null);
            // 按租约返回的 URL 和 Header 上传文件内容。
            uploadFile(uploadUrl, uploadHeaders, tempFile);

            // 文件上传完成后，需要调用 addFile 将租约转换为百炼文件记录。
            AddFileRequest addFileRequest = new AddFileRequest();
            addFileRequest.setLeaseId(leaseId);
            addFileRequest.setParser(resolveParser(document));
            addFileRequest.setCategoryId(categoryId);
            String fileId = Objects.toString(
                    bailianClient.addFileWithOptions(workspaceId, addFileRequest, new HashMap<>(), new RuntimeOptions())
                            .getBody().getData().getFileId(),
                    null
            );
            // addFile 成功后必须返回 fileId，否则后续无法创建或追加索引。
            if (!StringUtils.hasText(fileId)) {
                throw new IllegalStateException("bailian addFile returned empty fileId");
            }
            log.debug("qwen kb upload document finished, workspaceId={}, documentId={}, fileId={}",
                    workspaceId, documentId, fileId);
            return new UploadedDocument(fileId);
        } finally {
            // 无论上传成功还是失败，都清理本地临时文件，避免磁盘残留。
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 申请百炼文件上传租约。
     *
     * <p>租约中包含上传 URL、请求头等信息，客户端需要根据这些信息执行实际文件上传。</p>
     *
     * @param workspaceId 百炼工作空间 ID
     * @param categoryId  百炼文件类目 ID
     * @param file        待上传的本地文件
     * @return 上传租约响应
     * @throws Exception 计算文件信息或申请租约失败时抛出
     */
    private ApplyFileUploadLeaseResponse applyLease(String workspaceId, String categoryId, Path file) throws Exception {
        log.debug("qwen kb apply lease start, workspaceId={}, categoryId={}, fileName={}, sizeInBytes={}",
                workspaceId, categoryId, file.getFileName(), Files.size(file));
        // 申请租约时需要提供文件名、MD5 和文件大小，百炼会据此生成上传凭证。
        ApplyFileUploadLeaseRequest request = new ApplyFileUploadLeaseRequest();
        request.setFileName(file.getFileName().toString());
        request.setMd5(calculateMd5(file));
        request.setSizeInBytes(String.valueOf(Files.size(file)));
        return bailianClient.applyFileUploadLeaseWithOptions(categoryId, workspaceId, request, new HashMap<>(), new RuntimeOptions());
    }

    /**
     * 根据百炼上传租约执行实际文件上传。
     *
     * @param uploadUrl 租约返回的文件上传地址
     * @param headers   租约要求携带的请求头
     * @param file      待上传的本地文件
     * @throws IOException 文件读取、网络连接或上传失败时抛出
     */
    private void uploadFile(String uploadUrl, Map<String, String> headers, Path file) throws IOException {
        // 上传地址为空时无法执行 PUT 上传，直接参数校验失败。
        if (!StringUtils.hasText(uploadUrl)) {
            throw new IllegalArgumentException("uploadUrl must not be empty");
        }
        log.debug("qwen kb upload file start, fileName={}, headerCount={}",
                file.getFileName(), headers == null ? 0 : headers.size());
        // 使用租约提供的上传地址建立 HTTP 连接。
        HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
        // 百炼文件租约上传通常使用 PUT 方法写入文件内容。
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        // 按租约要求设置请求头，空 key 或空 value 会被跳过。
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (StringUtils.hasText(entry.getKey()) && StringUtils.hasText(entry.getValue())) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        // 以流式方式上传文件，避免一次性加载整个文件到内存。
        try (FileInputStream input = new FileInputStream(file.toFile());
             OutputStream output = connection.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
        // 上传完成后检查 HTTP 状态码，非 200 认为上传失败。
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IllegalStateException("bailian upload failed, responseCode=" + responseCode);
        }
        log.debug("qwen kb upload file finished, fileName={}, responseCode={}", file.getFileName(), responseCode);
    }

    /**
     * 使用指定文件创建新的知识库索引。
     *
     * <p>该方法会先创建 Index，再提交索引构建任务，并同步等待任务完成。</p>
     *
     * @param workspaceId 百炼工作空间 ID
     * @param fileId      已上传到百炼文件空间的文件 ID
     * @param document    用于解析知识库名称等信息的业务文档
     * @param meta        请求上下文扩展信息，可覆盖索引类型、来源类型等配置
     * @return 新创建的知识库 ID，即百炼 IndexId
     * @throws Exception 创建索引、提交任务或等待任务失败时抛出
     */
    private String createIndex(String workspaceId, String fileId, KbDocument document, RequestMeta meta) throws Exception {
        String indexName = resolveIndexName(document, meta);
        log.debug("qwen kb create index start, workspaceId={}, fileId={}, indexName={}",
                workspaceId, fileId, indexName);
        // 创建索引时需要指定名称、结构类型、来源类型、写入类型以及初始文档 ID。
        CreateIndexRequest request = new CreateIndexRequest();
        request.setName(indexName);
        request.setStructureType(resolveStructureType(meta));
        request.setSourceType(resolveSourceType(meta));
        request.setSinkType(resolveSinkType(meta));
        request.setDocumentIds(Collections.singletonList(fileId));
        // 调用百炼创建知识库索引接口。
        CreateIndexResponse response =
                bailianClient.createIndexWithOptions(workspaceId, request, new HashMap<>(), new RuntimeOptions());
        String kbId = Objects.toString(response.getBody().getData().getId(), null);
        // 创建成功后必须返回 IndexId，否则无法继续提交构建任务。
        if (!StringUtils.hasText(kbId)) {
            throw new IllegalStateException("bailian createIndex returned empty indexId");
        }
        log.debug("qwen kb create index response, workspaceId={}, kbId={}", workspaceId, kbId);

        // 创建索引后，还需要显式提交索引构建任务，文档才会进入可检索状态。
        SubmitIndexJobRequest submitRequest = new SubmitIndexJobRequest();
        submitRequest.setIndexId(kbId);
        SubmitIndexJobResponse submitResponse =
                bailianClient.submitIndexJobWithOptions(workspaceId, submitRequest, new HashMap<>(), new RuntimeOptions());
        String jobId = Objects.toString(submitResponse.getBody().getData().getId(), null);
        log.debug("qwen kb create index submit job, workspaceId={}, kbId={}, jobId={}", workspaceId, kbId, jobId);
        // 同步等待索引构建完成，避免调用方马上检索时查不到刚导入的内容。
        waitForIndexJob(workspaceId, kbId, jobId);
        return kbId;
    }

    /**
     * 向已有知识库追加文档。
     *
     * @param workspaceId 百炼工作空间 ID
     * @param kbId        已存在的知识库 ID，即百炼 IndexId
     * @param fileId      已上传到百炼文件空间的文件 ID
     * @throws Exception 提交追加任务或等待任务失败时抛出
     */
    private void submitAddDocumentsJob(String workspaceId, String kbId, String fileId) throws Exception {
        log.debug("qwen kb add documents job start, workspaceId={}, kbId={}, fileId={}", workspaceId, kbId, fileId);
        // 构造追加文档任务，请求中需要指定目标知识库和待追加文件 ID。
        SubmitIndexAddDocumentsJobRequest request = new SubmitIndexAddDocumentsJobRequest();
        request.setIndexId(kbId);
        request.setDocumentIds(Collections.singletonList(fileId));
        request.setSourceType(properties.getSourceType());
        // 提交追加文档任务后，百炼会异步构建新增文档索引。
        SubmitIndexAddDocumentsJobResponse response =
                bailianClient.submitIndexAddDocumentsJobWithOptions(workspaceId, request, new HashMap<>(), new RuntimeOptions());
        String jobId = Objects.toString(response.getBody().getData().getId(), null);
        log.debug("qwen kb add documents job submitted, workspaceId={}, kbId={}, jobId={}", workspaceId, kbId, jobId);
        // 等待追加索引任务完成，确保新增文档真正进入知识库。
        waitForIndexJob(workspaceId, kbId, jobId);
    }

    /**
     * 轮询等待百炼索引任务完成。
     *
     * <p>百炼创建索引和追加文档都是异步任务，因此需要根据 jobId 查询任务状态。
     * 任务完成后返回；任务失败或超时则抛出异常。</p>
     *
     * @param workspaceId 百炼工作空间 ID
     * @param kbId        知识库 ID，即百炼 IndexId
     * @param jobId       百炼索引任务 ID
     * @throws Exception 查询任务状态失败、任务失败或等待超时时抛出
     */
    private void waitForIndexJob(String workspaceId, String kbId, String jobId) throws Exception {
        // 没有 jobId 就无法查询异步任务状态，直接视为异常。
        if (!StringUtils.hasText(jobId)) {
            throw new IllegalStateException("bailian index job id must not be empty");
        }
        // 计算最大等待截止时间，避免无限轮询。
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getKbJobTimeoutMs());
        while (System.nanoTime() < deadline) {
            // 每轮根据知识库 ID 和任务 ID 查询当前索引任务状态。
            GetIndexJobStatusRequest request = new GetIndexJobStatusRequest();
            request.setIndexId(kbId);
            request.setJobId(jobId);
            GetIndexJobStatusResponse response =
                    bailianClient.getIndexJobStatusWithOptions(workspaceId, request, new HashMap<>(), new RuntimeOptions());
            // SDK 返回的数据结构可能存在差异，这里统一提取状态字段。
            String status = extractStatus(response.getBody().getData());
            log.debug("qwen kb index job status, workspaceId={}, kbId={}, jobId={}, status={}",
                    workspaceId, kbId, jobId, status);
            // 兼容不同状态命名：COMPLETED 和 FINISH 都视为成功完成。
            if ("COMPLETED".equalsIgnoreCase(status) || "FINISH".equalsIgnoreCase(status)) {
                return;
            }
            // 明确失败状态直接抛出异常，不再继续等待。
            if ("FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
                throw new IllegalStateException("bailian index job failed, jobId=" + jobId);
            }
            // 控制轮询频率，并设置最小 200ms 间隔，避免过于频繁调用接口。
            Thread.sleep(Math.max(properties.getKbPollIntervalMs(), 200));
        }
        // 超过最大等待时间仍未完成，认为任务超时。
        throw new IllegalStateException("bailian index job timeout, jobId=" + jobId);
    }

    /**
     * 从百炼任务状态响应中提取状态值。
     *
     * <p>由于 SDK 返回对象可能是 Map，也可能是强类型 Java Bean，
     * 这里同时兼容两种结构。</p>
     *
     * @param data 响应中的 data 对象
     * @return 状态字符串；无法提取时返回 null
     */
    private String extractStatus(Object data) {
        // 兼容 Map 结构：直接读取 Status 字段。
        if (data instanceof Map<?, ?> map) {
            Object value = map.get("Status");
            return value == null ? null : Objects.toString(value, null);
        }
        // 兼容 Java Bean 结构：通过反射调用 getStatus 方法。
        try {
            Object value = data.getClass().getMethod("getStatus").invoke(data);
            return value == null ? null : Objects.toString(value, null);
        } catch (Exception ignore) {
            // 提取失败时不抛出异常，由调用方根据 null 状态继续等待或超时处理。
            return null;
        }
    }

    /**
     * 从知识库检索响应中提取召回节点列表。
     *
     * @param response 百炼检索响应
     * @return 召回节点列表；无法提取时返回空列表
     */
    private List<?> extractNodes(RetrieveResponse response) {
        // Retrieve 响应的 data 中包含召回节点信息。
        Object data = response.getBody().getData();
        // 兼容 Map 结构：直接读取 Nodes 字段。
        if (data instanceof Map<?, ?> map) {
            Object nodes = map.get("Nodes");
            return nodes instanceof List<?> list ? list : Collections.emptyList();
        }
        // 兼容 Java Bean 结构：通过反射调用 getNodes 方法。
        try {
            Object nodes = data.getClass().getMethod("getNodes").invoke(data);
            return nodes instanceof List<?> list ? list : Collections.emptyList();
        } catch (Exception ignore) {
            // 提取失败时返回空集合，避免影响上层流程。
            return Collections.emptyList();
        }
    }

    /**
     * 将百炼原始召回节点转换为业务统一的知识库检索结果。
     *
     * @param rawNode 百炼原始节点数据
     * @return 业务侧知识库检索结果对象
     */
    private KbSearchItem toKbSearchItem(Object rawNode) {
        // 创建业务统一的召回结果对象。
        KbSearchItem item = new KbSearchItem();
        Map<String, Object> rawNodeMap = toObjectMap(rawNode);
        // Metadata 中通常包含文档 ID、来源等业务扩展信息。
        Map<String, Object> metadata = toObjectMap(readField(rawNode, rawNodeMap, "metadata", "Metadata"));
        item.setMetadata(metadata);
        // 文档 ID 字段兼容 doc_id 和 DocId 两种命名。
        item.setDocumentId(Objects.toString(metadata.getOrDefault("doc_id", metadata.get("DocId")), null));
        // Score 表示召回相关度分数，只有数值类型才进行设置。
        Object score = readField(rawNode, rawNodeMap, "score", "Score");
        if (score instanceof Number number) {
            item.setScore(number.doubleValue());
        }
        // Text 是实际召回的知识片段内容。
        Object text = readField(rawNode, rawNodeMap, "text", "Text");
        item.setContent(text == null ? null : Objects.toString(text, null));
        return item;
    }

    private Object readField(Object source, Map<String, Object> sourceMap, String camelName, String legacyName) {
        if (sourceMap.containsKey(camelName)) {
            return sourceMap.get(camelName);
        }
        if (sourceMap.containsKey(legacyName)) {
            return sourceMap.get(legacyName);
        }
        if (source == null) {
            return null;
        }
        try {
            return source.getClass()
                    .getMethod("get" + Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1))
                    .invoke(source);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 根据业务文档创建临时文本文件。
     *
     * <p>为了让知识库检索时保留部分业务上下文，临时文件会先写入 source 和 metadata，
     * 再写入文档正文内容。</p>
     *
     * @param document 业务文档
     * @param index    当前文档在列表中的位置，用于兜底生成文件名
     * @return 创建完成的临时文件路径
     * @throws IOException 创建或写入临时文件失败时抛出
     */
    private Path createDocumentTempFile(KbDocument document, int index) throws IOException {
        // 使用文档 ID 生成安全文件名，避免特殊字符影响临时文件创建。
        String filename = sanitizeFilename(resolveDocumentId(document, index));
        // 创建 txt 临时文件，百炼后续会按文件方式进行解析。
        Path tempFile = Files.createTempFile("qwen-kb-" + filename + "-", ".txt");
        // 组装写入文件的完整内容。
        StringBuilder content = new StringBuilder();
        // 将文档来源写入文件，便于后续召回结果定位来源。
        if (StringUtils.hasText(document.getSource())) {
            content.append("source: ").append(document.getSource()).append('\n');
        }
        // 将元数据写入文件头部，增强知识片段的上下文信息。
        if (!document.getMetadata().isEmpty()) {
            for (Map.Entry<String, Object> entry : document.getMetadata().entrySet()) {
                content.append(entry.getKey()).append(": ").append(Objects.toString(entry.getValue(), "")).append('\n');
            }
        }
        // 元数据和正文之间增加空行，提升文本可读性和解析效果。
        if (content.length() > 0) {
            content.append('\n');
        }
        // 最后写入真正需要被知识库检索的正文内容。
        content.append(document.getContent());
        // 使用 UTF-8 写入，避免中文内容乱码。
        Files.writeString(tempFile, content.toString(), StandardCharsets.UTF_8);
        return tempFile;
    }

    /**
     * 计算文件 MD5。
     *
     * <p>百炼申请上传租约时需要提供文件 MD5，用于文件完整性校验。</p>
     *
     * @param file 待计算的文件路径
     * @return 文件 MD5 十六进制字符串
     * @throws Exception MD5 算法不可用或文件读取失败时抛出
     */
    private String calculateMd5(Path file) throws Exception {
        // 创建 MD5 摘要计算器。
        MessageDigest digest = MessageDigest.getInstance("MD5");
        // 分块读取文件并更新摘要，避免大文件占用过多内存。
        try (FileInputStream inputStream = new FileInputStream(file.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        // 将二进制摘要结果转换为标准十六进制字符串。
        StringBuilder builder = new StringBuilder();
        for (byte value : digest.digest()) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    /**
     * 将任意 Map 结构转换为字符串 Map。
     *
     * @param headers 原始请求头对象
     * @return key 和 value 都转换为字符串后的 Map
     */
    private Map<String, String> toStringMap(Object headers) {
        // 先将原始对象统一转换为 Map<String, Object>。
        Map<String, Object> raw = toObjectMap(headers);
        // 再将 value 转换为字符串，便于设置 HTTP 请求头。
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            result.put(entry.getKey(), Objects.toString(entry.getValue(), null));
        }
        return result;
    }

    /**
     * 将任意 Map 对象规范化为 Map<String, Object>。
     *
     * @param value 原始对象
     * @return 规范化后的 Map；如果原始对象不是 Map，则尝试按 Java Bean getter 提取字段
     */
    private Map<String, Object> toObjectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            // 将 key 统一转成字符串，降低后续读取字段时的类型复杂度。
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(Objects.toString(entry.getKey(), null), entry.getValue());
            }
            return result;
        }
        if (value != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (java.lang.reflect.Method method : value.getClass().getMethods()) {
                if (method.getParameterCount() != 0) {
                    continue;
                }
                String methodName = method.getName();
                if (!methodName.startsWith("get") || "getClass".equals(methodName)) {
                    continue;
                }
                try {
                    Object fieldValue = method.invoke(value);
                    String fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                    result.put(fieldName, fieldValue);
                } catch (Exception ignore) {
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return new LinkedHashMap<>();
    }

    /**
     * 解析知识库索引名称。
     *
     * <p>优先级：请求扩展参数 kbName &gt; 文档来源 source &gt; 默认名称。</p>
     *
     * @param document 业务文档
     * @param meta     请求上下文扩展信息
     * @return 知识库索引名称
     */
    private String resolveIndexName(KbDocument document, RequestMeta meta) {
        // 请求扩展参数中的 kbName 优先级最高，适合业务侧主动指定知识库名称。
        Object custom = metaExt(meta, "kbName");
        if (custom instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        // 未指定 kbName 时，使用文档来源作为知识库名称。
        if (StringUtils.hasText(document.getSource())) {
            return document.getSource();
        }
        // 最后使用默认前缀加文档 ID 生成兜底名称。
        return "qwen-kb-" + resolveDocumentId(document, 0);
    }

    /**
     * 解析百炼文件类目 ID。
     *
     * <p>优先从文档 metadata.categoryId 中获取；未指定时使用默认配置。</p>
     *
     * @param document 业务文档
     * @return 百炼文件类目 ID
     */
    private String resolveCategoryId(KbDocument document) {
        // 文档级 categoryId 可用于将不同业务文档放入不同百炼文件类目。
        Object value = document.getMetadata().get("categoryId");
        if (value != null && StringUtils.hasText(Objects.toString(value, null))) {
            return Objects.toString(value, null);
        }
        // 文档未指定时使用系统默认文件类目。
        return properties.getCategoryId();
    }

    /**
     * 解析百炼文件解析器类型。
     *
     * <p>优先从文档 metadata.parser 中获取；未指定时使用默认配置。</p>
     *
     * @param document 业务文档
     * @return 百炼文件解析器类型
     */
    private String resolveParser(KbDocument document) {
        // 文档级 parser 可用于针对不同文档指定不同解析方式。
        Object value = document.getMetadata().get("parser");
        if (value != null && StringUtils.hasText(Objects.toString(value, null))) {
            return Objects.toString(value, null);
        }
        // 未指定解析器时使用系统默认配置。
        return properties.getParser();
    }

    /**
     * 解析知识库结构类型。
     *
     * @param meta 请求上下文扩展信息
     * @return 百炼索引结构类型
     */
    private String resolveStructureType(RequestMeta meta) {
        // 支持通过请求扩展参数覆盖默认结构类型。
        Object value = metaExt(meta, "structureType");
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        // 未指定时使用系统默认结构类型。
        return properties.getStructureType();
    }

    /**
     * 解析知识库来源类型。
     *
     * @param meta 请求上下文扩展信息
     * @return 百炼索引来源类型
     */
    private String resolveSourceType(RequestMeta meta) {
        // 支持通过请求扩展参数覆盖默认来源类型。
        Object value = metaExt(meta, "sourceType");
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        // 未指定时使用系统默认来源类型。
        return properties.getSourceType();
    }

    /**
     * 解析知识库写入类型。
     *
     * @param meta 请求上下文扩展信息
     * @return 百炼索引写入类型
     */
    private String resolveSinkType(RequestMeta meta) {
        // 支持通过请求扩展参数覆盖默认写入类型。
        Object value = metaExt(meta, "sinkType");
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        // 未指定时使用系统默认写入类型。
        return properties.getSinkType();
    }

    /**
     * 从请求扩展参数中读取指定 key 的值。
     *
     * @param meta 请求上下文扩展信息
     * @param key  扩展字段 key
     * @return 扩展字段值；不存在时返回 null
     */
    private Object metaExt(RequestMeta meta, String key) {
        // meta 或 ext 为空时，直接返回 null，调用方再走默认配置。
        if (meta == null || meta.getExt() == null) {
            return null;
        }
        return meta.getExt().get(key);
    }

    /**
     * 解析业务文档 ID。
     *
     * <p>优先使用文档自身 documentId；为空时根据列表下标生成兜底 ID。</p>
     *
     * @param document 业务文档
     * @param index    当前文档在列表中的位置
     * @return 文档 ID
     */
    private String resolveDocumentId(KbDocument document, int index) {
        // 业务侧传入 documentId 时直接使用，便于后续删除、排查和重试。
        if (document != null && StringUtils.hasText(document.getDocumentId())) {
            return document.getDocumentId();
        }
        // 文档 ID 缺失时使用下标生成兜底 ID。
        return "doc-" + index;
    }

    /**
     * 清理文件名中的非法字符。
     *
     * <p>仅保留字母、数字、点、下划线和中划线，避免特殊字符导致临时文件创建失败。</p>
     *
     * @param value 原始文件名片段
     * @return 安全的文件名片段，最大长度 32
     */
    private String sanitizeFilename(String value) {
        // 将不适合作为文件名的字符替换为下划线。
        String normalized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        // 限制文件名片段长度，避免临时文件名过长。
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }

    /**
     * 已上传到百炼文件空间的文档信息。
     *
     * @param fileId 百炼文件 ID
     */
    private record UploadedDocument(String fileId) {
    }

    /**
     * 知识库文档导入结果。
     *
     * @param kbId              最终使用或创建的知识库 ID
     * @param accepted          成功导入的文档数量
     * @param failedDocumentIds 导入失败或被跳过的文档 ID 列表
     */
    public record UpsertResult(String kbId, int accepted, List<String> failedDocumentIds) {
    }
}
