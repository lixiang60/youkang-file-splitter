package com.youkang.splitter.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 拆分服务配置参数
 * 对应 application.yml 中 splitter.* 配置块
 *
 * @author youkang
 */
@Data
@Validated
@ConfigurationProperties(prefix = "splitter")
public class SplitterProperties {

    /** 入口目录：用户上传 zip 的位置 */
    @NotBlank
    private String inboxDir;

    /** 工作目录：解压与中间产物 */
    @NotBlank
    private String workDir;

    /** 输出目录：拆分后 zip 输出 */
    @NotBlank
    private String outputDir;

    /** 失败目录：异常的原始 zip 隔离归档 */
    @NotBlank
    private String failedDir;

    /** 归档目录：成功处理后的原始 zip 归档 */
    @NotBlank
    private String archiveDir;

    /** 任务记录数据库路径 */
    @NotBlank
    private String sqlitePath;

    /** 扫描 cron 表达式 */
    @NotBlank
    private String scanCron = "0 */1 * * * ?";

    /** 文件就绪检测两次大小比对的间隔（秒） */
    @Min(1)
    private int readyCheckIntervalSeconds = 10;

    /** 归档完成后是否删除原始 zip */
    private boolean deleteAfterArchive = false;

    /** 文件锁定重试次数 */
    @Min(0)
    private int retryTimes = 3;

    /** 文件锁定重试间隔（毫秒） */
    @Min(0)
    private long retryIntervalMs = 2000L;
}
