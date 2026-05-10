package com.youkang.splitter.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件就绪检测器单元测试
 *
 * @author youkang
 */
class ReadyCheckerTest {

    private final ReadyChecker checker = new ReadyChecker();

    @Test
    void testIsReady_emptyFile_shouldReturnFalse() throws IOException {
        Path tempFile = Files.createTempFile("ready-test-empty", ".zip");
        try {
            // 空文件
            assertFalse(checker.isReady(tempFile, 100L),
                    "空文件应视为未就绪");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testIsReady_stableFile_shouldReturnTrue() throws IOException {
        Path tempFile = Files.createTempFile("ready-test-stable", ".zip");
        try {
            // 写入内容后不再改动
            Files.write(tempFile, "test content".getBytes());
            assertTrue(checker.isReady(tempFile, 100L),
                    "稳定的非空文件应视为就绪");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testIsReady_changingFile_shouldReturnFalse() throws IOException, InterruptedException {
        Path tempFile = Files.createTempFile("ready-test-changing", ".zip");
        try {
            Files.write(tempFile, "initial".getBytes());

            // 在后台线程持续写入，模拟上传中
            Thread writer = new Thread(() -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        Thread.sleep(50L);
                        Files.write(tempFile, ("data" + i).getBytes());
                    }
                } catch (Exception ignored) {
                }
            });
            writer.start();

            // 检测时文件仍在变化
            boolean ready = checker.isReady(tempFile, 200L);
            assertFalse(ready, "持续变化的文件应视为未就绪");

            writer.join(2000L);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testIsReady_nonExistentFile_shouldReturnFalse() {
        Path fakeFile = Path.of("/non/existent/file.zip");
        assertFalse(checker.isReady(fakeFile, 100L),
                "不存在的文件应视为未就绪");
    }

    @Test
    void testIsDirectoryReady_stableDir_shouldReturnTrue() throws IOException {
        Path tempDir = Files.createTempDirectory("ready-test-dir");
        try {
            Files.write(tempDir.resolve("file1.txt"), "content1".getBytes());
            Files.write(tempDir.resolve("file2.txt"), "content2".getBytes());
            assertTrue(checker.isDirectoryReady(tempDir, 100L),
                    "稳定的目录应视为就绪");
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    @Test
    void testIsDirectoryReady_emptyDir_shouldReturnFalse() throws IOException {
        Path tempDir = Files.createTempDirectory("ready-test-empty-dir");
        try {
            assertFalse(checker.isDirectoryReady(tempDir, 100L),
                    "空目录应视为未就绪");
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    @Test
    void testIsDirectoryReady_withHiddenFile_shouldIgnoreHidden() throws IOException {
        Path tempDir = Files.createTempDirectory("ready-test-hidden");
        try {
            Files.write(tempDir.resolve("normal.txt"), "content".getBytes());
            Files.write(tempDir.resolve(".hidden"), "hidden content".getBytes());
            assertTrue(checker.isDirectoryReady(tempDir, 100L),
                    "隐藏文件应被忽略，稳定目录应视为就绪");
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
        }
    }
}
