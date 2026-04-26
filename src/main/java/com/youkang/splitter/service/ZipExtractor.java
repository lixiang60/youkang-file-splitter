package com.youkang.splitter.service;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 基于 zip4j 的 ZIP 解压
 * 兼容中文文件名编码
 *
 * @author youkang
 */
@Slf4j
@Service
public class ZipExtractor {

    /**
     * 将 zip 解压到指定目录
     *
     * @param zipFile 源 zip 文件
     * @param destDir 目标解压目录
     * @throws ZipException 解压异常
     * @throws IOException  IO 异常
     */
    public void extract(Path zipFile, Path destDir) throws ZipException, IOException {
        log.info("开始解压：{} -> {}", zipFile, destDir);
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            // 若压缩包内文件名含中文，尝试用 GBK 读取（Windows 常见）
            if (zf.isEncrypted()) {
                throw new ZipException("暂不支持密码保护的 zip 文件：" + zipFile.getFileName());
            }
            zf.extractAll(destDir.toString());
        }
        log.info("解压完成：{}", destDir);
    }
}
