package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.infra.InMemoryControlPlaneStore;
import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.infra.persistence.AuditEventJpaRepository;
import com.maiya.investigator.infra.security.SecretCipher;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ReadonlyMongodbToolTest {
    private static final String MASTER_KEY = "NQPF9Blwik4godZmbz3FXo++wXei9Y4MD6cpSBTCPFQ=";

    @Test
    void requiresProjectBinding() {
        ReadonlyMongodbTool tool = new ReadonlyMongodbTool(
                new BindingConnectionLookup(new InMemoryControlPlaneStore(), new SecretCipher(MASTER_KEY), new ObjectMapper()),
                new AuditTrail(new ObjectProvider<>() {
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
                })
        );
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> tool.listDatabases(UUID.randomUUID(), "TEST", "mongo-test")
        );
        assertTrue(exception.getMessage().contains("没有已启用"));
    }
}
