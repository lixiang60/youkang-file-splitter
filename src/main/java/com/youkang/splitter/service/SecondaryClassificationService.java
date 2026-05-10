package com.youkang.splitter.service;

import com.youkang.splitter.domain.DetailRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 二次分类核心服务
 * 根据 Excel 明细表对一次分类结果进行分板、生成测序明细表和测序结果压缩包
 */
@Slf4j
@Service
public class SecondaryClassificationService {

    private static final Set<String> EXCEL_EXTENSIONS = Set.of("xls", "xlsx");
    private static final String BOARD_SUFFIX = "-分板";
    private static final String SEQUENCING_RESULT_SUFFIX = "-测序结果";
    private static final String RESULT_SUFFIX = "-result";
    private static final String[] SAMPLE_SUBDIRS = {"Bam", "Var", "Sequence", "QC"};

    /**
     * 处理单个订单目录
     *
     * @param orderDir 一次分类后的订单目录，如 result/SDHZ00001
     */
    public void processOrder(Path orderDir) {
        String orderName = orderDir.getFileName().toString();
        Path resultDir = orderDir.getParent();
        Path outputDir = resultDir.resolve(orderName + RESULT_SUFFIX);

        if (Files.exists(outputDir)) {
            log.debug("订单已处理过，跳过：{}", orderName);
            return;
        }

        Path excelFile = findExcelFile(orderDir);
        if (excelFile == null) {
            log.warn("订单目录下未找到对应 Excel 明细表，跳过：{}", orderName);
            return;
        }

        log.info("开始二次分类处理订单：{}", orderName);

        try {
            List<DetailRecord> records = readExcel(excelFile);
            if (records.isEmpty()) {
                log.warn("Excel 明细表为空，跳过：{}", orderName);
                return;
            }

            Map<String, String> codeToFolder = buildCodeToFolderMap(orderDir, records);

            Files.createDirectories(outputDir);

            // 复制 Excel 到输出目录
            Path excelCopy = outputDir.resolve(excelFile.getFileName());
            Files.copy(excelFile, excelCopy, StandardCopyOption.REPLACE_EXISTING);

            // 1. 分板
            splitByBoard(orderDir, outputDir, records, codeToFolder);

            // 2. 测序结果明细表
            generateDetailSheets(orderDir, outputDir, records, codeToFolder);

            // 3. 测序结果压缩包
            generateResultZips(orderDir, outputDir, records, codeToFolder);

            log.info("二次分类处理完成：{}", orderName);

        } catch (Exception e) {
            log.error("二次分类处理失败：{}", orderName, e);
            // 清理失败的输出目录
            try {
                if (Files.exists(outputDir)) {
                    FileUtils.deleteDirectory(outputDir.toFile());
                }
            } catch (IOException ioEx) {
                log.error("清理失败输出目录异常：{}", outputDir, ioEx);
            }
        }
    }

    /**
     * 在订单目录同级查找同名 Excel 文件
     */
    private Path findExcelFile(Path orderDir) {
        String baseName = orderDir.getFileName().toString();
        Path parent = orderDir.getParent();
        for (String ext : EXCEL_EXTENSIONS) {
            Path candidate = parent.resolve(baseName + "." + ext);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 读取 Excel 明细表
     */
    private List<DetailRecord> readExcel(Path excelFile) throws IOException {
        List<DetailRecord> records = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(excelFile.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean firstRow = true;

            for (Row row : sheet) {
                if (firstRow) {
                    firstRow = false;
                    continue; // 跳过表头
                }

                DetailRecord record = new DetailRecord();
                record.setProductionCode(getCellStringValue(row.getCell(0)));
                record.setOrderId(getCellStringValue(row.getCell(2)));
                record.setCustomerName(getCellStringValue(row.getCell(4)));
                record.setSampleCode(getCellStringValue(row.getCell(5)));
                record.setSequence(getCellStringValue(row.getCell(6)));
                record.setSampleType(getCellStringValue(row.getCell(7)));
                record.setSequencingProject(getCellStringValue(row.getCell(8)));
                record.setEstimatedFragmentSize(getCellStringValue(row.getCell(10)));
                record.setBoardNo(getCellStringValue(row.getCell(13)));
                record.setWellNo(getCellStringValue(row.getCell(14)));
                record.setBarcode(getCellStringValue(row.getCell(15)));
                record.setConcentration(getCellStringValue(row.getCell(16)));
                record.setRemark(getCellStringValue(row.getCell(17)));

                if (record.getProductionCode() != null && !record.getProductionCode().isBlank()) {
                    records.add(record);
                }
            }
        }

        return records;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    /**
     * 建立生产编号到样品文件夹名的映射
     */
    private Map<String, String> buildCodeToFolderMap(Path orderDir, List<DetailRecord> records) throws IOException {
        Map<String, String> codeToFolder = new HashMap<>();

        try (Stream<Path> stream = Files.list(orderDir)) {
            List<String> folderNames = stream
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .toList();

            for (DetailRecord record : records) {
                String code = record.getProductionCode();
                String matched = folderNames.stream()
                        .filter(f -> f.startsWith(code))
                        .findFirst()
                        .orElse(null);
                if (matched != null) {
                    codeToFolder.put(code, matched);
                } else {
                    log.warn("未找到生产编号对应的样品文件夹：{}", code);
                }
            }
        }

        return codeToFolder;
    }

    /**
     * 分板：按板号归类样品文件夹
     */
    private void splitByBoard(Path orderDir, Path outputDir, List<DetailRecord> records,
                              Map<String, String> codeToFolder) throws IOException {
        Path boardOutputDir = outputDir.resolve(orderDir.getFileName() + BOARD_SUFFIX);

        Map<String, List<DetailRecord>> boardGroups = records.stream()
                .collect(Collectors.groupingBy(r -> r.getBoardNo() != null ? r.getBoardNo() : "未知板号"));

        for (Map.Entry<String, List<DetailRecord>> entry : boardGroups.entrySet()) {
            String boardNo = entry.getKey();
            Path boardDir = boardOutputDir.resolve(boardNo);
            Files.createDirectories(boardDir);

            for (DetailRecord record : entry.getValue()) {
                String folderName = codeToFolder.get(record.getProductionCode());
                if (folderName == null) {
                    continue;
                }
                Path source = orderDir.resolve(folderName);
                Path target = boardDir.resolve(folderName);
                if (Files.exists(source)) {
                    copyDirectory(source, target);
                }
            }
        }

        log.debug("分板完成：{} 个板号", boardGroups.size());
    }

    /**
     * 生成测序结果明细表
     */
    private void generateDetailSheets(Path orderDir, Path outputDir, List<DetailRecord> records,
                                      Map<String, String> codeToFolder) throws IOException {
        Path resultDir = outputDir.resolve(orderDir.getFileName() + SEQUENCING_RESULT_SUFFIX);
        Files.createDirectories(resultDir);

        Map<String, List<DetailRecord>> orderGroups = records.stream()
                .collect(Collectors.groupingBy(r -> r.getOrderId() != null ? r.getOrderId() : "未知订单"));

        for (Map.Entry<String, List<DetailRecord>> entry : orderGroups.entrySet()) {
            String orderId = entry.getKey();
            String customerName = entry.getValue().get(0).getCustomerName();
            String fileName = customerName + "-" + orderId + "-测序明细.xlsx";
            Path excelPath = resultDir.resolve(fileName);

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("测序明细");

                // 表头
                String[] headers = {"生产编号", "样本名称", "样本类型", "测序项目",
                        "实验室状态", "测序结果说明", "风险检测原因",
                        "预估片段长度", "预估质粒总长度", "交付长度"};
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    headerRow.createCell(i).setCellValue(headers[i]);
                }

                // 数据行
                int rowNum = 1;
                for (DetailRecord record : entry.getValue()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(record.getProductionCode());
                    row.createCell(1).setCellValue(record.getSampleCode());
                    row.createCell(2).setCellValue(record.getSampleType());
                    row.createCell(3).setCellValue(record.getSequencingProject());
                    row.createCell(4).setCellValue(""); // 实验室状态
                    row.createCell(5).setCellValue(""); // 测序结果说明
                    row.createCell(6).setCellValue(""); // 风险检测原因
                    row.createCell(7).setCellValue(record.getEstimatedFragmentSize() != null ? record.getEstimatedFragmentSize() : "");
                    row.createCell(8).setCellValue(""); // 预估质粒总长度

                    // 交付长度：读取 fasta 文件
                    String folderName = codeToFolder.get(record.getProductionCode());
                    int fastaLength = 0;
                    if (folderName != null) {
                        fastaLength = calculateFastaLength(orderDir.resolve(folderName).resolve("Sequence"));
                    }
                    row.createCell(9).setCellValue(fastaLength > 0 ? String.valueOf(fastaLength) : "");
                }

                // 自动调整列宽
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }

                try (FileOutputStream fos = new FileOutputStream(excelPath.toFile())) {
                    workbook.write(fos);
                }
            }
        }

        log.debug("测序明细表生成完成：{} 个订单", orderGroups.size());
    }

    /**
     * 计算 fasta 文件的序列长度（从第二行起取所有大写字母）
     */
    private int calculateFastaLength(Path sequenceDir) {
        if (!Files.exists(sequenceDir) || !Files.isDirectory(sequenceDir)) {
            return 0;
        }

        try (Stream<Path> stream = Files.list(sequenceDir)) {
            Optional<Path> fastaFile = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".fasta"))
                    .findFirst();

            if (fastaFile.isEmpty()) {
                return 0;
            }

            List<String> lines = Files.readAllLines(fastaFile.get());
            if (lines.isEmpty()) {
                return 0;
            }

            int length = 0;
            boolean firstLine = true;
            for (String line : lines) {
                if (firstLine) {
                    firstLine = false;
                    continue; // 跳过 header 行
                }
                for (char c : line.toCharArray()) {
                    if (c >= 'A' && c <= 'Z') {
                        length++;
                    }
                }
            }
            return length;

        } catch (IOException e) {
            log.warn("读取 fasta 文件异常", e);
            return 0;
        }
    }

    /**
     * 生成测序结果压缩包
     */
    private void generateResultZips(Path orderDir, Path outputDir, List<DetailRecord> records,
                                    Map<String, String> codeToFolder) throws IOException {
        Path resultDir = outputDir.resolve(orderDir.getFileName() + SEQUENCING_RESULT_SUFFIX);
        Files.createDirectories(resultDir);

        Map<String, List<DetailRecord>> orderGroups = records.stream()
                .collect(Collectors.groupingBy(r -> r.getOrderId() != null ? r.getOrderId() : "未知订单"));

        for (Map.Entry<String, List<DetailRecord>> entry : orderGroups.entrySet()) {
            String orderId = entry.getKey();
            String customerName = entry.getValue().get(0).getCustomerName();

            // 判断是否全部失败
            boolean allFailed = true;
            List<String> successFolders = new ArrayList<>();

            for (DetailRecord record : entry.getValue()) {
                String folderName = codeToFolder.get(record.getProductionCode());
                if (folderName == null) {
                    continue;
                }
                Path sampleDir = orderDir.resolve(folderName);
                if (!isSampleFailed(sampleDir)) {
                    allFailed = false;
                    successFolders.add(folderName);
                }
            }

            String zipName = customerName + "-" + orderId + "-测序结果";
            if (allFailed) {
                zipName += "NO";
            }
            zipName += ".zip";

            Path zipPath = resultDir.resolve(zipName);
            createSequencingResultZip(orderDir, zipPath, successFolders);
        }

        log.debug("测序结果压缩包生成完成：{} 个订单", orderGroups.size());
    }

    /**
     * 判断样品是否测序失败（四个子目录全空）
     */
    private boolean isSampleFailed(Path sampleDir) {
        if (!Files.exists(sampleDir) || !Files.isDirectory(sampleDir)) {
            return true;
        }
        for (String sub : SAMPLE_SUBDIRS) {
            Path subDir = sampleDir.resolve(sub);
            if (Files.exists(subDir) && Files.isDirectory(subDir)) {
                try (Stream<Path> stream = Files.list(subDir)) {
                    if (stream.findAny().isPresent()) {
                        return false;
                    }
                } catch (IOException e) {
                    log.warn("检查子目录异常：{}", subDir, e);
                }
            }
        }
        return true;
    }

    /**
     * 创建测序结果 ZIP 包
     */
    private void createSequencingResultZip(Path orderDir, Path zipPath, List<String> folderNames) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // 收集文件
            List<Path> bamFiles = new ArrayList<>();
            List<Path> qcFiles = new ArrayList<>();
            List<Path> sequenceFiles = new ArrayList<>();
            List<Path> varFilteredFiles = new ArrayList<>();
            List<Path> varRawFiles = new ArrayList<>();

            for (String folderName : folderNames) {
                Path sampleDir = orderDir.resolve(folderName);

                // Bam
                Path bamDir = sampleDir.resolve("Bam");
                if (Files.exists(bamDir)) {
                    try (Stream<Path> s = Files.list(bamDir)) {
                        s.filter(Files::isRegularFile).forEach(bamFiles::add);
                    }
                }

                // QC
                Path qcDir = sampleDir.resolve("QC");
                if (Files.exists(qcDir)) {
                    try (Stream<Path> s = Files.list(qcDir)) {
                        s.filter(Files::isRegularFile).forEach(qcFiles::add);
                    }
                }

                // Sequence
                Path seqDir = sampleDir.resolve("Sequence");
                if (Files.exists(seqDir)) {
                    try (Stream<Path> s = Files.list(seqDir)) {
                        s.filter(Files::isRegularFile).forEach(sequenceFiles::add);
                    }
                }

                // Var
                Path varDir = sampleDir.resolve("Var");
                if (Files.exists(varDir)) {
                    try (Stream<Path> s = Files.list(varDir)) {
                        s.filter(Files::isRegularFile).forEach(f -> {
                            String name = f.getFileName().toString().toLowerCase();
                            if (name.contains("_filtered")) {
                                varFilteredFiles.add(f);
                            } else if (name.contains("_raw")) {
                                varRawFiles.add(f);
                            }
                        });
                    }
                }
            }

            // 写入 ZIP
            writeZipEntries(zos, "Bam/", bamFiles);
            writeZipEntries(zos, "QC/", qcFiles);
            writeZipEntries(zos, "Sequence/", sequenceFiles);
            writeZipEntries(zos, "Var/", varFilteredFiles);
            writeZipEntries(zos, "Var/", varRawFiles);

            // 合并 var 文件
            if (!varFilteredFiles.isEmpty()) {
                mergeVarFilesToZip(zos, varFilteredFiles, "merged_data.filt.var.xls");
            }
            if (!varRawFiles.isEmpty()) {
                mergeVarFilesToZip(zos, varRawFiles, "merged_data.var.xls");
            }
        }
    }

    private void writeZipEntries(ZipOutputStream zos, String prefix, List<Path> files) throws IOException {
        for (Path file : files) {
            String entryName = prefix + file.getFileName().toString();
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zos);
            zos.closeEntry();
        }
    }

    /**
     * 合并 Var CSV 文件并写入 ZIP 中的 XLS
     */
    private void mergeVarFilesToZip(ZipOutputStream zos, List<Path> csvFiles, String entryName) throws IOException {
        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            int rowNum = 0;
            boolean headerWritten = false;
            List<String> headers = new ArrayList<>();

            for (Path csvFile : csvFiles) {
                List<String> lines = Files.readAllLines(csvFile);
                if (lines.isEmpty()) {
                    continue;
                }

                // 解析表头
                if (!headerWritten) {
                    String[] headerParts = lines.get(0).split(",");
                    headers = Arrays.asList(headerParts);
                    Row headerRow = sheet.createRow(rowNum++);
                    for (int i = 0; i < headers.size(); i++) {
                        headerRow.createCell(i).setCellValue(headers.get(i).trim());
                    }
                    headerWritten = true;
                }

                // 数据行
                for (int i = 1; i < lines.size(); i++) {
                    String[] parts = lines.get(i).split(",");
                    Row row = sheet.createRow(rowNum++);
                    for (int j = 0; j < parts.length && j < headers.size(); j++) {
                        row.createCell(j).setCellValue(parts[j].trim());
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(baos.toByteArray());
            zos.closeEntry();
        }
    }

    /**
     * 递归复制目录
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path dest = target.resolve(relative);
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Path dest = target.resolve(relative);
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
