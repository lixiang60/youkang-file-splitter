package com.youkang.splitter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件就绪检测器
 * 通过两次大小比对确认文件已停止写入（上传完成）
 *
 * @author youkang
 */
@Slf4j
@Service
public class ReadyChecker {

    /**
     * 检测文件是否已就绪（大小不再变化）
     *
     * @param file       待检测文件
     * @param intervalMs 两次比对的间隔毫秒数
     * @return true 表示文件已稳定；false 表示文件仍在变化或为空
     */
    public boolean isReady(Path file, long intervalMs) {
        try {
            long size1 = Files.size(file);
            if (size1 == 0) {
                log.debug("文件大小为 0，视为未就绪：{}", file.getFileName());
                return false;
            }
            Thread.sleep(intervalMs);
            long size2 = Files.size(file);
            boolean ready = size1 == size2;
            if (ready) {
                log.info("文件就绪：{}（{} 字节）", file.getFileName(), size2);
            } else {
                log.debug("文件仍在写入，跳过本次处理：{}（{} -> {} 字节）",
                        file.getFileName(), size1, size2);
            }
            return ready;
        } catch (IOException e) {
            log.warn("读取文件大小异常：{}", file, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("就绪检测线程被中断：{}", file);
            return false;
        }
    }
}
