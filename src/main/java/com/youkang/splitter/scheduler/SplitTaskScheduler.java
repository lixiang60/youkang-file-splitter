package com.youkang.splitter.scheduler;

import com.youkang.splitter.runner.SplitTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 拆分任务定时调度器
 * 通过 ReentrantLock 保证同一时刻只有一个扫描周期在执行
 *
 * @author youkang
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SplitTaskScheduler {

    private final SplitTaskRunner taskRunner;
    private final ReentrantLock lock = new ReentrantLock();

    @Scheduled(cron = "${splitter.scan-cron}")
    public void scanAndSplit() {
        if (!lock.tryLock()) {
            log.warn("上一轮扫描尚未结束，本次跳过");
            return;
        }
        try {
            log.info("定时扫描任务开始...");
            taskRunner.run();
            log.info("定时扫描任务结束");
        } catch (Exception e) {
            log.error("定时扫描任务执行异常", e);
        } finally {
            lock.unlock();
        }
    }
}
