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

    /** 入口目录：Seq 目录，扫描该目录下的所有文件夹作为订单 */
    @NotBlank
    private String inboxDir;

    /** 结果目录：拆分后的 Bam/Var/Sequence/QC 输出位置 */
    @NotBlank
    private String resultDir;

    /** 归档目录：处理完成后的原始订单归档 */
    @NotBlank
    private String archiveDir;

    /** 扫描 cron 表达式 */
    @NotBlank
    private String scanCron = "0 0 */1 * * ?";

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
