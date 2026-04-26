-- 拆分任务记录表
CREATE TABLE IF NOT EXISTS split_task_record (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id           TEXT    NOT NULL,            -- 单次 zip 任务唯一 ID（UUID）
    zip_name          TEXT    NOT NULL,            -- 原始压缩包文件名
    zip_size_bytes    INTEGER,                     -- 原始压缩包大小（字节）
    start_time        TEXT    NOT NULL,            -- 任务开始时间（ISO-8601）
    end_time          TEXT,                        -- 任务结束时间（ISO-8601）
    duration_ms       INTEGER,                     -- 任务耗时（毫秒）
    status            TEXT    NOT NULL,            -- 任务状态：SUCCESS / PARTIAL_SUCCESS / FAILED
    order_count       INTEGER DEFAULT 0,           -- 解压后包含的订单（SDHZ）数量
    sample_total      INTEGER DEFAULT 0,           -- 总样品数
    sample_normal     INTEGER DEFAULT 0,           -- 正常拆分的样品数
    sample_empty      INTEGER DEFAULT 0,           -- 空白样品数（三类合计）
    sample_failed     INTEGER DEFAULT 0,           -- 处理失败的样品数
    output_zip_path   TEXT,                        -- 输出 zip 路径
    error_message     TEXT                         -- 错误摘要（失败时填充）
);

CREATE INDEX IF NOT EXISTS idx_record_start_time ON split_task_record(start_time);
CREATE INDEX IF NOT EXISTS idx_record_status     ON split_task_record(status);
CREATE INDEX IF NOT EXISTS idx_record_task_id    ON split_task_record(task_id);
