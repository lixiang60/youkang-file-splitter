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
 * 负责：扫描 -> 就绪检测 -> 移动 -> 解压 -> 拆分 -> 打包 -> 归档 -> 清理 -> 记录
 *
 * @author youkang
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SplitTaskRunner {

    private final SplitterProperties props;
    private final ReadyChecker readyChecker;
    private final ZipExtractor zipExtractor;
    private final ZipPackager zipPackager;
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

        List<Path> zipFiles = listZipFiles(inboxDir);
        if (zipFiles.isEmpty()) {
            log.debug("本次扫描未发现待处理 zip 文件");
            return;
        }

        log.info("本次扫描发现 {} 个待处理 zip 文件", zipFiles.size());
        for (Path zipFile : zipFiles) {
            processSingleZip(zipFile);
        }
    }

    private void processSingleZip(Path zipFile) {
        String zipName = zipFile.getFileName().toString();
        long zipSize;
        try {
            zipSize = Files.size(zipFile);
        } catch (IOException e) {
            log.error("无法读取文件大小：{}", zipFile, e);
            return;
        }

        // 1. 就绪检测
        if (!readyChecker.isReady(zipFile, props.getReadyCheckIntervalSeconds() * 1000L)) {
            log.info("文件尚未就绪，跳过：{}", zipName);
            return;
        }

        // 2. 初始化任务记录
        Long recordId = taskRecorder.start(zipName, zipSize);
        String taskId = recordId != null ? null : UUID.randomUUID().toString();
        SplitOutcome outcome = new SplitOutcome();
        outcome.setTaskId(taskId);
        outcome.setZipName(zipName);
        outcome.setZipSizeBytes(zipSize);
        outcome.setStartTime(java.time.LocalDateTime.now());

        Path workDir = null;
        try {
            // 3. 创建工作目录并移动源文件
            String uuid = UUID.randomUUID().toString();
            workDir = Paths.get(props.getWorkDir()).resolve(uuid);
            Files.createDirectories(workDir);
            Path sourceZip = workDir.resolve("source.zip");
            moveWithRetry(zipFile, sourceZip);

            // 4. 解压
            Path extractedDir = workDir.resolve("extracted");
            Files.createDirectories(extractedDir);
            zipExtractor.extract(sourceZip, extractedDir);

            // 5. 拆分
            Path splitDir = workDir.resolve("split");
            Files.createDirectories(splitDir);
            SplitBatchResult batchResult = fileSplitterService.split(extractedDir, splitDir);

            // 统计
            outcome.setOrderCount((int) Files.list(extractedDir).filter(Files::isDirectory).count());
            outcome.setSampleTotal(batchResult.getClassifications().size());
            outcome.setSampleNormal((int) batchResult.getClassifications().values().stream()
                    .filter(c -> c == SampleFolderClassification.NORMAL).count());
            outcome.setSampleEmpty((int) batchResult.getClassifications().values().stream()
                    .filter(c -> c == SampleFolderClassification.EMPTY).count());
            outcome.setSampleFailed(batchResult.getSampleFailed());
            batchResult.getErrorMessages().forEach(outcome::addSampleError);

            // 6. 重新打包
            Path outputDir = Paths.get(props.getOutputDir());
            Files.createDirectories(outputDir);
            Path outputZip = outputDir.resolve(zipName);
            zipPackager.packageDir(splitDir, outputZip);
            outcome.setOutputZipPath(outputZip.toString());

            // 7. 归档或删除原文件
            archiveOrDelete(sourceZip);

            // 8. 状态判定
            if (batchResult.getSampleFailed() > 0) {
                outcome.setStatus("PARTIAL_SUCCESS");
                taskRecorder.markPartial(recordId, taskId, outcome);
            } else {
                outcome.setStatus("SUCCESS");
                taskRecorder.complete(recordId, taskId, outcome);
            }

        } catch (Exception e) {
            log.error("zip 处理失败：{}", zipName, e);
            outcome.setStatus("FAILED");
            outcome.setErrorMessage(truncate(e.getMessage()));
            moveToFailed(zipFile);
            taskRecorder.markFailed(recordId, taskId, zipName, outcome.getErrorMessage());
        } finally {
            // 9. 清理工作目录
            if (workDir != null) {
                deleteDirectoryWithRetry(workDir);
            }
        }
    }

    private List<Path> listZipFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().toLowerCase().endsWith(".zip"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描入口目录异常：{}", dir, e);
            return List.of();
        }
    }

    private void moveWithRetry(Path source, Path target) throws IOException {
        int retries = props.getRetryTimes();
        long interval = props.getRetryIntervalMs();
        for (int i = 0; i <= retries; i++) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                if (i == retries) {
                    throw new IOException("移动文件失败（重试" + retries + "次）：" + source + " -> " + target, e);
                }
                log.warn("文件移动失败，{}ms 后重试 ({}/{})：{}", interval, i + 1, retries, source);
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("移动文件线程被中断", ie);
                }
            }
        }
    }

    private void archiveOrDelete(Path sourceZip) throws IOException {
        if (props.isDeleteAfterArchive()) {
            Files.deleteIfExists(sourceZip);
            log.debug("已删除源 zip：{}", sourceZip.getFileName());
        } else {
            Path archiveDir = Paths.get(props.getArchiveDir());
            Files.createDirectories(archiveDir);
            Path target = archiveDir.resolve(sourceZip.getFileName());
            Files.move(sourceZip, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("已归档源 zip：{}", target);
        }
    }

    private void moveToFailed(Path originalZip) {
        try {
            Path failedDir = Paths.get(props.getFailedDir());
            Files.createDirectories(failedDir);
            Path target = failedDir.resolve(originalZip.getFileName());
            Files.move(originalZip, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("已将异常 zip 移至失败目录：{}", target);
        } catch (IOException e) {
            log.error("移动异常 zip 到失败目录失败：{}", originalZip, e);
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
                    log.error("清理工作目录失败：{}", dir, e);
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
