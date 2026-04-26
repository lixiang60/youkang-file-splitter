package com.youkang.splitter.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拆分任务记录实体
 * 映射 split_task_record 表
 *
 * @author youkang
 */
@Data
public class SplitTaskRecord {

    private Long id;
    private String taskId;
    private String zipName;
    private Long zipSizeBytes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String status;
    private Integer orderCount;
    private Integer sampleTotal;
    private Integer sampleNormal;
    private Integer sampleEmpty;
    private Integer sampleFailed;
    private String outputZipPath;
    private String errorMessage;
}
