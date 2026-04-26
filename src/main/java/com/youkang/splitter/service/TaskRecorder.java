package com.youkang.splitter.service;

import com.youkang.splitter.domain.SplitOutcome;
import com.youkang.splitter.domain.SplitTaskRecord;
import com.youkang.splitter.repository.SplitTaskRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 任务记录写入器
 * 封装 SQLite 任务持久化，写失败不影响主链路
 *
 * @author youkang
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskRecorder {

    private final SplitTaskRecordRepository repository;

    /**
     * 开始一个新任务，生成 UUID，写入初始记录，返回主键
     */
    public Long start(String zipName, long zipSizeBytes) {
        try {
            SplitTaskRecord record = new SplitTaskRecord();
            record.setTaskId(UUID.randomUUID().toString());
            record.setZipName(zipName);
            record.setZipSizeBytes(zipSizeBytes);
            record.setStartTime(LocalDateTime.now());
            record.setStatus("RUNNING");
            return repository.insert(record);
        } catch (Exception e) {
            log.error("写入任务初始记录失败：zipName={}", zipName, e);
            return null;
        }
    }

    /**
     * 标记任务为失败
     */
    public void markFailed(Long recordId, String taskId, String zipName, String errorMessage) {
        try {
            if (recordId == null) {
                recordId = resolveRecordId(taskId, zipName);
            }
            if (recordId == null) {
                log.warn("无法定位任务记录，放弃标记失败：taskId={}, zipName={}", taskId, zipName);
                return;
            }
            SplitTaskRecord record = repository.findById(recordId);
            if (record == null) {
                return;
            }
            record.setEndTime(LocalDateTime.now());
            record.setDurationMs(computeDuration(record.getStartTime()));
            record.setStatus("FAILED");
            record.setErrorMessage(truncate(errorMessage, 2000));
            repository.update(record);
        } catch (Exception e) {
            log.error("标记任务失败记录异常：recordId={}", recordId, e);
        }
    }

    /**
     * 标记任务为部分成功（存在个别样品异常）
     */
    public void markPartial(Long recordId, String taskId, SplitOutcome outcome) {
        complete(recordId, taskId, outcome);
    }

    /**
     * 完成任务记录更新
     */
    public void complete(Long recordId, String taskId, SplitOutcome outcome) {
        try {
            if (recordId == null) {
                recordId = resolveRecordId(taskId, outcome.getZipName());
            }
            if (recordId == null) {
                log.warn("无法定位任务记录，放弃完成标记：taskId={}", taskId);
                return;
            }
            SplitTaskRecord record = repository.findById(recordId);
            if (record == null) {
                return;
            }
            record.setEndTime(LocalDateTime.now());
            record.setDurationMs(computeDuration(record.getStartTime()));
            record.setStatus(outcome.getStatus());
            record.setOrderCount(outcome.getOrderCount());
            record.setSampleTotal(outcome.getSampleTotal());
            record.setSampleNormal(outcome.getSampleNormal());
            record.setSampleEmpty(outcome.getSampleEmpty());
            record.setSampleFailed(outcome.getSampleFailed());
            record.setOutputZipPath(outcome.getOutputZipPath());
            if (outcome.getSampleFailed() > 0 && !outcome.getSampleErrorMessages().isEmpty()) {
                record.setErrorMessage(truncate(String.join("; ", outcome.getSampleErrorMessages()), 2000));
            }
            repository.update(record);
        } catch (Exception e) {
            log.error("完成任务记录更新异常：recordId={}", recordId, e);
        }
    }

    private Long resolveRecordId(String taskId, String zipName) {
        if (taskId != null) {
            SplitTaskRecord rec = repository.findByTaskId(taskId);
            if (rec != null) {
                return rec.getId();
            }
        }
        return null;
    }

    private Long computeDuration(LocalDateTime startTime) {
        if (startTime == null) {
            return 0L;
        }
        return java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
    }

    private String truncate(String s, int maxLength) {
        if (s == null || s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength);
    }
}
