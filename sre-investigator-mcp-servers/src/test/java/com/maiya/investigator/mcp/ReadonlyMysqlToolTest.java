package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.infra.InMemoryControlPlaneStore;
import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.infra.persistence.AuditEventJpaRepository;
import com.maiya.investigator.infra.security.SecretCipher;
import com.maiya.investigator.mcp.config.ReadonlyToolProperties;
import com.maiya.investigator.mcp.config.ReadonlyToolProperties.MysqlProfile;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ReadonlyMysqlToolTest {
    @Test
    void investigationQueryDoesNotFallBackToYamlProfile() {
        ReadonlyToolProperties properties = new ReadonlyToolProperties();
        MysqlProfile yaml = new MysqlProfile();
        yaml.setHost("127.0.0.1");
        yaml.setDatabase("sre_investigator");
        yaml.setUsername("root");
        properties.getMysqlProfiles().put("test", yaml);

        ReadonlyMysqlTool tool = new ReadonlyMysqlTool(
                properties,
                audit(),
                new BindingConnectionLookup(new InMemoryControlPlaneStore(), new SecretCipher(""), new ObjectMapper())
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> tool.query(UUID.randomUUID(), "TEST", "my_test", "SELECT 1")
        );
        assertTrue(exception.getMessage().contains("不会回退"));
    }

    private static AuditTrail audit() {
        return new AuditTrail(new ObjectProvider<>() {
            @Override
            public AuditEventJpaRepository getObject() {
                return null;
            }

            @Override
            public AuditEventJpaRepository getObject(Object... args) {
                return null;
            }

            @Override
            public AuditEventJpaRepository getIfAvailable() {
                return null;
            }

            @Override
            public AuditEventJpaRepository getIfUnique() {
                return null;
            }

            @Override
            public Iterator<AuditEventJpaRepository> iterator() {
                return List.<AuditEventJpaRepository>of().iterator();
            }
        });
    }
}
