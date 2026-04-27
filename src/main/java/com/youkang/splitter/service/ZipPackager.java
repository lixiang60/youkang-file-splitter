package com.youkang.splitter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP 重新打包
 * 保留原文件目录层级与元数据（含空目录）
 *
 * @author youkang
 */
@Slf4j
@Service
public class ZipPackager {

    /**
     * 将目录打包为 zip
     * 使用标准 ZipOutputStream 确保空目录也会被写入 zip
     *
     * @param sourceDir     源目录（内部文件和空目录会按相对路径打包）
     * @param outputZipFile 输出 zip 文件路径
     * @throws IOException IO 异常
     */
    public void packageDir(Path sourceDir, Path outputZipFile) throws IOException {
        log.info("开始打包：{} -> {}", sourceDir, outputZipFile);

        Path parent = outputZipFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.deleteIfExists(outputZipFile);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZipFile))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(sourceDir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    // 每个目录（含空目录）都写一个以 / 结尾的 ZipEntry
                    String relativePath = sourceDir.relativize(dir).toString().replace('\\', '/') + "/";
                    zos.putNextEntry(new ZipEntry(relativePath));
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relativePath = sourceDir.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(relativePath));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        log.info("打包完成：{}（大小 {} 字节）", outputZipFile, Files.size(outputZipFile));
    }
}
