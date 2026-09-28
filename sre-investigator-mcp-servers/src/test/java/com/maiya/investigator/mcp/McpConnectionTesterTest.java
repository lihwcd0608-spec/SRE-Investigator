package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.McpConnectionTestRequest;
import com.maiya.investigator.api.McpConnectionTestResponse;
import com.maiya.investigator.mcp.aliyun.AliyunCloudClient;
import com.maiya.investigator.mcp.aliyun.MseRpcClient;
import com.maiya.investigator.mcp.aliyun.SlsLogClient;
import com.maiya.investigator.mcp.config.AliyunCloudProperties;
import com.maiya.investigator.mcp.k8s.KuboardClient;
import org.junit.jupiter.api.Test;

class McpConnectionTesterTest {
    private final McpConnectionTester tester = new McpConnectionTester(
            new SlsLogClient(),
            new MseRpcClient(),
            new KuboardClient(),
            new com.maiya.investigator.mcp.mq.KafkaReadonlyClient(),
            new com.maiya.investigator.mcp.mq.RocketMqReadonlyClient(),
            new AliyunCloudClient(new AliyunCloudProperties())
    );

    @Test
    void mysqlRequiresHostDatabaseAndUsername() {
        McpConnectionTestResponse result = tester.test(new McpConnectionTestRequest(
                "MYSQL", "", 3306, "", "readonly", "x", null, null, null
        ));
        assertFalse(result.ok());
        assertTrue(result.message().contains("主机"));
    }

    @Test
    void mongoRequiresHostOrUri() {
        McpConnectionTestResponse result = tester.test(new McpConnectionTestRequest(
                "MONGODB", "", 27017, "", null, null, null, null, null
        ));
        assertFalse(result.ok());
        assertTrue(result.message().contains("主机"));
    }

    @Test
    void elasticsearchRequiresHostOrUri() {
        McpConnectionTestResponse result = tester.test(new McpConnectionTestRequest(
                "ELASTICSEARCH", "", 9200, null, null, null, null, null, null
        ));
        assertFalse(result.ok());
        assertTrue(result.message().contains("主机"));
    }

    @Test
    void codeRequiresExistingAbsoluteDirectory() {
        McpConnectionTestResponse missing = tester.test(new McpConnectionTestRequest(
                "CODE", null, null, null, null, null, null, null, "/definitely-not-there-sre-investigator"
        ));
        assertFalse(missing.ok());

        McpConnectionTestResponse relative = tester.test(new McpConnectionTestRequest(
                "CODE", null, null, null, null, null, null, null, "relative/path"
        ));
        assertFalse(relative.ok());
        assertTrue(relative.message().contains("绝对路径"));
    }

    @Test
    void logsRequiresRegionProjectAndKeys() {
        McpConnectionTestResponse result = tester.test(new McpConnectionTestRequest(
                "LOGS", null, null, null, null, null, null, null, null,
                "", "demo", "app-log", "", "", null, null, null
        ));
        assertFalse(result.ok());
        assertTrue(result.message().contains("regionId"));
    }

    @Test
    void mseRequiresRegionAndKeys() {
        McpConnectionTestResponse result = tester.test(new McpConnectionTestRequest(
                "MSE", null, null, null, null, null, null, null, null,
                "", null, null, "", "", null, null, null
        ));
        assertFalse(result.ok());
        assertTrue(result.message().contains("regionId"));
    }

    @Test
    void k8sRequiresBaseUrlUserAndPassword() {
        McpConnectionTestResponse result = tester.test(new McpConnectionTestRequest(
                "K8S", null, null, null, "", "", null, null, null,
                null, null, null, null, null, null, null, null,
                "https://kuboard.example.com", "", null, null
        ));
        assertFalse(result.ok());
        assertTrue(result.message().contains("Kuboard"));
    }

    @Test
    void armsRequiresGlobalAccessKey() {
        McpConnectionTestResponse result = tester.test(new McpConnectionTestRequest(
                "ARMS", null, null, null, null, null, null, null, null,
                "cn-hangzhou", null, null, null, null, null, null, null
        ));
        assertFalse(result.ok());
        assertTrue(result.message().contains("ALIBABA_CLOUD_ACCESS_KEY_ID"));
    }
}
