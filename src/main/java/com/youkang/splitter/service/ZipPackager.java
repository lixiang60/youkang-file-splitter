package com.youkang.splitter.service;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 基于 zip4j 的 ZIP 重新打包
 * 保留原文件目录层级与元数据
 *
 * @author youkang
 */
@Slf4j
@Service
public class ZipPackager {

    /**
     * 将目录打包为 zip
     *
     * @param sourceDir    源目录（内部文件会被按相对路径打包）
     * @param outputZipFile 输出 zip 文件路径
     * @throws IOException IO 异常
     */
    public void packageDir(Path sourceDir, Path outputZipFile) throws IOException {
        log.info("开始打包：{} -> {}", sourceDir, outputZipFile);

        // 确保输出目录存在
        Path parent = outputZipFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // 若输出文件已存在则先删除
        Files.deleteIfExists(outputZipFile);

        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionLevel(CompressionLevel.NORMAL);

        try (ZipFile zipFile = new ZipFile(outputZipFile.toFile())) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile()) {
                        // 计算相对路径作为 zip 内部路径
                        String relativePath = sourceDir.relativize(file).toString().replace('\\', '/');
                        parameters.setFileNameInZip(relativePath);
                        zipFile.addFile(file.toFile(), parameters);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        log.info("打包完成：{}（大小 {} 字节）", outputZipFile, Files.size(outputZipFile));
    }
}
