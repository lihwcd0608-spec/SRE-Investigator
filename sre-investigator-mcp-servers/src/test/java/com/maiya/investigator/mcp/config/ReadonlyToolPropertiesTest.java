package com.maiya.investigator.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.mcp.config.ReadonlyToolProperties.MysqlProfile;
import org.junit.jupiter.api.Test;

class ReadonlyToolPropertiesTest {
    @Test
    void configuredRequiresHostDatabaseAndUsername() {
        MysqlProfile profile = new MysqlProfile();
        assertFalse(profile.configured());

        profile.setHost("127.0.0.1");
        profile.setDatabase("app");
        profile.setUsername("readonly");
        assertTrue(profile.configured());
    }

    @Test
    void jdbcUrlAndMysqlUriUseYmlFields() {
        MysqlProfile profile = new MysqlProfile();
        profile.setHost("db-test");
        profile.setPort(3307);
        profile.setDatabase("app");
        profile.setUsername("readonly");
        profile.setPassword("secret");

        assertEquals(
                "jdbc:mysql://db-test:3307/app?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                profile.jdbcUrl()
        );
        assertEquals("mysql://readonly:secret@db-test:3307/app", profile.mysqlUri());
    }
}
