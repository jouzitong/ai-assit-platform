package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.DbMetaImportJobEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportJobProgressDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportProgressSummaryDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.importer.DbMetaImportData;
import ai.platform.aiassit.db.engine.meta.enums.DbMetaImportJobStage;
import ai.platform.aiassit.db.engine.meta.enums.DbMetaImportJobStatus;
import ai.platform.aiassit.db.engine.meta.mapper.DbMetaImportJobMapper;
import ai.platform.aiassit.db.engine.meta.service.DbMetaImportJobService;
import ai.platform.aiassit.db.engine.meta.service.importer.DbMetaImportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.security.auth.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DbMetaImportJobServiceImpl implements DbMetaImportJobService {

    private static final int MAX_RECENT_MESSAGES = 8;
    private static final long STREAM_IDLE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final List<DbMetaImportService> importServices;
    private final DbMetaImportExecutor importExecutor;
    private final DbMetaImportJobMapper jobMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final ExecutorService jobExecutorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("db-meta-import-job");
        thread.setDaemon(true);
        return thread;
    });

    public DbMetaImportJobServiceImpl(
            List<DbMetaImportService> importServices,
            DbMetaImportExecutor importExecutor,
            DbMetaImportJobMapper jobMapper,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.importServices = importServices;
        this.importExecutor = importExecutor;
        this.jobMapper = jobMapper;
        this.objectMapper = objectMapper;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public SseEmitter streamImport(String sourceKey, MultipartFile file) throws IOException {
        DbMetaImportJobEntity job = createPendingJob(sourceKey, file);
        SseEmitter emitter = new SseEmitter(STREAM_IDLE_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(ex -> closed.set(true));
        sendProgressEvent(emitter, closed, "progress", toProgressDTO(job));

        UserContext userContext = SecurityContextHolder.get();
        Path tempFile = createTempFile(file, job.getFileName());
        jobExecutorService.submit(() -> {
            SecurityContextHolder.set(userContext);
            try {
                runJob(job.getJobId(), tempFile, progress -> {
                    String eventName = resolveEventName(progress);
                    sendProgressEvent(emitter, closed, eventName, progress);
                    if ("complete".equals(eventName) || "failed".equals(eventName)) {
                        try {
                            emitter.complete();
                        } catch (Exception ignore) {
                        }
                    }
                });
            } finally {
                SecurityContextHolder.clear();
            }
        });
        return emitter;
    }

    @PreDestroy
    public void shutdown() {
        jobExecutorService.shutdownNow();
    }

    private void runJob(String jobId, Path tempFile, JobProgressConsumer progressConsumer) {
        DbMetaImportJobEntity job = findRequiredJob(jobId);
        try {
            notifyProgress(updateJob(job, entity -> {
                entity.setStatus(DbMetaImportJobStatus.RUNNING);
                entity.setStage(DbMetaImportJobStage.PARSING);
                entity.setProgressPercent(5);
                entity.setMessage("正在解析导入文件");
                pushRecentMessage(entity, "导入任务已开始，正在解析文件");
            }), progressConsumer);

            MultipartFile storedFile = new TempFileMultipartFile(tempFile, job.getFileName(), job.getContentType());
            DbMetaImportService importService = importServices.stream()
                    .filter(service -> service.supports(storedFile))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("暂不支持的导入文件格式: " + job.getFileName()));
            DbMetaImportData importData = importService.parse(job.getSourceKey(), storedFile);

            notifyProgress(updateJob(job, entity -> {
                entity.setTableTotal(safeSize(importData.getTables()));
                entity.setFieldTotal(safeSize(importData.getFields()));
                entity.setIndexTotal(safeSize(importData.getIndexes()));
                String parsedMessage = String.format("解析完成：%d 张表，%d 个字段，%d 个索引",
                        entity.getTableTotal(), entity.getFieldTotal(), entity.getIndexTotal());
                entity.setMessage(parsedMessage);
                pushRecentMessage(entity, parsedMessage);
                entity.setProgressPercent(calculateProgressPercent(entity));
            }), progressConsumer);

            DbMetaImportResultDTO result = importExecutor.importData(
                    job.getSourceKey(),
                    storedFile,
                    importService.getFormat(),
                    importData,
                    new DbMetaImportProgressListener() {
                        @Override
                        public void onStageChanged(DbMetaImportJobStage stage, String stageMessage) {
                            notifyProgress(updateJob(job, entity -> {
                                entity.setStage(stage);
                                entity.setMessage(stageMessage);
                                pushRecentMessage(entity, stageMessage);
                                entity.setProgressPercent(calculateProgressPercent(entity));
                            }), progressConsumer);
                        }

                        @Override
                        public void onTableProgress(int processed, int total, int createdCount, int updatedCount) {
                            notifyProgress(updateJob(job, entity -> {
                                entity.setStage(DbMetaImportJobStage.IMPORTING_TABLES);
                                entity.setTableProcessed(processed);
                                entity.setTableCreatedCount(createdCount);
                                entity.setTableUpdatedCount(updatedCount);
                                entity.setProgressPercent(calculateProgressPercent(entity));
                            }), progressConsumer);
                        }

                        @Override
                        public void onFieldProgress(int processed, int total, int createdCount, int updatedCount) {
                            notifyProgress(updateJob(job, entity -> {
                                entity.setStage(DbMetaImportJobStage.IMPORTING_FIELDS);
                                entity.setFieldProcessed(processed);
                                entity.setFieldCreatedCount(createdCount);
                                entity.setFieldUpdatedCount(updatedCount);
                                entity.setProgressPercent(calculateProgressPercent(entity));
                            }), progressConsumer);
                        }

                        @Override
                        public void onIndexProgress(int processed, int total, int createdCount, int updatedCount) {
                            notifyProgress(updateJob(job, entity -> {
                                entity.setStage(DbMetaImportJobStage.IMPORTING_INDEXES);
                                entity.setIndexProcessed(processed);
                                entity.setIndexCreatedCount(createdCount);
                                entity.setIndexUpdatedCount(updatedCount);
                                entity.setProgressPercent(calculateProgressPercent(entity));
                            }), progressConsumer);
                        }
                    }
            );

            notifyProgress(updateJob(job, entity -> {
                entity.setStatus(DbMetaImportJobStatus.COMPLETED);
                entity.setStage(DbMetaImportJobStage.COMPLETED);
                entity.setProgressPercent(100);
                entity.setResultJson(writeResult(result));
                entity.setMessage("导入完成");
                pushRecentMessage(entity, "导入完成");
            }), progressConsumer);
        } catch (Exception ex) {
            log.error("数据库元数据异步导入失败, jobId={}, sourceKey={}, fileName={}", job.getJobId(), job.getSourceKey(), job.getFileName(), ex);
            notifyProgress(updateJob(job, entity -> {
                entity.setStatus(DbMetaImportJobStatus.FAILED);
                entity.setStage(DbMetaImportJobStage.FAILED);
                entity.setMessage(resolveRootCauseMessage(ex));
                entity.setProgressPercent(Math.max(defaultNumber(entity.getProgressPercent()), 1));
                pushRecentMessage(entity, "导入失败: " + entity.getMessage());
            }), progressConsumer);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignore) {
            }
        }
    }

    private DbMetaImportJobEntity createPendingJob(String sourceKey, MultipartFile file) {
        if (!StringUtils.hasText(sourceKey)) {
            throw new IllegalArgumentException("缺少 sourceKey");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }
        DbMetaImportJobEntity job = DbMetaImportJobEntity.builder()
                .jobId(UUID.randomUUID().toString())
                .sourceKey(sourceKey.trim())
                .fileName(resolveFilename(file))
                .contentType(file.getContentType())
                .status(DbMetaImportJobStatus.PENDING)
                .stage(DbMetaImportJobStage.QUEUED)
                .progressPercent(0)
                .message("任务已创建，等待处理")
                .recentMessagesJson(writeRecentMessages(List.of("任务已创建，等待后台处理")))
                .tableTotal(0)
                .tableProcessed(0)
                .tableCreatedCount(0)
                .tableUpdatedCount(0)
                .fieldTotal(0)
                .fieldProcessed(0)
                .fieldCreatedCount(0)
                .fieldUpdatedCount(0)
                .indexTotal(0)
                .indexProcessed(0)
                .indexCreatedCount(0)
                .indexUpdatedCount(0)
                .build();
        jobMapper.insert(job);
        return job;
    }

    private Path createTempFile(MultipartFile file, String fileName) throws IOException {
        String suffix = resolveSuffix(fileName);
        Path tempFile = Files.createTempFile("db-meta-import-", suffix);
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private DbMetaImportJobEntity updateJob(DbMetaImportJobEntity job, JobMutator mutator) {
        DbMetaImportJobEntity latest = requiresNewTransactionTemplate.execute(status -> {
            DbMetaImportJobEntity current = findRequiredJob(job.getJobId());
            mutator.mutate(current);
            current.setRecentMessagesJson(writeRecentMessages(readRecentMessages(current.getRecentMessagesJson())));
            if (jobMapper.updateById(current) <= 0) {
                throw new IllegalStateException("更新导入任务失败: " + current.getJobId());
            }
            return current;
        });
        if (latest == null) {
            throw new IllegalStateException("更新导入任务失败: " + job.getJobId());
        }
        copyJobState(latest, job);
        return latest;
    }

    private void notifyProgress(DbMetaImportJobEntity entity, JobProgressConsumer progressConsumer) {
        if (progressConsumer != null) {
            progressConsumer.accept(toProgressDTO(entity));
        }
    }

    private void sendProgressEvent(SseEmitter emitter, AtomicBoolean closed, String eventName, DbMetaImportJobProgressDTO progress) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(progress));
        } catch (Exception ex) {
            closed.set(true);
            try {
                emitter.completeWithError(ex);
            } catch (Exception ignore) {
            }
        }
    }

    private String resolveEventName(DbMetaImportJobProgressDTO progress) {
        if (progress == null || progress.getStatus() == null) {
            return "progress";
        }
        if (progress.getStatus() == DbMetaImportJobStatus.COMPLETED) {
            return "complete";
        }
        if (progress.getStatus() == DbMetaImportJobStatus.FAILED) {
            return "failed";
        }
        return "progress";
    }

    private DbMetaImportJobEntity findRequiredJob(String jobId) {
        DbMetaImportJobEntity entity = jobMapper.selectOne(new LambdaQueryWrapper<DbMetaImportJobEntity>()
                .eq(DbMetaImportJobEntity::getJobId, jobId));
        if (entity == null) {
            throw new IllegalArgumentException("导入任务不存在: " + jobId);
        }
        return entity;
    }

    private DbMetaImportJobProgressDTO toProgressDTO(DbMetaImportJobEntity entity) {
        return DbMetaImportJobProgressDTO.builder()
                .jobId(entity.getJobId())
                .sourceKey(entity.getSourceKey())
                .fileName(entity.getFileName())
                .status(entity.getStatus())
                .stage(entity.getStage())
                .progressPercent(defaultNumber(entity.getProgressPercent()))
                .message(entity.getMessage())
                .recentMessages(readRecentMessages(entity.getRecentMessagesJson()))
                .summary(DbMetaImportProgressSummaryDTO.builder()
                        .tableTotal(defaultNumber(entity.getTableTotal()))
                        .tableProcessed(defaultNumber(entity.getTableProcessed()))
                        .tableCreatedCount(defaultNumber(entity.getTableCreatedCount()))
                        .tableUpdatedCount(defaultNumber(entity.getTableUpdatedCount()))
                        .fieldTotal(defaultNumber(entity.getFieldTotal()))
                        .fieldProcessed(defaultNumber(entity.getFieldProcessed()))
                        .fieldCreatedCount(defaultNumber(entity.getFieldCreatedCount()))
                        .fieldUpdatedCount(defaultNumber(entity.getFieldUpdatedCount()))
                        .indexTotal(defaultNumber(entity.getIndexTotal()))
                        .indexProcessed(defaultNumber(entity.getIndexProcessed()))
                        .indexCreatedCount(defaultNumber(entity.getIndexCreatedCount()))
                        .indexUpdatedCount(defaultNumber(entity.getIndexUpdatedCount()))
                        .build())
                .result(readResult(entity.getResultJson()))
                .build();
    }

    private void pushRecentMessage(DbMetaImportJobEntity entity, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        Deque<String> messages = new ArrayDeque<>(readRecentMessages(entity.getRecentMessagesJson()));
        if (messages.size() >= MAX_RECENT_MESSAGES) {
            messages.removeFirst();
        }
        messages.addLast(text);
        entity.setRecentMessagesJson(writeRecentMessages(new ArrayList<>(messages)));
    }

    private List<String> readRecentMessages(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            log.warn("读取导入任务 recentMessages 失败", ex);
            return new ArrayList<>();
        }
    }

    private String writeRecentMessages(List<String> messages) {
        try {
            return objectMapper.writeValueAsString(messages == null ? List.of() : messages);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化导入任务 recentMessages 失败", ex);
        }
    }

    private DbMetaImportResultDTO readResult(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DbMetaImportResultDTO.class);
        } catch (Exception ex) {
            log.warn("读取导入任务 result 失败", ex);
            return null;
        }
    }

    private String writeResult(DbMetaImportResultDTO result) {
        if (result == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化导入任务 result 失败", ex);
        }
    }

    private int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private int calculateProgressPercent(DbMetaImportJobEntity state) {
        if (state.getStatus() == DbMetaImportJobStatus.COMPLETED) {
            return 100;
        }
        if (state.getStatus() == DbMetaImportJobStatus.FAILED) {
            return Math.max(defaultNumber(state.getProgressPercent()), 1);
        }
        double parseProgress = 10D;
        double tableProgress = 20D * ratio(defaultNumber(state.getTableProcessed()), defaultNumber(state.getTableTotal()));
        double fieldProgress = 60D * ratio(defaultNumber(state.getFieldProcessed()), defaultNumber(state.getFieldTotal()));
        double indexProgress = 8D * ratio(defaultNumber(state.getIndexProcessed()), defaultNumber(state.getIndexTotal()));
        double finalizeProgress = state.getStage() == DbMetaImportJobStage.FINALIZING || state.getStage() == DbMetaImportJobStage.COMPLETED ? 2D : 0D;
        return (int) Math.min(99, Math.round(parseProgress + tableProgress + fieldProgress + indexProgress + finalizeProgress));
    }

    private double ratio(int processed, int total) {
        if (total <= 0) {
            return 1D;
        }
        return Math.min(1D, Math.max(0D, processed / (double) total));
    }

    private int defaultNumber(Integer value) {
        return value == null ? 0 : value;
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

    private void copyJobState(DbMetaImportJobEntity source, DbMetaImportJobEntity target) {
        target.setId(source.getId());
        target.setSourceKey(source.getSourceKey());
        target.setFileName(source.getFileName());
        target.setContentType(source.getContentType());
        target.setStatus(source.getStatus());
        target.setStage(source.getStage());
        target.setProgressPercent(source.getProgressPercent());
        target.setMessage(source.getMessage());
        target.setRecentMessagesJson(source.getRecentMessagesJson());
        target.setTableTotal(source.getTableTotal());
        target.setTableProcessed(source.getTableProcessed());
        target.setTableCreatedCount(source.getTableCreatedCount());
        target.setTableUpdatedCount(source.getTableUpdatedCount());
        target.setFieldTotal(source.getFieldTotal());
        target.setFieldProcessed(source.getFieldProcessed());
        target.setFieldCreatedCount(source.getFieldCreatedCount());
        target.setFieldUpdatedCount(source.getFieldUpdatedCount());
        target.setIndexTotal(source.getIndexTotal());
        target.setIndexProcessed(source.getIndexProcessed());
        target.setIndexCreatedCount(source.getIndexCreatedCount());
        target.setIndexUpdatedCount(source.getIndexUpdatedCount());
        target.setResultJson(source.getResultJson());
    }

    @FunctionalInterface
    private interface JobMutator {
        void mutate(DbMetaImportJobEntity entity);
    }

    @FunctionalInterface
    private interface JobProgressConsumer {
        void accept(DbMetaImportJobProgressDTO progress);
    }
}
