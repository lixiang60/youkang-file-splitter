package com.youkang.splitter.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SQLite 数据源配置
 *
 * @author youkang
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSourceConfig {

    private final SplitterProperties splitterProperties;

    @Bean
    public DataSource dataSource() {
        Path dbPath = Paths.get(splitterProperties.getSqlitePath()).toAbsolutePath();
        ensureParentDir(dbPath);

        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath);
        // SQLite 性能与并发优化
        ds.setEnforceForeignKeys(true);
        ds.setJournalMode("WAL");
        ds.setSynchronous("NORMAL");
        log.info("SQLite 数据源初始化：{}", dbPath);
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private void ensureParentDir(Path dbPath) {
        Path parent = dbPath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 SQLite 父目录：" + parent, e);
        }
    }
}
