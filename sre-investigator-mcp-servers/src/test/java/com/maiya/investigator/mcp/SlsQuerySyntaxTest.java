package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SlsQuerySyntaxTest {

    @Test
    void normalizeKeepsStandardKeyValueAndBareId() {
        assertEquals("traceId: abc", SlsQuerySyntax.normalize("traceId: abc"));
        assertEquals("traceId: abc", SlsQuerySyntax.normalize("traceId=\"abc\""));
        assertEquals("traceId: abc", SlsQuerySyntax.normalize("traceId=abc"));
        assertEquals("b34cff7175217b3811c163a3517257dd", SlsQuerySyntax.normalize("b34cff7175217b3811c163a3517257dd"));
        assertEquals("", SlsQuerySyntax.normalize("  "));
    }

    @Test
    void normalizeKeepsOldJsonFragmentForm() {
        assertEquals("traceId\":\"abc", SlsQuerySyntax.normalize("traceId\":\"abc"));
        assertEquals("\"traceId: abc\"", SlsQuerySyntax.normalize("\"traceId: abc\""));
    }

    @Test
    void candidatesPreferKeyValueThenFragmentThenBare() {
        List<String> fromColon = SlsQuerySyntax.candidates("traceId: 72fb5cdae565fd9a6742652cdf59e44f");
        assertEquals("traceId: 72fb5cdae565fd9a6742652cdf59e44f", fromColon.get(0));
        assertTrue(fromColon.contains("traceId\":\"72fb5cdae565fd9a6742652cdf59e44f"));
        assertTrue(fromColon.contains("72fb5cdae565fd9a6742652cdf59e44f"));

        // 旧提示写法：全文碎片零命中时，候选里必须有标准 KV 与裸 id
        List<String> fromFragment = SlsQuerySyntax.candidates("traceId\":\"72fb5cdae565fd9a6742652cdf59e44f");
        assertTrue(fromFragment.contains("traceId: 72fb5cdae565fd9a6742652cdf59e44f"));
        assertTrue(fromFragment.contains("72fb5cdae565fd9a6742652cdf59e44f"));
    }

    @Test
    void noisyAndQueryIsolatesBareHexFirst() {
        List<String> noisy = SlsQuerySyntax.candidates(
                "traceId:72fb5cdae565fd9a6742652cdf59e44f AND VmsTheaterController OR TheaterController"
        );
        assertEquals("72fb5cdae565fd9a6742652cdf59e44f", noisy.get(0));
        assertTrue(noisy.contains("traceId: 72fb5cdae565fd9a6742652cdf59e44f"));
        assertTrue(noisy.stream().noneMatch(item -> item.contains("VmsTheaterController")));
        assertTrue(noisy.stream().noneMatch(item -> item.toUpperCase().contains(" AND ")));
    }

    @Test
    void detectsPhraseFallbackFromSlsError() {
        assertTrue(SlsQuerySyntax.needsPhraseFallback(
                "SLS HTTP 400：{\"errorCode\":\"ParameterInvalid\",\"errorMessage\":\"key (trace_id) is not config as key value config,if symbol : is  in your log,please wrap : with quotation mark \\\"\"}"
        ));
        assertFalse(SlsQuerySyntax.needsPhraseFallback("SLS HTTP 401：SignatureNotMatch"));
        assertEquals("\"traceId: abc\"", SlsQuerySyntax.asPhrase("traceId: abc"));
    }
}
