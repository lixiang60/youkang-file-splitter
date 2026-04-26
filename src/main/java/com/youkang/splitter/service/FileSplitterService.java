package com.youkang.splitter.service;

import com.youkang.splitter.domain.SampleFolderClassification;
import com.youkang.splitter.domain.SplitBatchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件拆分核心服务
 * 按照统一规则将原始生信产物归类到 Bam / Var / Sequence / QC 四个目录
 *
 * @author youkang
 */
@Slf4j
@Service
public class FileSplitterService {

    private static final Set<String> BAM_EXTENSIONS = Set.of("bam", "bai");
    private static final Set<String> VAR_EXTENSIONS = Set.of("csv", "xls", "xlsx");
    private static final Set<String> SEQUENCE_AB1_EXTENSIONS = Set.of("ab1");
    private static final Set<String> SEQUENCE_FASTA_EXTENSIONS = Set.of("fasta");
    private static final Set<String> QC_PNG_EXTENSIONS = Set.of("png");
    private static final Set<String> QC_HTML_EXTENSIONS = Set.of("html", "htm");

    /**
     * 对解压后的目录执行拆分
     *
     * @param extractedDir 解压根目录（可能含多个 SDHZ 订单文件夹）
     * @param splitDir     拆分输出根目录
     * @return 批次拆分结果
     * @throws IOException 目录遍历异常
     */
    public SplitBatchResult split(Path extractedDir, Path splitDir) throws IOException {
        SplitBatchResult result = new SplitBatchResult();

        List<Path> orderDirs = listDirectories(extractedDir);
        if (orderDirs.isEmpty()) {
            log.warn("解压目录下未发现任何订单文件夹：{}", extractedDir);
            return result;
        }

        for (Path orderDir : orderDirs) {
            String orderName = orderDir.getFileName().toString();
            Path targetOrderDir = splitDir.resolve(orderName);
            Files.createDirectories(targetOrderDir);

            List<Path> sampleDirs = listDirectories(orderDir);
            for (Path sampleDir : sampleDirs) {
                String sampleName = sampleDir.getFileName().toString();
                String relativeKey = orderName + "/" + sampleName;
                Path targetSampleDir = targetOrderDir.resolve(sampleName);

                try {
                    SampleFolderClassification classification = splitSample(sampleDir, targetSampleDir);
                    result.addClassification(relativeKey, classification);
                    log.debug("样品拆分完成：{} -> {}", relativeKey, classification);
                } catch (Exception e) {
                    log.error("样品拆分异常：{}", relativeKey, e);
                    result.addClassification(relativeKey, SampleFolderClassification.EMPTY);
                    result.addSampleError(relativeKey, e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * 拆分单个样品
     *
     * @param sampleSourceDir  样品源目录（原始位置）
     * @param sampleTargetDir  样品目标目录（拆分后位置）
     * @return 分类结果
     * @throws IOException IO 异常
     */
    private SampleFolderClassification splitSample(Path sampleSourceDir, Path sampleTargetDir) throws IOException {
        Path actualSource = resolveActualSourceDir(sampleSourceDir);

        // 收集各类文件
        List<Path> allFiles = listAllFiles(actualSource);

        List<Path> bamFiles = filterBam(allFiles);
        List<Path> varFiles = filterVar(allFiles);
        List<Path> sequenceFiles = filterSequence(allFiles);
        List<Path> qcFiles = filterQc(allFiles);

        boolean hasAny = !bamFiles.isEmpty() || !varFiles.isEmpty()
                || !sequenceFiles.isEmpty() || !qcFiles.isEmpty();

        if (!hasAny) {
            log.debug("样品无有效产物，标记为空白：{}", sampleSourceDir.getFileName());
            return SampleFolderClassification.EMPTY;
        }

        // 创建目标子目录并复制文件
        if (!bamFiles.isEmpty()) {
            copyFiles(bamFiles, sampleTargetDir.resolve("Bam"));
        }
        if (!varFiles.isEmpty()) {
            copyFiles(varFiles, sampleTargetDir.resolve("Var"));
        }
        if (!sequenceFiles.isEmpty()) {
            copyFiles(sequenceFiles, sampleTargetDir.resolve("Sequence"));
        }
        if (!qcFiles.isEmpty()) {
            copyFiles(qcFiles, sampleTargetDir.resolve("QC"));
        }

        return SampleFolderClassification.NORMAL;
    }

    /**
     * 确定实际源目录：若存在 reference_analysis 则优先使用
     */
    private Path resolveActualSourceDir(Path sampleDir) {
        Path refDir = sampleDir.resolve("reference_analysis");
        if (Files.exists(refDir) && Files.isDirectory(refDir)) {
            return refDir;
        }
        return sampleDir;
    }

    /**
     * 递归列出目录下所有常规文件
     */
    private List<Path> listAllFiles(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    /**
     * 列出目录下的直接子目录
     */
    private List<Path> listDirectories(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    // ===================== 各类文件过滤 =====================

    private List<Path> filterBam(List<Path> files) {
        return files.stream()
                .filter(f -> BAM_EXTENSIONS.contains(getExtension(f).toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<Path> filterVar(List<Path> files) {
        return files.stream()
                .filter(f -> {
                    String name = f.getFileName().toString().toLowerCase();
                    String ext = getExtension(f).toLowerCase();
                    return (name.contains("_filtered") || name.contains("_raw"))
                            && VAR_EXTENSIONS.contains(ext);
                })
                .collect(Collectors.toList());
    }

    /**
     * Sequence 规则：全部 ab1 + 筛选后的 fasta
     */
    private List<Path> filterSequence(List<Path> files) {
        List<Path> ab1Files = files.stream()
                .filter(f -> SEQUENCE_AB1_EXTENSIONS.contains(getExtension(f).toLowerCase()))
                .collect(Collectors.toList());

        List<Path> rawFasta = files.stream()
                .filter(f -> SEQUENCE_FASTA_EXTENSIONS.contains(getExtension(f).toLowerCase()))
                .collect(Collectors.toList());

        List<Path> filteredFasta = filterFasta(rawFasta);

        List<Path> result = new ArrayList<>(ab1Files.size() + filteredFasta.size());
        result.addAll(ab1Files);
        result.addAll(filteredFasta);
        return result;
    }

    /**
     * fasta 筛选逻辑：
     * 1. 剔除含 all 或 best 的文件
     * 2. 若同时存在 assembly 和 insertseq，仅保留 insertseq
     * 3. 否则全部保留
     */
    private List<Path> filterFasta(List<Path> fastaFiles) {
        // 第一步：剔除含 all / best
        List<Path> step1 = fastaFiles.stream()
                .filter(f -> {
                    String name = f.getFileName().toString().toLowerCase();
                    return !name.contains("all") && !name.contains("best");
                })
                .collect(Collectors.toList());

        if (step1.isEmpty()) {
            return Collections.emptyList();
        }

        // 第二步：判断 assembly / insertseq 共存情况
        boolean hasAssembly = step1.stream()
                .anyMatch(f -> f.getFileName().toString().toLowerCase().contains("assembly"));
        boolean hasInsertseq = step1.stream()
                .anyMatch(f -> f.getFileName().toString().toLowerCase().contains("insertseq"));

        if (hasAssembly && hasInsertseq) {
            return step1.stream()
                    .filter(f -> f.getFileName().toString().toLowerCase().contains("insertseq"))
                    .collect(Collectors.toList());
        }

        return step1;
    }

    /**
     * QC 规则：
     * - coverage 的 PNG
     * - frequency_wave 的 HTML
     * - plann 的 HTML（不区分大小写）
     */
    private List<Path> filterQc(List<Path> files) {
        return files.stream()
                .filter(f -> {
                    String name = f.getFileName().toString().toLowerCase();
                    String ext = getExtension(f).toLowerCase();

                    boolean isPng = QC_PNG_EXTENSIONS.contains(ext);
                    boolean isHtml = QC_HTML_EXTENSIONS.contains(ext);

                    return (isPng && name.contains("coverage"))
                            || (isHtml && name.contains("frequency_wave"))
                            || (isHtml && name.contains("plann"));
                })
                .collect(Collectors.toList());
    }

    // ===================== 工具方法 =====================

    private String getExtension(Path file) {
        String name = file.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : "";
    }

    /**
     * 批量复制文件到目标目录
     */
    private void copyFiles(List<Path> files, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        for (Path source : files) {
            Path target = targetDir.resolve(source.getFileName().toString());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }
}
