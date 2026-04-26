package com.youkang.splitter.repository;

import com.youkang.splitter.domain.SplitTaskRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 拆分任务记录持久化（SQLite）
 *
 * @author youkang
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SplitTaskRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
            INSERT INTO split_task_record
            (task_id, zip_name, zip_size_bytes, start_time, status, order_count,
             sample_total, sample_normal, sample_empty, sample_failed, output_zip_path, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_SQL = """
            UPDATE split_task_record
            SET end_time = ?, duration_ms = ?, status = ?, order_count = ?,
                sample_total = ?, sample_normal = ?, sample_empty = ?,
                sample_failed = ?, output_zip_path = ?, error_message = ?
            WHERE id = ?
            """;

    /**
     * 插入一条初始任务记录，返回自增主键
     */
    public Long insert(SplitTaskRecord record) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, record.getTaskId());
            ps.setString(2, record.getZipName());
            setLongOrNull(ps, 3, record.getZipSizeBytes());
            ps.setString(4, toIsoString(record.getStartTime()));
            ps.setString(5, record.getStatus());
            setIntOrNull(ps, 6, record.getOrderCount());
            setIntOrNull(ps, 7, record.getSampleTotal());
            setIntOrNull(ps, 8, record.getSampleNormal());
            setIntOrNull(ps, 9, record.getSampleEmpty());
            setIntOrNull(ps, 10, record.getSampleFailed());
            ps.setString(11, record.getOutputZipPath());
            ps.setString(12, record.getErrorMessage());
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    /**
     * 根据主键更新记录
     */
    public void update(SplitTaskRecord record) {
        jdbcTemplate.update(UPDATE_SQL,
                toIsoString(record.getEndTime()),
                record.getDurationMs(),
                record.getStatus(),
                record.getOrderCount(),
                record.getSampleTotal(),
                record.getSampleNormal(),
                record.getSampleEmpty(),
                record.getSampleFailed(),
                record.getOutputZipPath(),
                record.getErrorMessage(),
                record.getId());
    }

    /**
     * 按主键查询
     */
    public SplitTaskRecord findById(Long id) {
        List<SplitTaskRecord> list = jdbcTemplate.query(
                "SELECT * FROM split_task_record WHERE id = ?",
                new BeanPropertyRowMapper<>(SplitTaskRecord.class), id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按任务 ID 查询
     */
    public SplitTaskRecord findByTaskId(String taskId) {
        List<SplitTaskRecord> list = jdbcTemplate.query(
                "SELECT * FROM split_task_record WHERE task_id = ?",
                new BeanPropertyRowMapper<>(SplitTaskRecord.class), taskId);
        return list.isEmpty() ? null : list.get(0);
    }

    private void setLongOrNull(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, java.sql.Types.BIGINT);
        }
    }

    private void setIntOrNull(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, java.sql.Types.INTEGER);
        }
    }

    private String toIsoString(LocalDateTime time) {
        return time != null ? time.toString() : null;
    }
}
