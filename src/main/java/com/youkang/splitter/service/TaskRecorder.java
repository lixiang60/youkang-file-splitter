package com.youkang.splitter.service;

import com.youkang.splitter.domain.SplitOutcome;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 任务记录写入器
 * 通过独立日志文件（splitter.log.task）记录每次 zip 任务的执行轨迹
 *
 * @author youkang
 */
@Slf4j
@Service
public class TaskRecorder {

    /**
     * 独立的任务日志 Logger，输出到 TASK_FILE appender
     */
    private static final Logger TASK_LOGGER = LoggerFactory.getLogger("com.youkang.splitter.TASK");

    /**
     * 开始一个新任务，生成 UUID 并记录 START 日志
     *
     * @return taskId（UUID）
     */
    public String start(String zipName, long zipSizeBytes) {
        String taskId = UUID.randomUUID().toString();
        TASK_LOGGER.info("[TASK][START] taskId={} | zipName={} | zipSizeBytes={} | startTime={}",
                taskId, zipName, zipSizeBytes, LocalDateTime.now());
        return taskId;
    }

    /**
     * 标记任务为成功
     */
    public void complete(String taskId, SplitOutcome outcome) {
        TASK_LOGGER.info(buildCompleteLog(taskId, outcome));
    }

    /**
     * 标记任务为部分成功（存在个别样品异常）
     */
    public void markPartial(String taskId, SplitOutcome outcome) {
        TASK_LOGGER.warn(buildCompleteLog(taskId, outcome));
    }

    /**
     * 标记任务为失败
     */
    public void markFailed(String taskId, String zipName, String errorMessage) {
        TASK_LOGGER.error("[TASK][FAILED] taskId={} | zipName={} | endTime={} | errorMessage={}",
                taskId, zipName, LocalDateTime.now(), truncate(errorMessage, 2000));
    }

    private String buildCompleteLog(String taskId, SplitOutcome outcome) {
        StringBuilder sb = new StringBuilder();
        sb.append("[TASK][COMPLETE] taskId=").append(taskId);
        sb.append(" | zipName=").append(outcome.getZipName());
        sb.append(" | status=").append(outcome.getStatus());
        sb.append(" | startTime=").append(outcome.getStartTime());
        sb.append(" | endTime=").append(LocalDateTime.now());
        sb.append(" | durationMs=").append(computeDuration(outcome));
        sb.append(" | orderCount=").append(outcome.getOrderCount());
        sb.append(" | sampleTotal=").append(outcome.getSampleTotal());
        sb.append(" | sampleNormal=").append(outcome.getSampleNormal());
        sb.append(" | sampleEmpty=").append(outcome.getSampleEmpty());
        sb.append(" | sampleFailed=").append(outcome.getSampleFailed());
        if (outcome.getOutputZipPath() != null) {
            sb.append(" | outputZipPath=").append(outcome.getOutputZipPath());
        }
        if (!outcome.getSampleErrorMessages().isEmpty()) {
            sb.append(" | sampleErrors=").append(String.join("; ", outcome.getSampleErrorMessages()));
        }
        return sb.toString();
    }

    private Long computeDuration(SplitOutcome outcome) {
        if (outcome.getStartTime() == null) {
            return 0L;
        }
        return java.time.Duration.between(outcome.getStartTime(), LocalDateTime.now()).toMillis();
    }

    private String truncate(String s, int maxLength) {
        if (s == null || s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength);
    }
}
