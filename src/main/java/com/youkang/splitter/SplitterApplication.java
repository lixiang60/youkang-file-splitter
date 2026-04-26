package com.youkang.splitter;

import com.youkang.splitter.config.SplitterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 有康文件拆分服务启动类
 *
 * @author youkang
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SplitterProperties.class)
public class SplitterApplication {

    public static void main(String[] args) {
        SpringApplication.run(SplitterApplication.class, args);
    }
}
