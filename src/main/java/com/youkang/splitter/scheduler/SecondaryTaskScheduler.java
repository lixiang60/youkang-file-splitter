package com.youkang.splitter.scheduler;

import com.youkang.splitter.config.SplitterProperties;
import com.youkang.splitter.service.SecondaryClassificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * 二次分类定时调度器
 * 扫描 result-dir 下的订单目录，结合同名 Excel 明细表进行二次分类处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecondaryTaskScheduler {

    private static final Set<String> EXCEL_EXTENSIONS = Set.of("xls", "xlsx");

    private final SplitterProperties props;
    private final SecondaryClassificationService secondaryService;
    private final ReentrantLock lock = new ReentrantLock();

    @Scheduled(cron = "${splitter.secondary.scan-cron}")
    public void scanAndClassify() {
        if (!props.getSecondary().isEnabled()) {
            return;
        }

        if (!lock.tryLock()) {
            log.warn("上一轮二次分类扫描尚未结束，本次跳过");
            return;
        }

        try {
            log.info("二次分类定时扫描任务开始...");

            Path resultDir = Paths.get(props.getResultDir());
            if (!Files.exists(resultDir) || !Files.isDirectory(resultDir)) {
                log.warn("结果目录不存在：{}", resultDir);
                return;
            }

            try (Stream<Path> stream = Files.list(resultDir)) {
                stream.filter(Files::isDirectory)
                        .filter(this::shouldProcess)
                        .forEach(secondaryService::processOrder);
            }

            log.info("二次分类定时扫描任务结束");
        } catch (Exception e) {
            log.error("二次分类定时扫描任务执行异常", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 判断该订单目录是否应该被处理
     * 条件：1. 不是 -result 后缀的目录 2. 存在同名 Excel 3. 不存在对应的 -result 输出目录
     */
    private boolean shouldProcess(Path orderDir) {
        String orderName = orderDir.getFileName().toString();

        // 跳过已经处理过的结果目录
        if (orderName.endsWith("-result")) {
            return false;
        }

        Path parent = orderDir.getParent();
        if (parent == null) {
            return false;
        }

        // 检查是否存在对应的 -result 目录
        Path resultOutputDir = parent.resolve(orderName + "-result");
        if (Files.exists(resultOutputDir)) {
            return false;
        }

        // 检查是否存在同名 Excel
        boolean hasExcel = EXCEL_EXTENSIONS.stream()
                .map(ext -> parent.resolve(orderName + "." + ext))
                .anyMatch(p -> Files.exists(p) && Files.isRegularFile(p));

        return hasExcel;
    }
}
