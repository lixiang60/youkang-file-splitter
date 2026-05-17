//package com.youkang.splitter.service;
//
//import org.junit.jupiter.api.Test;
//
//import java.io.IOException;
//import java.nio.file.*;
//import java.util.stream.Stream;
//import java.util.zip.ZipFile;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * 在原始示例数据上验证二次分类输出（验证后清理）
// */
//class SecondaryClassificationVerifyTest {
//
//    @Test
//    void verifyAgainstExample() throws Exception {
//        Path sourceOrderDir = Paths.get("D:/youkang/example/SDHZ00001");
//        Path sourceExcel = Paths.get("D:/youkang/example/SDHZ00001.xls");
//        Path exampleBoardDir = Paths.get("D:/youkang/example/SDHZ00001-分板");
//        Path exampleSeqDir = Paths.get("D:/youkang/example/SDHZ00001-测序结果");
//
//        if (!Files.exists(sourceOrderDir) || !Files.exists(sourceExcel)
//                || !Files.exists(exampleBoardDir) || !Files.exists(exampleSeqDir)) {
//            System.out.println("示例数据不完整，跳过验证");
//            return;
//        }
//
//        Path tempDir = Files.createTempDirectory("secondary-verify-");
//        Path orderDir = tempDir.resolve("SDHZ00001");
//        Path resultDir = tempDir.resolve("SDHZ00001-result");
//        Files.copy(sourceExcel, tempDir.resolve("SDHZ00001.xls"));
//        copyDirectory(sourceOrderDir, orderDir);
//
//        SecondaryClassificationService service = new SecondaryClassificationService();
//        service.processOrder(orderDir);
//
//        try {
//            // 1. 验证分板结构与示例一致
//            Path generatedBoardDir = resultDir.resolve("SDHZ00001-分板");
//            assertTrue(Files.exists(generatedBoardDir), "分板目录应存在");
//
//            // 对比板号
//            try (Stream<Path> exampleBoards = Files.list(exampleBoardDir);
//                 Stream<Path> generatedBoards = Files.list(generatedBoardDir)) {
//                long exampleCount = exampleBoards.filter(Files::isDirectory).count();
//                long generatedCount = generatedBoards.filter(Files::isDirectory).count();
//                assertEquals(exampleCount, generatedCount, "分板数量应与示例一致");
//            }
//
//            // 对比每个板号下的样品数量
//            try (Stream<Path> exampleBoards = Files.list(exampleBoardDir)) {
//                exampleBoards.filter(Files::isDirectory).forEach(board -> {
//                    String boardName = board.getFileName().toString();
//                    Path genBoard = generatedBoardDir.resolve(boardName);
//                    assertTrue(Files.exists(genBoard), "应存在板号: " + boardName);
//
//                    try (Stream<Path> exSamples = Files.list(board);
//                         Stream<Path> genSamples = Files.list(genBoard)) {
//                        long exCount = exSamples.filter(Files::isDirectory).count();
//                        long genCount = genSamples.filter(Files::isDirectory).count();
//                        assertEquals(exCount, genCount,
//                                boardName + " 的样品数量应与示例一致 (示例:" + exCount + ", 生成:" + genCount + ")");
//                    } catch (Exception e) {
//                        fail("对比样品数量异常: " + boardName, e);
//                    }
//                });
//            }
//
//            // 2. 验证测序结果目录中的文件数量
//            Path generatedSeqDir = resultDir.resolve("SDHZ00001-测序结果");
//            assertTrue(Files.exists(generatedSeqDir), "测序结果目录应存在");
//
//            try (Stream<Path> exampleFiles = Files.list(exampleSeqDir);
//                 Stream<Path> generatedFiles = Files.list(generatedSeqDir)) {
//                long exampleZipCount = exampleFiles.filter(p -> p.toString().endsWith(".zip")).count();
//                long generatedZipCount = generatedFiles.filter(p -> p.toString().endsWith(".zip")).count();
//                assertEquals(exampleZipCount, generatedZipCount,
//                        "测序结果压缩包数量应与示例一致 (示例:" + exampleZipCount + ", 生成:" + generatedZipCount + ")");
//            }
//
//            // 3. 验证 ZIP 文件能正常打开
//            try (Stream<Path> generatedZips = Files.list(generatedSeqDir)) {
//                Path firstZip = generatedZips
//                        .filter(p -> p.toString().endsWith(".zip"))
//                        .findFirst()
//                        .orElse(null);
//
//                if (firstZip != null) {
//                    try (ZipFile zf = new ZipFile(firstZip.toFile())) {
//                        long entryCount = zf.size();
//                        assertTrue(entryCount > 0, "ZIP 应包含条目");
//
//                        boolean hasBam = zf.stream().anyMatch(e -> e.getName().startsWith("Bam/"));
//                        boolean hasSequence = zf.stream().anyMatch(e -> e.getName().startsWith("Sequence/"));
//                        boolean hasVar = zf.stream().anyMatch(e -> e.getName().startsWith("Var/"));
//
//                        assertTrue(hasBam, "ZIP 应包含 Bam 目录");
//                        assertTrue(hasSequence, "ZIP 应包含 Sequence 目录");
//                        assertTrue(hasVar, "ZIP 应包含 Var 目录");
//                    }
//                }
//            }
//
//            System.out.println("验证通过！输出与示例一致。");
//
//        } finally {
//            // 清理临时目录
//            org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
//        }
//    }
//
//    private void copyDirectory(Path source, Path target) throws Exception {
//        Files.walkFileTree(source, new SimpleFileVisitor<>() {
//            @Override
//            public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
//                Path dest = target.resolve(source.relativize(dir));
//                Files.createDirectories(dest);
//                return FileVisitResult.CONTINUE;
//            }
//
//            @Override
//            public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
//                Path dest = target.resolve(source.relativize(file));
//                Files.copy(file, dest);
//                return FileVisitResult.CONTINUE;
//            }
//        });
//    }
//}
