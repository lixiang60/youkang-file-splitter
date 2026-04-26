package com.youkang.splitter.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单次 zip 任务的拆分结果聚合
 *
 * @author youkang
 */
@Data
public class SplitOutcome {

    /** 任务唯一标识 */
    private String taskId;

    /** 原始 zip 文件名 */
    private String zipName;

    /** 原始 zip 大小（字节） */
    private Long zipSizeBytes;

    /** 任务开始时间 */
    private LocalDateTime startTime;

    /** 任务结束时间 */
    private LocalDateTime endTime;

    /** 任务耗时（毫秒） */
    private Long durationMs;

    /** 任务状态：SUCCESS / PARTIAL_SUCCESS / FAILED */
    private String status;

    /** 解压后包含的订单（SDHZ）数量 */
    private int orderCount;

    /** 总样品数 */
    private int sampleTotal;

    /** 正常拆分的样品数 */
    private int sampleNormal;

    /** 空白样品数 */
    private int sampleEmpty;

    /** 处理失败的样品数 */
    private int sampleFailed;

    /** 输出 zip 路径 */
    private String outputZipPath;

    /** 错误摘要 */
    private String errorMessage;

    /** 各样品失败时的异常信息 */
    private final List<String> sampleErrorMessages = new ArrayList<>();

    public void addSampleError(String message) {
        this.sampleErrorMessages.add(message);
    }

    /**
     * 计算并设置耗时
     */
    public void computeDuration() {
        if (startTime != null && endTime != null) {
            this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }
}
