package com.youkang.splitter.service;

import com.youkang.splitter.domain.SampleFolderClassification;
import com.youkang.splitter.domain.SplitBatchResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件拆分核心规则单元测试
 * 使用真实样本（D:/document/2026-0424版）进行端到端断言
 *
 * @author youkang
 */
class FileSplitterServiceTest {

    private final FileSplitterService service = new FileSplitterService();

    private static final Path SOURCE_BASE = Paths.get("D:/document/2026-0424版/原始文件");

    @Test
    void testSplit_SDHZ00001_overallStats() throws IOException {
        Path target = Files.createTempDirectory("split-test-");
        try {
            SplitBatchResult result = service.split(SOURCE_BASE, target);

            // 总样品数（SDHZ00001 下共 25 个）
            assertEquals(25, result.getClassifications().size(),
                    "总样品数应与原始文件一致");

            // 失败样品数应为 0
            assertEquals(0, result.getSampleFailed(),
                    "所有样品应正常拆分，不应出现失败");

            // 统计 NORMAL 和 EMPTY 数量
            long normalCount = result.getClassifications().values().stream()
                    .filter(c -> c == SampleFolderClassification.NORMAL)
                    .count();
            long emptyCount = result.getClassifications().values().stream()
                    .filter(c -> c == SampleFolderClassification.EMPTY)
                    .count();

            assertTrue(normalCount > 0, "应有正常拆分的样品");
            assertTrue(emptyCount > 0, "应有空白样品");

            // NORMAL 样品在目标目录下应有文件
            Path orderDir = target.resolve("SDHZ00001");
            for (Map.Entry<String, SampleFolderClassification> entry : result.getClassifications().entrySet()) {
                String sampleName = entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1);
                Path sampleDir = orderDir.resolve(sampleName);
                if (entry.getValue() == SampleFolderClassification.NORMAL) {
                    assertTrue(Files.isDirectory(sampleDir),
                            "NORMAL 样品应有目录：" + sampleName);
                    assertFalse(collectRelativeFiles(sampleDir).isEmpty(),
                            "NORMAL 样品目录下应有文件：" + sampleName);
                } else {
                    assertTrue(!Files.exists(sampleDir) || collectRelativeFiles(sampleDir).isEmpty(),
                            "EMPTY 样品应无文件：" + sampleName);
                }
            }
        } finally {
            deleteDirectory(target);
        }
    }

    @Test
    void testSplit_YK00000001_AC385() throws IOException {
        // A 类样品（有 reference_analysis），正常拆分
        String sample = "YK00000001-AC385";
        Path target = runSplitForSample(sample);
        try {
            Path sampleDir = target.resolve("SDHZ00001").resolve(sample);
            assertTrue(Files.isDirectory(sampleDir), "样品目录应存在");

            Set<String> files = collectRelativeFiles(sampleDir);
            assertTrue(files.contains("Bam/" + sample + ".ref.sorted.bam"), "应包含 bam 文件");
            assertTrue(files.contains("Bam/" + sample + ".ref.sorted.bam.bai"), "应包含 bai 文件");
            assertTrue(files.contains("Var/" + sample + ".ref.evaluated_filtered.csv"), "应包含 filtered");
            assertTrue(files.contains("Var/" + sample + ".ref.evaluated_raw.csv"), "应包含 raw");
            assertTrue(files.contains("Sequence/" + sample + ".ref.ab1"), "应包含 ab1");
            assertTrue(files.contains("Sequence/" + sample + ".ref.fasta"), "应包含 fasta");
            assertTrue(files.contains("QC/" + sample + ".ref.coverage.png"), "应包含 coverage");
            assertTrue(files.contains("QC/" + sample + ".ref.frequency_wave.html"), "应包含 frequency_wave");
        } finally {
            deleteDirectory(target);
        }
    }

    @Test
    void testSplit_YK00000002_AC385w() throws IOException {
        // B 类样品（无 reference_analysis），正常拆分
        String sample = "YK00000002-AC385w";
        Path target = runSplitForSample(sample);
        try {
            Path sampleDir = target.resolve("SDHZ00001").resolve(sample);
            Set<String> files = collectRelativeFiles(sampleDir);
            assertTrue(files.contains("Sequence/" + sample + ".final_consensus.fasta"),
                    "应包含 final_consensus fasta");
            assertTrue(files.contains("QC/" + sample + "_coverage.png"), "应包含 coverage");
            assertTrue(files.contains("QC/" + sample + "_frequency_wave.html"), "应包含 frequency_wave");
        } finally {
            deleteDirectory(target);
        }
    }

    @Test
    void testSplit_YK00000004_AC386w() throws IOException {
        // B 类样品，含 hap1/hap2
        String sample = "YK00000004-AC386w";
        Path target = runSplitForSample(sample);
        try {
            Path sampleDir = target.resolve("SDHZ00001").resolve(sample);
            Set<String> files = collectRelativeFiles(sampleDir);
            assertTrue(files.contains("Sequence/" + sample + ".final_consensus.fasta"), "应包含 final_consensus");
            assertTrue(files.contains("Sequence/" + sample + ".hap1.fasta"), "应包含 hap1");
            assertTrue(files.contains("Sequence/" + sample + ".hap2.fasta"), "应包含 hap2");
            assertFalse(files.stream().anyMatch(f -> f.contains("all_consensus")), "不应包含 all_consensus");
            assertFalse(files.stream().anyMatch(f -> f.contains("best_consensus")), "不应包含 best_consensus");
        } finally {
            deleteDirectory(target);
        }
    }

    @Test
    void testSplit_YK00000010_qAC01() throws IOException {
        // A 类样品，QC 为 plann（无 coverage PNG）
        String sample = "YK00000010-qAC01";
        Path target = runSplitForSample(sample);
        try {
            Path sampleDir = target.resolve("SDHZ00001").resolve(sample);
            Set<String> files = collectRelativeFiles(sampleDir);
            assertTrue(files.contains("QC/" + sample + ".ref.pLann.html"), "应包含 pLann");
            assertTrue(files.contains("Sequence/" + sample + ".ref.fasta"), "应包含 fasta");
        } finally {
            deleteDirectory(target);
        }
    }

    @Test
    void testSplit_YK00000012_cAC201w() throws IOException {
        // B 类样品，insertseq 场景
        String sample = "YK00000012-cAC201w";
        Path target = runSplitForSample(sample);
        try {
            Path sampleDir = target.resolve("SDHZ00001").resolve(sample);
            Set<String> files = collectRelativeFiles(sampleDir);
            assertTrue(files.contains("Sequence/" + sample + ".insertseq.fasta"), "应包含 insertseq");
            assertFalse(files.stream().anyMatch(f -> f.contains("assembly") && f.endsWith(".fasta")),
                    "不应包含 assembly fasta（insertseq 共存时只保留 insertseq）");
        } finally {
            deleteDirectory(target);
        }
    }

    @Test
    void testSplit_emptySamples() throws IOException {
        // 三个空白样品
        Path target = Files.createTempDirectory("split-test-empty-");
        try {
            service.split(SOURCE_BASE, target);
            Path orderDir = target.resolve("SDHZ00001");

            String[] emptySamples = {
                    "YK00000007-H02",
                    "YK00000008-H02w",
                    "YK00000009-D06",
                    "YK00000018-qAC241wno",
                    "YK00000020-cAC241no",
                    "YK00000023-qAC97no"
            };
            for (String sample : emptySamples) {
                Path sampleDir = orderDir.resolve(sample);
                assertTrue(Files.isDirectory(sampleDir), "空白样品目录应存在：" + sample);
                assertTrue(collectRelativeFiles(sampleDir).isEmpty(), "空白样品应无文件：" + sample);
            }
        } finally {
            deleteDirectory(target);
        }
    }

    @Test
    void testSplit_emptySource_shouldReturnEmpty() throws IOException {
        Path emptySource = Files.createTempDirectory("empty-source-");
        Path target = Files.createTempDirectory("empty-target-");
        try {
            SplitBatchResult result = service.split(emptySource, target);
            assertTrue(result.getClassifications().isEmpty(),
                    "空源目录应返回空结果");
        } finally {
            deleteDirectory(emptySource);
            deleteDirectory(target);
        }
    }

    @Test
    void testEmptySamplesSurvivePackaging() throws Exception {
        // 端到端验证：拆分 -> 打包 -> 解压后，空白样品目录仍然存在
        Path splitDir = Files.createTempDirectory("split-");
        Path zipFile = Files.createTempFile("output", ".zip");
        Path extractDir = Files.createTempDirectory("extract-");
        try {
            service.split(SOURCE_BASE, splitDir);

            ZipPackager packager = new ZipPackager();
            packager.packageDir(splitDir, zipFile);

            ZipExtractor extractor = new ZipExtractor();
            extractor.extract(zipFile, extractDir);

            String[] emptySamples = {
                    "YK00000007-H02",
                    "YK00000008-H02w",
                    "YK00000009-D06",
                    "YK00000018-qAC241wno",
                    "YK00000020-cAC241no",
                    "YK00000023-qAC97no"
            };
            for (String sample : emptySamples) {
                Path sampleDir = extractDir.resolve("SDHZ00001").resolve(sample);
                assertTrue(Files.isDirectory(sampleDir),
                        "打包解压后空白样品目录应仍然存在：" + sample);
            }
        } finally {
            deleteDirectory(splitDir);
            deleteDirectory(extractDir);
            Files.deleteIfExists(zipFile);
        }
    }

    // ===================== 辅助方法 =====================

    private Path runSplitForSample(String sampleName) throws IOException {
        Path target = Files.createTempDirectory("split-test-");
        service.split(SOURCE_BASE, target);
        return target;
    }

    private Set<String> collectRelativeFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return Collections.emptySet();
        }
        Set<String> set = new TreeSet<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    set.add(dir.relativize(file).toString().replace('\\', '/'));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return set;
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
