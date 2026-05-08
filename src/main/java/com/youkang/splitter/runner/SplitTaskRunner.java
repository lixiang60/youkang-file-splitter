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
    private final FileSplitterService fileSplitterService;
    private final TaskRecorder taskRecorder;

    /**
     * 执行一次扫描与处理
     * 扫描 /data/youkang/Seq/ 下的所有文件夹，进入后查找 results 或 07_results 子目录作为实际订单入口
     */
    public void run() {
        Path seqDir = Paths.get(props.getInboxDir());
        if (!Files.exists(seqDir)) {
            log.warn("Seq 目录不存在：{}", seqDir);
            return;
        }

        List<Path> orderDirs = listDirectories(seqDir);
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

        // 查找 results 或 07_results 子目录
        Path resultsDir = findResultsDir(orderDir);
        if (resultsDir == null) {
            log.warn("订单目录下未找到 results 或 07_results，跳过：{}", orderDir);
            return;
        }

        List<Path> samples = listDirectories(resultsDir);
        if (samples.isEmpty()) {
            log.debug("results 目录下无样品，跳过：{}", resultsDir);
            return;
        }

        String taskId = taskRecorder.start(orderName, 0L);
        SplitOutcome outcome = new SplitOutcome();
        outcome.setTaskId(taskId);
        outcome.setZipName(orderName);
        outcome.setZipSizeBytes(0L);
        outcome.setStartTime(java.time.LocalDateTime.now());

        try {
            Path resultDir = Paths.get(props.getResultDir());
            Files.createDirectories(resultDir);
            Path targetOrderDir = resultDir.resolve(orderName);

            // 拆分：从 resultsDir 到 resultDir/orderName
            SplitBatchResult batchResult = fileSplitterService.splitSingleOrder(resultsDir, targetOrderDir);

            outcome.setOrderCount(1);
            outcome.setSampleTotal(batchResult.getClassifications().size());
            outcome.setSampleNormal((int) batchResult.getClassifications().values().stream()
                    .filter(c -> c == SampleFolderClassification.NORMAL).count());
            outcome.setSampleEmpty((int) batchResult.getClassifications().values().stream()
                    .filter(c -> c == SampleFolderClassification.EMPTY).count());
            outcome.setSampleFailed(batchResult.getSampleFailed());
            batchResult.getErrorMessages().forEach(outcome::addSampleError);
            outcome.setOutputZipPath(targetOrderDir.toString());

            if (batchResult.getSampleFailed() > 0) {
                outcome.setStatus("PARTIAL_SUCCESS");
                taskRecorder.markPartial(taskId, outcome);
            } else {
                outcome.setStatus("SUCCESS");
                taskRecorder.complete(taskId, outcome);
            }
            archiveOrDeleteDir(orderDir);

        } catch (Exception e) {
            log.error("订单处理失败：{}", orderName, e);
            outcome.setStatus("FAILED");
            outcome.setErrorMessage(truncate(e.getMessage()));
            taskRecorder.markFailed(taskId, orderName, outcome.getErrorMessage());
            try {
                archiveOrDeleteDir(orderDir);
            } catch (IOException ioEx) {
                log.error("归档失败订单目录失败：{}", orderDir, ioEx);
            }
        }
    }


    // ===================== 文件夹处理辅助方法 =====================

    /**
     * 在订单目录下查找 results 或 07_results 子目录（两者只会存在一个）
     */
    private Path findResultsDir(Path orderDir) {
        Path resultsDir = orderDir.resolve("results");
        if (Files.exists(resultsDir) && Files.isDirectory(resultsDir)) {
            return resultsDir;
        }
        Path results07Dir = orderDir.resolve("07_results");
        if (Files.exists(results07Dir) && Files.isDirectory(results07Dir)) {
            return results07Dir;
        }
        return null;
    }

    private List<Path> listDirectories(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描目录异常：{}", dir, e);
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
            // Windows 下 Files.move 对非空目录不支持 REPLACE_EXISTING，使用 commons-io
            org.apache.commons.io.FileUtils.moveDirectory(orderDir.toFile(), target.toFile());
            log.debug("已归档原订单目录：{}", target);
        }
    }

    private void deleteDirectoryWithRetry(Path dir) throws IOException {
        int retries = props.getRetryTimes();
        for (int i = 0; i <= retries; i++) {
            try {
                org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile());
                return;
            } catch (Exception e) {
                if (i == retries) {
                    throw new IOException("清理目录失败：" + dir, e);
                }
                try {
                    Thread.sleep(props.getRetryIntervalMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("清理目录被中断：" + dir, ie);
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
