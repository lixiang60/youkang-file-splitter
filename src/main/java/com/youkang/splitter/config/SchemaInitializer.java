package com.youkang.splitter.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 数据库表结构初始化
 * 依赖 DataSource，在数据源就绪后自动执行 schema.sql
 *
 * @author youkang
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaInitializer {

    private final DataSource dataSource;

    /**
     * 启动时执行 schema.sql 建表
     * 用 IF NOT EXISTS 保证幂等
     */
    @PostConstruct
    public void initSchema() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/schema.sql"));
        populator.setSeparator(";");
        populator.setIgnoreFailedDrops(true);
        populator.execute(dataSource);
        log.info("SQLite 表结构初始化完成");
    }
}
