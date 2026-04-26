# youkang-file-splitter

有康生信产物文件拆分服务。长期运行在 Linux 服务器上，定时扫描指定目录，自动解压用户上传的 ZIP 压缩包，按照统一规则将原始产物归类到 `Bam / Var / Sequence / QC` 四个目录后重新打包输出。

## 技术栈

- Java 17
- Spring Boot 3.5.4
- SQLite（本地任务记录，零外部依赖）
- zip4j 2.11.5（ZIP 解压/打包，中文文件名兼容）
- Commons IO 2.19.0

## 核心拆分规则

1. **目录层级**：保留订单号（`SDHZxxxxx`）和样品号（`YKxxxxxxxx-...`）两层命名。
2. **源目录定位**：
   - 若样品文件夹下存在 `reference_analysis` 子目录，从该子目录内提取（A 类）。
   - 否则从样品文件夹根目录直接提取（B 类）。
3. **提取规则**（A/B 类完全一致）：
   - **Bam**：扩展名为 `.bam` 或 `.bai`。
   - **Var**：文件名包含 `_filtered` 或 `_raw` 的表格文件（`.csv` / `.xls` / `.xlsx`）。
   - **Sequence**：全部 `.ab1` 峰图文件 + 经筛选后的 `.fasta` 序列文件。
     - 先剔除文件名含 `all` 或 `best` 的 fasta。
     - 若同时存在含 `assembly` 和含 `insertseq` 的 fasta，仅保留 `insertseq`。
     - 否则全部保留（如 `final_consensus`、`hap1`、`hap2` 等）。
   - **QC**：文件名含 `coverage` 的 PNG 图片、含 `frequency_wave` 的 HTML、或含 `plann` 的 HTML。
4. **空白样品**：若样品下无任何文件命中上述规则，拆分后不创建子目录（空白文件夹）。

## 目录结构

```
youkang-file-splitter/
├── pom.xml
├── README.md
├── deploy/
│   ├── youkang-splitter.service        # systemd 服务模板
│   └── application-prod.yml.example    # 生产配置样例
├── src/main/java/com/youkang/splitter/
│   ├── SplitterApplication.java        # 启动类
│   ├── config/
│   │   ├── SplitterProperties.java     # 配置参数绑定
│   │   └── DataSourceConfig.java       # SQLite 数据源
│   ├── scheduler/
│   │   └── SplitTaskScheduler.java     # @Scheduled 定时入口
│   ├── runner/
│   │   └── SplitTaskRunner.java        # 单次扫描周期编排
│   ├── service/
│   │   ├── ZipExtractor.java           # ZIP 解压
│   │   ├── ZipPackager.java            # ZIP 重新打包
│   │   ├── ReadyChecker.java           # 文件就绪检测（双次大小比对）
│   │   ├── FileSplitterService.java    # 拆分核心规则
│   │   └── TaskRecorder.java           # 任务记录写入 SQLite
│   ├── domain/
│   │   ├── SampleFolderClassification.java
│   │   ├── SplitBatchResult.java
│   │   ├── SplitOutcome.java
│   │   └── SplitTaskRecord.java
│   └── repository/
│       └── SplitTaskRecordRepository.java
└── src/main/resources/
    ├── application.yml                   # 默认配置（开发参考）
    ├── application-dev.yml               # 开发环境配置（Windows 路径）
    ├── logback-spring.xml                # 日志滚动策略
    └── db/schema.sql                     # SQLite 建表语句
```

## 构建

```bash
mvn clean package -DskipTests
```

产出：`target/youkang-file-splitter.jar`（fat jar，可直接 `java -jar` 运行）。

## 本地验证（Windows）

```bash
mvn spring-boot:run
```

默认加载 `application-dev.yml`，使用 `D:/youkang/splitter/` 下的各工作目录。

## 部署（Linux）

1. 创建运行用户与工作目录：
   ```bash
   sudo useradd -r -s /bin/false youkang
   sudo mkdir -p /data/youkang/{inbox,work,output,failed,archive,splitter/logs}
   sudo chown -R youkang:youkang /data/youkang
   ```

2. 复制 jar 与配置：
   ```bash
   sudo mkdir -p /opt/youkang-splitter
   sudo cp target/youkang-file-splitter.jar /opt/youkang-splitter/
   sudo cp deploy/application-prod.yml.example /opt/youkang-splitter/application.yml
   # 按需编辑 application.yml
   ```

3. 安装 systemd 服务：
   ```bash
   sudo cp deploy/youkang-splitter.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable youkang-splitter
   sudo systemctl start youkang-splitter
   ```

4. 查看日志：
   ```bash
   sudo journalctl -u youkang-splitter -f
   ```

## 测试

```bash
mvn test
```

测试覆盖：
- `FileSplitterServiceTest`：使用 `D:/document/2026-0424版` 真实样本，断言 25 个样品的拆分结果与规则一致（含 A/B 类、hap 场景、insertseq 场景、空白场景）。
- `ReadyCheckerTest`：就绪检测逻辑（空文件、稳定文件、变化文件）。

## 任务记录查询

SQLite 数据库默认位于 `/data/youkang/splitter/records.db`，可通过命令行查询：

```bash
sqlite3 /data/youkang/splitter/records.db "SELECT * FROM split_task_record ORDER BY start_time DESC LIMIT 10;"
```

## 配置项说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `splitter.inbox-dir` | 用户上传 zip 的入口目录 | `/data/youkang/inbox` |
| `splitter.work-dir` | 解压与中间产物工作目录 | `/data/youkang/work` |
| `splitter.output-dir` | 拆分后 zip 输出目录 | `/data/youkang/output` |
| `splitter.failed-dir` | 异常 zip 隔离目录 | `/data/youkang/failed` |
| `splitter.archive-dir` | 成功归档目录 | `/data/youkang/archive` |
| `splitter.scan-cron` | 扫描定时 cron 表达式 | `0 */1 * * * ?`（每分钟） |
| `splitter.ready-check-interval-seconds` | 文件就绪检测间隔 | `10` |
| `splitter.sqlite-path` | SQLite 数据库路径 | `/data/youkang/splitter/records.db` |
| `splitter.delete-after-archive` | 归档后是否删除原 zip | `false` |

## 注意事项

- 同一时刻只有一个扫描周期在执行（`ReentrantLock.tryLock()` 防并发）。
- 单个样品拆分异常不会中断整个订单，最终状态记为 `PARTIAL_SUCCESS`。
- zip 解压失败或目录结构异常会移至 `failed-dir`，状态记为 `FAILED`。
- 日志默认滚动保留 30 天，单文件最大 50MB。
