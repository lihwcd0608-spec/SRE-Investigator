package com.maiya.investigator.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 启动时先 repair 再 migrate。
 *
 * <p>远程 RDS 每条 DDL 都慢，IDEA 中途停 Debug 容易在 flyway_schema_history 留下半截 failed 记录，
 * 下次启动就报「Detected failed migration」。repair 会清掉失败记录并对齐 checksum，随后正常 migrate。
 * 只在真正跑 Flyway 的 mysql 环境生效，内存单测不加载。
 */
@Configuration
@Profile("mysql")
public class FlywayRepairConfig {
    private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfig.class);

    @Bean
    FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            try {
                flyway.repair();
            } catch (RuntimeException exception) {
                // repair 失败不阻断启动，交给 migrate 决定是否真的有问题
                log.warn("Flyway repair 跳过：{}", exception.getMessage());
            }
            flyway.migrate();
        };
    }
}
