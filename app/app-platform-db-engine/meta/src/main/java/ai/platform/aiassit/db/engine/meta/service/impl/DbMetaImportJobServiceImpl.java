package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportJobCreateResponse;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportJobProgressDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportProgressSummaryDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.importer.DbMetaImportData;
import ai.platform.aiassit.db.engine.meta.enums.DbMetaImportJobStage;
import ai.platform.aiassit.db.engine.meta.enums.DbMetaImportJobStatus;
import ai.platform.aiassit.db.engine.meta.service.DbMetaImportJobService;
import ai.platform.aiassit.db.engine.meta.service.importer.DbMetaImportService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.security.auth.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class DbMetaImportJobServiceImpl implements DbMetaImportJobService {

    private static final int MAX_RECENT_MESSAGES = 8;

    private final List<DbMetaImportService> importServices;
    private final DbMetaImportExecutor importExecutor;
    private final Map<String, ImportJobState> jobStore = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("db-meta-import-job");
        thread.setDaemon(true);
        return thread;
    });

    public DbMetaImportJobServiceImpl(
            List<DbMetaImportService> importServices,
            DbMetaImportExecutor importExecutor
    ) {
        this.importServices = importServices;
        this.importExecutor = importExecutor;
    }

    @Override
    public DbMetaImportJobCreateResponse createImportJob(String sourceKey, MultipartFile file) throws IOException {
        if (!StringUtils.hasText(sourceKey)) {
            throw new IllegalArgumentException("缺少 sourceKey");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }
        String jobId = UUID.randomUUID().toString();
        String fileName = resolveFilename(file);
        String suffix = resolveSuffix(fileName);
        Path tempFile = Files.createTempFile("db-meta-import-", suffix);
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        ImportJobState state = new ImportJobState(jobId, sourceKey.trim(), fileName, file.getContentType());
        jobStore.put(jobId, state);
        UserContext userContext = SecurityContextHolder.get();
        executorService.submit(() -> {
            SecurityContextHolder.set(userContext);
            try {
                runJob(state, tempFile);
            } finally {
                SecurityContextHolder.clear();
            }
        });
        return DbMetaImportJobCreateResponse.builder()
                .jobId(jobId)
                .build();
    }

    @Override
    public DbMetaImportJobProgressDTO getImportJobProgress(String jobId) {
        ImportJobState state = jobStore.get(jobId);
        if (state == null) {
            throw new IllegalArgumentException("导入任务不存在: " + jobId);
        }
        synchronized (state) {
            return state.toProgressDTO();
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void runJob(ImportJobState state, Path tempFile) {
        try {
            synchronized (state) {
                state.status = DbMetaImportJobStatus.RUNNING;
                state.stage = DbMetaImportJobStage.PARSING;
                state.progressPercent = 5;
                state.message = "正在解析导入文件";
                state.pushMessage("导入任务已开始，正在解析文件");
            }
            MultipartFile storedFile = new TempFileMultipartFile(tempFile, state.fileName, state.contentType);
            DbMetaImportService importService = importServices.stream()
                    .filter(service -> service.supports(storedFile))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("暂不支持的导入文件格式: " + state.fileName));
            DbMetaImportData importData = importService.parse(state.sourceKey, storedFile);
            synchronized (state) {
                state.tableTotal = safeSize(importData.getTables());
                state.fieldTotal = safeSize(importData.getFields());
                state.indexTotal = safeSize(importData.getIndexes());
                state.message = String.format("解析完成：%d 张表，%d 个字段，%d 个索引", state.tableTotal, state.fieldTotal, state.indexTotal);
                state.pushMessage(state.message);
                state.progressPercent = calculateProgressPercent(state);
            }
            DbMetaImportResultDTO result = importExecutor.importData(
                    state.sourceKey,
                    storedFile,
                    importService.getFormat(),
                    importData,
                    new DbMetaImportProgressListener() {
                        @Override
                        public void onStageChanged(DbMetaImportJobStage stage, String stageMessage) {
                            synchronized (state) {
                                state.stage = stage;
                                state.message = stageMessage;
                                state.pushMessage(stageMessage);
                                state.progressPercent = calculateProgressPercent(state);
                            }
                        }

                        @Override
                        public void onTableProgress(int processed, int total, int createdCount, int updatedCount) {
                            synchronized (state) {
                                state.stage = DbMetaImportJobStage.IMPORTING_TABLES;
                                state.tableProcessed = processed;
                                state.tableCreatedCount = createdCount;
                                state.tableUpdatedCount = updatedCount;
                                state.progressPercent = calculateProgressPercent(state);
                            }
                        }

                        @Override
                        public void onFieldProgress(int processed, int total, int createdCount, int updatedCount) {
                            synchronized (state) {
                                state.stage = DbMetaImportJobStage.IMPORTING_FIELDS;
                                state.fieldProcessed = processed;
                                state.fieldCreatedCount = createdCount;
                                state.fieldUpdatedCount = updatedCount;
                                state.progressPercent = calculateProgressPercent(state);
                            }
                        }

                        @Override
                        public void onIndexProgress(int processed, int total, int createdCount, int updatedCount) {
                            synchronized (state) {
                                state.stage = DbMetaImportJobStage.IMPORTING_INDEXES;
                                state.indexProcessed = processed;
                                state.indexCreatedCount = createdCount;
                                state.indexUpdatedCount = updatedCount;
                                state.progressPercent = calculateProgressPercent(state);
                            }
                        }
                    }
            );
            synchronized (state) {
                state.status = DbMetaImportJobStatus.COMPLETED;
                state.stage = DbMetaImportJobStage.COMPLETED;
                state.progressPercent = 100;
                state.result = result;
                state.message = "导入完成";
                state.pushMessage("导入完成");
            }
        } catch (Exception ex) {
            log.error("数据库元数据异步导入失败, jobId={}, sourceKey={}, fileName={}", state.jobId, state.sourceKey, state.fileName, ex);
            synchronized (state) {
                state.status = DbMetaImportJobStatus.FAILED;
                state.stage = DbMetaImportJobStage.FAILED;
                state.message = resolveRootCauseMessage(ex);
                state.pushMessage("导入失败: " + state.message);
            }
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignore) {
            }
        }
    }

    private int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private int calculateProgressPercent(ImportJobState state) {
        if (state.status == DbMetaImportJobStatus.COMPLETED) {
            return 100;
        }
        if (state.status == DbMetaImportJobStatus.FAILED) {
            return Math.max(state.progressPercent, 1);
        }
        double parseProgress = 10D;
        double tableProgress = 20D * ratio(state.tableProcessed, state.tableTotal);
        double fieldProgress = 60D * ratio(state.fieldProcessed, state.fieldTotal);
        double indexProgress = 8D * ratio(state.indexProcessed, state.indexTotal);
        double finalizeProgress = state.stage == DbMetaImportJobStage.FINALIZING || state.stage == DbMetaImportJobStage.COMPLETED ? 2D : 0D;
        return (int) Math.min(99, Math.round(parseProgress + tableProgress + fieldProgress + indexProgress + finalizeProgress));
    }

    private double ratio(int processed, int total) {
        if (total <= 0) {
            return 1D;
        }
        return Math.min(1D, Math.max(0D, processed / (double) total));
    }

    private String resolveFilename(MultipartFile file) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        return StringUtils.hasText(originalFilename) ? originalFilename : "db-meta-import";
    }

    private String resolveSuffix(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) {
            return ".tmp";
        }
        return fileName.substring(index);
    }

    private String resolveRootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage()) ? current.getMessage() : throwable.getClass().getSimpleName();
    }

    private static final class ImportJobState {

        private final String jobId;
        private final String sourceKey;
        private final String fileName;
        private final String contentType;
        private final Deque<String> recentMessages = new ArrayDeque<>();

        private DbMetaImportJobStatus status = DbMetaImportJobStatus.PENDING;
        private DbMetaImportJobStage stage = DbMetaImportJobStage.QUEUED;
        private int progressPercent = 0;
        private String message = "任务已创建，等待处理";
        private int tableTotal;
        private int tableProcessed;
        private int tableCreatedCount;
        private int tableUpdatedCount;
        private int fieldTotal;
        private int fieldProcessed;
        private int fieldCreatedCount;
        private int fieldUpdatedCount;
        private int indexTotal;
        private int indexProcessed;
        private int indexCreatedCount;
        private int indexUpdatedCount;
        private DbMetaImportResultDTO result;

        private ImportJobState(String jobId, String sourceKey, String fileName, String contentType) {
            this.jobId = jobId;
            this.sourceKey = sourceKey;
            this.fileName = fileName;
            this.contentType = contentType;
            pushMessage("任务已创建，等待后台处理");
        }

        private void pushMessage(String text) {
            if (!StringUtils.hasText(text)) {
                return;
            }
            if (recentMessages.size() >= MAX_RECENT_MESSAGES) {
                recentMessages.removeFirst();
            }
            recentMessages.addLast(text);
        }

        private DbMetaImportJobProgressDTO toProgressDTO() {
            return DbMetaImportJobProgressDTO.builder()
                    .jobId(jobId)
                    .sourceKey(sourceKey)
                    .fileName(fileName)
                    .status(status)
                    .stage(stage)
                    .progressPercent(progressPercent)
                    .message(message)
                    .recentMessages(new ArrayList<>(recentMessages))
                    .summary(DbMetaImportProgressSummaryDTO.builder()
                            .tableTotal(tableTotal)
                            .tableProcessed(tableProcessed)
                            .tableCreatedCount(tableCreatedCount)
                            .tableUpdatedCount(tableUpdatedCount)
                            .fieldTotal(fieldTotal)
                            .fieldProcessed(fieldProcessed)
                            .fieldCreatedCount(fieldCreatedCount)
                            .fieldUpdatedCount(fieldUpdatedCount)
                            .indexTotal(indexTotal)
                            .indexProcessed(indexProcessed)
                            .indexCreatedCount(indexCreatedCount)
                            .indexUpdatedCount(indexUpdatedCount)
                            .build())
                    .result(result)
                    .build();
        }
    }
}
