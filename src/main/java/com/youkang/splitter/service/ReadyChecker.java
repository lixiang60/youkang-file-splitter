package com.youkang.splitter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/**
 * 文件就绪检测器
 * 通过两次大小比对确认文件已停止写入（上传完成）
 *
 * @author youkang
 */
@Slf4j
@Service
public class ReadyChecker {

    private static final Set<String> SYSTEM_FILES = Set.of(
            "thumbs.db", "desktop.ini", ".ds_store", "ntuser.dat", "iconcache.db"
    );

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

    /**
     * 检测目录下所有文件是否已就绪（大小不再变化）
     * 通过两次遍历累加目录下所有常规文件的总大小进行比对
     *
     * @param dir        待检测目录
     * @param intervalMs 两次比对的间隔毫秒数
     * @return true 表示目录内所有文件已稳定；false 表示有文件仍在变化或目录为空
     */
    public boolean isDirectoryReady(Path dir, long intervalMs) {
        try {
            long totalSize1 = calcDirectorySize(dir);
            if (totalSize1 == 0) {
                log.info("目录为空、只有隐藏文件或存在 0 字节文件，视为未就绪：{}", dir);
                return false;
            }

            Thread.sleep(intervalMs);

            long totalSize2 = calcDirectorySize(dir);
            boolean ready = totalSize1 == totalSize2;

            if (ready) {
                log.info("目录已就绪：{}（总大小 {} 字节）", dir, totalSize2);
            } else {
                log.info("目录总大小发生变化，视为未就绪：{}（{} -> {} 字节）",
                        dir, totalSize1, totalSize2);
            }
            return ready;

        } catch (IOException e) {
            log.warn("读取目录大小异常：{}", dir, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("就绪检测线程被中断：{}", dir);
            return false;
        }
    }

    private long calcDirectorySize(Path dir) throws IOException {
        long[] total = {0};
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile() && !isSystemOrHiddenFile(file)) {
                    total[0] += attrs.size();
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return total[0];
    }

    private boolean isSystemOrHiddenFile(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        return name.startsWith(".") || SYSTEM_FILES.contains(name) || Files.isHidden(file);
    }
}
