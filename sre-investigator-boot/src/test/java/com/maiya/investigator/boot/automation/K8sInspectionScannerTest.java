package com.maiya.investigator.boot.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.Environment;
import com.maiya.investigator.infra.InMemoryControlPlaneStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class K8sInspectionScannerTest {
    @Test
    void flagsNotReadyCrashLoopAndWarnings() {
        List<K8sInspectionScanner.Finding> nodes = K8sInspectionScanner.inspectNodes(List.of(
                Map.of("name", "n1", "ready", "True"),
                Map.of("name", "n2", "ready", "False")
        ));
        assertEquals(1, nodes.size());
        assertTrue(nodes.get(0).detail().contains("n2"));

        List<K8sInspectionScanner.Finding> pods = K8sInspectionScanner.inspectPods("demo", List.of(
                Map.of("name", "ok", "restarts", 0, "containers", List.of("app")),
                Map.of("name", "loop", "restarts", 8, "containers", List.of("app=CrashLoopBackOff")),
                Map.of("name", "flap", "restarts", 6, "containers", List.of("app"))
        ));
        assertEquals(2, pods.size());
        assertTrue(pods.stream().anyMatch(item -> item.detail().contains("demo/loop")));
        assertTrue(pods.stream().anyMatch(item -> item.detail().contains("demo/flap")));

        List<K8sInspectionScanner.Finding> events = K8sInspectionScanner.inspectEvents("demo", List.of(
                Map.of("type", "Normal", "reason", "Pulled", "object", "Pod/ok", "message", "fine"),
                Map.of("type", "Warning", "reason", "BackOff", "object", "Pod/loop", "message", "back-off restarting")
        ));
        assertEquals(1, events.size());
        assertTrue(events.get(0).title().contains("BackOff"));
    }

    @Test
    void prefersAppNamespacesAndDefersSystemOnes() {
        List<String> names = new java.util.ArrayList<>();
        names.add("kube-system");
        names.add("ack-csi-fuse");
        for (int index = 0; index < 15; index++) {
            names.add("ns-" + index);
        }
        List<String> capped = K8sInspectionScanner.capNamespaces(names);
        assertEquals("ns-0", capped.get(0));
        assertTrue(capped.indexOf("ns-14") < capped.indexOf("kube-system"));
        assertTrue(capped.indexOf("ack-csi-fuse") > capped.indexOf("ns-0"));
        assertTrue(K8sInspectionScanner.deferredNamespace("arms-prom"));
        assertTrue(K8sInspectionScanner.isForbidden(new IllegalStateException(
                "Kuboard HTTP 403：pods is forbidden: User cannot list resource"
        )));
        assertTrue(K8sInspectionScanner.isForbidden(new IllegalStateException(
                "K8s 无权限：用户 changfeng 不能访问 pods（namespace=nacos）。请换 Binding 默认或业务命名空间"
        )));
        assertFalse(K8sInspectionScanner.isForbidden(new IllegalStateException("连接超时")));
    }

    @Test
    void renderHealthyAndRequiresBinding() {
        K8sInspectionScanner.Report ok = K8sInspectionScanner.render(
                Environment.TEST,
                1,
                3,
                2,
                List.of(),
                List.of("## cluster")
        );
        assertFalse(ok.hasFindings());
        assertEquals("未见异常", ok.summary());
        assertTrue(ok.markdown().contains("正常"));

        K8sInspectionScanner scanner = new K8sInspectionScanner(new InMemoryControlPlaneStore(), null);
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> scanner.scan(java.util.UUID.randomUUID(), Environment.TEST)
        );
        assertTrue(error.getMessage().contains("K8s Binding"));
    }
}
