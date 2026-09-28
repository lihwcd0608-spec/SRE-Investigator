package com.maiya.investigator.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.mcp.NamedMcpFocus;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestigationBriefTest {
    @Test
    void rewritesEntryIdsAndForbidsGuessingTables() {
        String brief = InvestigationBrief.rewrite(
                "@ReserveDramaInsertProcessor 测试反馈test环境foryou预约剧强插方案没生效。使用my-test mcp看下这个问题，补充信息如下： 强插位id：250 用户Id:：2010994477046091778",
                "TEST",
                List.of("my_test"),
                List.of("test"),
                "my_test → pc-example.rds.aliyuncs.com/demo_video",
                "test → r-example.redis.rds.aliyuncs.com:6379"
        );

        assertTrue(brief.contains("@ReserveDramaInsertProcessor"));
        assertTrue(brief.contains("仅入口"));
        assertTrue(brief.contains("250"));
        assertTrue(brief.contains("2010994477046091778"));
        assertTrue(brief.contains("my_test"));
        assertTrue(brief.contains("demo_video"));
        assertTrue(brief.contains("禁止按业务名猜测表名"));
        assertTrue(brief.contains("my-test"));
        assertTrue(brief.contains("本轮可用 MySQL MCP：my_test"));
        assertTrue(brief.contains("本轮可用 Redis MCP：test"));
        assertTrue(!brief.contains("SLS MCP"));
        assertTrue(!brief.contains("K8s MCP"));
        assertTrue(!brief.contains("必须使用的"));
    }

    @Test
    void includesSlsAndMseProfiles() {
        String brief = InvestigationBrief.rewrite(
                "traceId: abc 接口 5xx",
                "TEST",
                List.of(),
                List.of(),
                "",
                "",
                List.of("sls-test"),
                List.of("mse-test"),
                "sls-test → cn-hangzhou/demo/app-log",
                "mse-test → cn-hangzhou gw=gw-1"
        );
        assertTrue(brief.contains("sls-test"));
        assertTrue(brief.contains("mse-test"));
        assertTrue(brief.contains("sls_query_readonly"));
        assertTrue(brief.contains("不要编造年份"));
        assertTrue(brief.contains("mse_*"));
        assertTrue(brief.contains("本轮可用 SLS MCP：sls-test"));
        assertTrue(!brief.contains("K8s MCP"));
    }

    @Test
    void includesK8sProfiles() {
        String brief = InvestigationBrief.rewrite(
                "pod CrashLoop",
                "TEST",
                List.of(),
                List.of(),
                "",
                "",
                List.of(),
                List.of(),
                "",
                "",
                List.of("k8s-test"),
                "k8s-test → kuboard.example.com cluster=prod ns=demo"
        );
        assertTrue(brief.contains("k8s-test"));
        assertTrue(brief.contains("k8s_*"));
        assertTrue(brief.contains("不得重启"));
        assertTrue(brief.contains("本轮可用 K8s MCP：k8s-test"));
    }

    @Test
    void usesFocusedWorkOrderWhenNamedMcpResolved() {
        NamedMcpFocus focus = new NamedMcpFocus("zg_mongodb_test", ToolKind.MONGODB);
        String brief = InvestigationBrief.rewrite(
                "查用户画像，使用 zg_mongodb_test mcp",
                "TEST",
                List.of(),
                List.of(),
                "",
                "",
                List.of(),
                List.of(),
                "",
                "",
                List.of(),
                "",
                List.of(),
                "",
                List.of(),
                "",
                List.of(),
                "",
                List.of("zg_mongodb_test"),
                "zg_mongodb_test → mongo.example.com/demo_test",
                focus
        );
        assertTrue(brief.contains("【硬约束】"));
        assertTrue(brief.contains("zg_mongodb_test"));
        assertTrue(brief.contains("mongodb_query_readonly"));
        assertTrue(brief.contains("本轮可用 MongoDB MCP：zg_mongodb_test"));
        assertTrue(!brief.contains("Redis MCP"));
        assertTrue(brief.contains("JSON 代码块"));
    }
}
