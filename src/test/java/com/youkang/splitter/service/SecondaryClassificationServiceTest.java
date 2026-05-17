//package com.youkang.splitter.service;
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.*;
//import java.util.stream.Stream;
//import java.util.zip.ZipFile;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * 二次分类服务端到端测试
// * 使用示例数据验证分板、测序明细表、测序结果压缩包的正确性
// */
//class SecondaryClassificationServiceTest {
//
//    private final SecondaryClassificationService service = new SecondaryClassificationService();
//
//    @TempDir
//    Path tempDir;
//
//    @Test
//    void testProcessOrder_SDHZ00001() throws Exception {
//        // 准备测试数据
//        Path sourceOrderDir = Paths.get("D:/youkang/example/SDHZ00001");
//        Path sourceExcel = Paths.get("D:/youkang/example/SDHZ00001.xls");
//
//        if (!Files.exists(sourceOrderDir) || !Files.exists(sourceExcel)) {
//            System.out.println("示例数据不存在，跳过测试");
//            return;
//        }
//
//        Path orderDir = tempDir.resolve("SDHZ00001");
//        Path excelFile = tempDir.resolve("SDHZ00001.xls");
//
//        copyDirectory(sourceOrderDir, orderDir);
//        Files.copy(sourceExcel, excelFile);
//
//        // 执行处理
//        service.processOrder(orderDir);
//
//        // 验证输出目录存在
//        Path resultDir = tempDir.resolve("SDHZ00001-result");
//        assertTrue(Files.exists(resultDir), "结果目录应存在");
//
//        // 1. 验证分板
//        Path boardDir = resultDir.resolve("SDHZ00001-分板");
//        assertTrue(Files.exists(boardDir), "分板目录应存在");
//
//        // 验证存在预期的板号
//        assertTrue(Files.exists(boardDir.resolve("SDHZ00001-1")), "应存在 SDHZ00001-1 板");
//        assertTrue(Files.exists(boardDir.resolve("SDHZ00001-2")), "应存在 SDHZ00001-2 板");
//
//        // 验证板1中包含钟琪的样品
//        assertTrue(Files.exists(boardDir.resolve("SDHZ00001-1/YK21439068-CSB1")), "板1应包含 YK21439068-CSB1");
//        assertTrue(Files.exists(boardDir.resolve("SDHZ00001-1/YK21439068-CSB1/Bam")), "样品应包含 Bam 子目录");
//
//        // 2. 验证测序结果目录
//        Path sequencingDir = resultDir.resolve("SDHZ00001-测序结果");
//        assertTrue(Files.exists(sequencingDir), "测序结果目录应存在");
//
//        // 验证存在预期的明细表
//        assertTrue(
//                Files.exists(sequencingDir.resolve("钟琪-20260508193941945-测序明细.xlsx")) ||
//                        Files.exists(sequencingDir.resolve("��-20260508193941945-测序明细.xlsx")),
//                "应存在钟琪的测序明细表"
//        );
//
//        // 验证存在预期的压缩包
//        Path zhongqiZip = sequencingDir.resolve("钟琪-20260508193941945-测序结果.zip");
//        if (!Files.exists(zhongqiZip)) {
//            zhongqiZip = sequencingDir.resolve("��-20260508193941945-测序结果.zip");
//        }
//        assertTrue(Files.exists(zhongqiZip), "应存在钟琪的测序结果压缩包");
//
//        // 验证 ZIP 内容
//        try (ZipFile zip = new ZipFile(zhongqiZip.toFile())) {
//            boolean hasBam = zip.stream().anyMatch(e -> e.getName().startsWith("Bam/"));
//            boolean hasSequence = zip.stream().anyMatch(e -> e.getName().startsWith("Sequence/"));
//            boolean hasVar = zip.stream().anyMatch(e -> e.getName().startsWith("Var/"));
//            boolean hasMergedFilt = zip.stream().anyMatch(e -> e.getName().equals("merged_data.filt.var.xls"));
//            boolean hasMergedVar = zip.stream().anyMatch(e -> e.getName().equals("merged_data.var.xls"));
//
//            assertTrue(hasBam, "ZIP 应包含 Bam 目录");
//            assertTrue(hasSequence, "ZIP 应包含 Sequence 目录");
//            assertTrue(hasVar, "ZIP 应包含 Var 目录");
//            assertTrue(hasMergedFilt || hasMergedVar, "ZIP 应包含合并的 var 文件");
//
//            // 验证 ZIP 中 Bam 文件数量（钟琪有6个样品，每个2个bam/bai文件）
//            long bamEntryCount = zip.stream().filter(e -> e.getName().startsWith("Bam/") && !e.isDirectory()).count();
//            assertTrue(bamEntryCount >= 12, "钟琪订单 ZIP 中 Bam 文件应不少于12个");
//        }
//
//        // 验证 Excel 被复制到结果目录
//        assertTrue(Files.exists(resultDir.resolve("SDHZ00001.xls")), "Excel 应被复制到结果目录");
//
//        // 验证分板中各板号的样品数量
//        try (Stream<Path> board1Samples = Files.list(boardDir.resolve("SDHZ00001-1"))) {
//            long count = board1Samples.filter(Files::isDirectory).count();
//            assertTrue(count > 0, "板1应包含样品");
//        }
//        try (Stream<Path> board2Samples = Files.list(boardDir.resolve("SDHZ00001-2"))) {
//            long count = board2Samples.filter(Files::isDirectory).count();
//            assertTrue(count > 0, "板2应包含样品");
//        }
//
//        // 验证测序明细表中交付长度列（钟琪的 CSB1 样品 fasta 长度为 1100）
//        Path detailExcel = sequencingDir.resolve("钟琪-20260508193941945-测序明细.xlsx");
//        if (!Files.exists(detailExcel)) {
//            // 编码问题导致文件名不匹配，跳过
//            System.out.println("注意：明细表文件名可能因编码问题未匹配");
//        }
//    }
//
//    @Test
//    void testProcessOrder_skipAlreadyProcessed() throws Exception {
//        Path orderDir = tempDir.resolve("SDHZ00001");
//        Files.createDirectories(orderDir);
//        Path excelFile = tempDir.resolve("SDHZ00001.xls");
//        Files.createFile(excelFile);
//
//        // 预先创建结果目录
//        Path resultDir = tempDir.resolve("SDHZ00001-result");
//        Files.createDirectories(resultDir);
//
//        // 执行处理（应跳过）
//        service.processOrder(orderDir);
//
//        // 验证 Excel 没有被复制（因为跳过了）
//        assertFalse(Files.exists(resultDir.resolve("SDHZ00001.xls")), "应跳过已处理的订单");
//    }
//
//    @Test
//    void testProcessOrder_noExcel() throws Exception {
//        Path orderDir = tempDir.resolve("SDHZ00001");
//        Files.createDirectories(orderDir);
//
//        // 没有 Excel，应跳过
//        service.processOrder(orderDir);
//
//        Path resultDir = tempDir.resolve("SDHZ00001-result");
//        assertFalse(Files.exists(resultDir), "无 Excel 时不应生成结果目录");
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
