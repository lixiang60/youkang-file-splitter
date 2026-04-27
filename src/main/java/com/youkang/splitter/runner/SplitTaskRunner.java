package com.youkang.splitter.runner;

import com.youkang.splitter.config.SplitterProperties;
import com.youkang.splitter.domain.SampleFolderClassification;
import com.youkang.splitter.domain.SplitBatchResult;
import com.youkang.splitter.domain.SplitOutcome;
import com.youkang.splitter.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 单次扫描周期的任务编排器
 * 负责：扫描 -> 拆分 -> 归档 -> 记录
 * （ZIP 解压/打包已注释掉，当前直接处理文件夹）
 *
 * @author youkang
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SplitTaskRunner {

    private final SplitterProperties props;
    // ZIP 相关服务已注释，后续如需恢复可直接启用
    // private final ReadyChecker readyChecker;
    // private final ZipExtractor zipExtractor;
    // private final ZipPackager zipPackager;
    private final FileSplitterService fileSplitterService;
    private final TaskRecorder taskRecorder;

    /**
     * 执行一次扫描与处理
     */
    public void run() {
        Path inboxDir = Paths.get(props.getInboxDir());
        if (!Files.exists(inboxDir)) {
            log.warn("入口目录不存在：{}", inboxDir);
            return;
        }

        List<Path> orderDirs = listOrderDirs(inboxDir);
        if (orderDirs.isEmpty()) {
            log.debug("本次扫描未发现待处理订单目录");
            return;
        }

        log.info("本次扫描发现 {} 个待处理订单目录", orderDirs.size());
        for (Path orderDir : orderDirs) {
            processSingleOrder(orderDir);
        }
    }

    private void processSingleOrder(Path orderDir) {
        String orderName = orderDir.getFileName().toString();

        String taskId = taskRecorder.start(orderName, 0L);
        SplitOutcome outcome = new SplitOutcome();
        outcome.setTaskId(taskId);
        outcome.setZipName(orderName);
        outcome.setZipSizeBytes(0L);
        outcome.setStartTime(java.time.LocalDateTime.now());

        try {
            Path outputDir = Paths.get(props.getOutputDir());
            Files.createDirectories(outputDir);
            Path targetOrderDir = outputDir.resolve(orderName);

            SplitBatchResult batchResult = fileSplitterService.splitSingleOrder(orderDir, targetOrderDir);

            outcome.setOrderCount(1);
            outcome.setSampleTotal(batchResult.getClassifications().size());
            outcome.setSampleNormal((int) batchResult.getClassifications().values().stream()
                    .filter(c -> c == SampleFolderClassification.NORMAL).count());
            outcome.setSampleEmpty((int) batchResult.getClassifications().values().stream()
                    .filter(c -> c == SampleFolderClassification.EMPTY).count());
            outcome.setSampleFailed(batchResult.getSampleFailed());
            batchResult.getErrorMessages().forEach(outcome::addSampleError);
            outcome.setOutputZipPath(targetOrderDir.toString());

            // 归档或删除原订单目录
            archiveOrDeleteDir(orderDir);

            if (batchResult.getSampleFailed() > 0) {
                outcome.setStatus("PARTIAL_SUCCESS");
                taskRecorder.markPartial(taskId, outcome);
            } else {
                outcome.setStatus("SUCCESS");
                taskRecorder.complete(taskId, outcome);
            }

        } catch (Exception e) {
            log.error("订单处理失败：{}", orderName, e);
            outcome.setStatus("FAILED");
            outcome.setErrorMessage(truncate(e.getMessage()));
            moveToFailed(orderDir);
            taskRecorder.markFailed(taskId, orderName, outcome.getErrorMessage());
        }
    }

    /*
    // ========== ZIP 模式旧逻辑（已注释，后续可恢复） ==========

    private void processSingleZip(Path zipFile) {
        String zipName = zipFile.getFileName().toString();
        long zipSize;
        try {
            zipSize = Files.size(zipFile);
        } catch (IOException e) {
            log.error("无法读取文件大小：{}", zipFile, e);
            return;
        }

        if (!readyChecker.isReady(zipFile, props.getReadyCheckIntervalSeconds() * 1000L)) {
            log.info("文件尚未就绪，跳过：{}", zipName);
            return;
        }

        String taskId = taskRecorder.start(zipName, zipSize);
        SplitOutcome outcome = new SplitOutcome();
        outcome.setTaskId(taskId);
        outcome.setZipName(zipName);
        outcome.setZipSizeBytes(zipSize);
        outcome.setStartTime(java.time.LocalDateTime.now());

        Path workDir = null;
        try {
            String uuid = UUID.randomUUID().toString();
            workDir = Paths.get(props.getWorkDir()).resolve(uuid);
            Files.createDirectories(workDir);
            Path sourceZip = workDir.resolve("source.zip");
            moveWithRetry(zipFile, sourceZip);

            Path extractedDir = workDir.resolve("extracted");
            Files.createDirectories(extractedDir);
            zipExtractor.extract(sourceZip, extractedDir);

            Path splitDir = workDir.resolve("split");
            Files.createDirectories(splitDir);
            SplitBatchResult batchResult = fileSplitterService.split(extractedDir, splitDir);

            outcome.setOrderCount((int) Files.list(extractedDir).filter(Files::isDirectory).count());
            outcome.setSampleTotal(batchResult.getClassifications().size());
            outcome.setSampleNormal((int) batchResult.getClassifications().values().stream()
                    .filter(c -> c == SampleFolderClassification.NORMAL).count());
            outcome.setSampleEmpty((int) batchResult.getClassifications().values().stream()
                    .filter(c -> c == SampleFolderClassification.EMPTY).count());
            outcome.setSampleFailed(batchResult.getSampleFailed());
            batchResult.getErrorMessages().forEach(outcome::addSampleError);

            Path outputDir = Paths.get(props.getOutputDir());
            Files.createDirectories(outputDir);
            Path outputZip = outputDir.resolve(zipName);
            zipPackager.packageDir(splitDir, outputZip);
            outcome.setOutputZipPath(outputZip.toString());

            archiveOrDelete(sourceZip);

            if (batchResult.getSampleFailed() > 0) {
                outcome.setStatus("PARTIAL_SUCCESS");
                taskRecorder.markPartial(taskId, outcome);
            } else {
                outcome.setStatus("SUCCESS");
                taskRecorder.complete(taskId, outcome);
            }

        } catch (Exception e) {
            log.error("zip 处理失败：{}", zipName, e);
            outcome.setStatus("FAILED");
            outcome.setErrorMessage(truncate(e.getMessage()));
            moveToFailed(zipFile);
            taskRecorder.markFailed(taskId, zipName, outcome.getErrorMessage());
        } finally {
            if (workDir != null) {
                deleteDirectoryWithRetry(workDir);
            }
        }
    }
    */

    // ===================== 文件夹处理辅助方法 =====================

    private List<Path> listOrderDirs(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描入口目录异常：{}", dir, e);
            return List.of();
        }
    }

    private void archiveOrDeleteDir(Path orderDir) throws IOException {
        if (props.isDeleteAfterArchive()) {
            deleteDirectoryWithRetry(orderDir);
            log.debug("已删除原订单目录：{}", orderDir.getFileName());
        } else {
            Path archiveDir = Paths.get(props.getArchiveDir());
            Files.createDirectories(archiveDir);
            Path target = archiveDir.resolve(orderDir.getFileName());
            if (Files.exists(target)) {
                deleteDirectoryWithRetry(target);
            }
            Files.move(orderDir, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("已归档原订单目录：{}", target);
        }
    }

    private void moveToFailed(Path orderDir) {
        try {
            Path failedDir = Paths.get(props.getFailedDir());
            Files.createDirectories(failedDir);
            Path target = failedDir.resolve(orderDir.getFileName());
            if (Files.exists(target)) {
                deleteDirectoryWithRetry(target);
            }
            Files.move(orderDir, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("已将异常订单目录移至失败目录：{}", target);
        } catch (IOException e) {
            log.error("移动异常订单目录到失败目录失败：{}", orderDir, e);
        }
    }

    private void deleteDirectoryWithRetry(Path dir) {
        int retries = props.getRetryTimes();
        for (int i = 0; i <= retries; i++) {
            try {
                org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile());
                return;
            } catch (Exception e) {
                if (i == retries) {
                    log.error("清理目录失败：{}", dir, e);
                    return;
                }
                try {
                    Thread.sleep(props.getRetryIntervalMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private String truncate(String s) {
        if (s == null || s.length() <= 2000) {
            return s;
        }
        return s.substring(0, 2000);
    }
}
