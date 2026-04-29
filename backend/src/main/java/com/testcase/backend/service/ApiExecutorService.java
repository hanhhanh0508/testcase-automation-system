package com.testcase.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testcase.backend.entity.TestCase;
import com.testcase.backend.entity.TestResult;
import com.testcase.backend.enums.TestCaseStatus;
import com.testcase.backend.enums.TestOutcome;
import com.testcase.backend.repository.TestCaseRepository;
import com.testcase.backend.repository.TestResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Thực thi Test Case bằng RestTemplate (HTTP-based, không dùng Selenium).
 *
 * ────────────────────────────────────────────────────────────────
 * Cú pháp nhận dạng trong các bước (Step):
 * HTTP {METHOD} {path} → gọi API
 * EXPECT_STATUS {code} → kiểm tra status code
 * EXPECT_BODY_CONTAINS {text} → kiểm tra body chứa text
 * EXPECT_BODY_FIELD {field} → kiểm tra field tồn tại trong JSON
 * INPUT {key} = {value} → lưu biến vào context (dùng trong body)
 * HEADER {name}: {value} → thêm header vào request tiếp theo
 * SET_BODY {json} → thiết lập body cho request tiếp theo
 * ────────────────────────────────────────────────────────────────
 */
@Service
public class ApiExecutorService {

    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.test.target.baseUrl:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.test.timeout.ms:5000}")
    private int timeoutMs;

    public ApiExecutorService(TestCaseRepository testCaseRepository,
            TestResultRepository testResultRepository) {
        this.testCaseRepository = testCaseRepository;
        this.testResultRepository = testResultRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: chạy 1 test case
    // ─────────────────────────────────────────────────────────────────────────

    public TestResult execute(UUID testCaseId) {
        TestCase tc = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new RuntimeException("TestCase không tồn tại: " + testCaseId));

        tc.setStatus(TestCaseStatus.RUNNING);
        testCaseRepository.save(tc);

        long startMs = System.currentTimeMillis();
        StringBuilder log = new StringBuilder();

        // Ngữ cảnh thực thi: lưu biến và trạng thái giữa các bước
        ExecutionContext ctx = new ExecutionContext();

        try {
            List<String> steps = tc.getSteps();
            log.append("▶ Bắt đầu thực thi: ").append(tc.getTcCode()).append(" — ").append(tc.getName()).append("\n");
            log.append("  Target URL: ").append(baseUrl).append("\n\n");

            for (int i = 0; i < steps.size(); i++) {
                String step = steps.get(i).trim();
                if (step.isEmpty() || step.startsWith("===") || step.startsWith("---")) {
                    log.append("[INFO] ").append(step).append("\n");
                    continue;
                }

                log.append("[Step ").append(i + 1).append("] ").append(step).append("\n");
                processStep(step, ctx, log);
            }

            // Kiểm tra expected result
            String expected = tc.getExpectedResult();
            if (expected != null && !expected.isBlank() && ctx.lastResponseBody != null) {
                if (!ctx.lastResponseBody.toLowerCase().contains(extractKeyword(expected))) {
                    log.append("[WARN] Expected result không tìm thấy trong response cuối.\n");
                }
            }

            int durationMs = (int) (System.currentTimeMillis() - startMs);
            log.append("\n✓ PASSED (").append(durationMs).append("ms)\n");

            tc.setStatus(TestCaseStatus.PASSED);
            testCaseRepository.save(tc);
            return saveResult(tc, TestOutcome.PASSED, log.toString(), null, durationMs);

        } catch (AssertionError ae) {
            int durationMs = (int) (System.currentTimeMillis() - startMs);
            log.append("\n✗ FAILED: ").append(ae.getMessage()).append("\n");
            tc.setStatus(TestCaseStatus.FAILED);
            testCaseRepository.save(tc);
            return saveResult(tc, TestOutcome.FAILED, log.toString(), ae.getMessage(), durationMs);

        } catch (Exception ex) {
            int durationMs = (int) (System.currentTimeMillis() - startMs);
            log.append("\n✗ ERROR: ").append(ex.getMessage()).append("\n");
            tc.setStatus(TestCaseStatus.FAILED);
            testCaseRepository.save(tc);
            return saveResult(tc, TestOutcome.ERROR, log.toString(), ex.getMessage(), durationMs);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: batch
    // ─────────────────────────────────────────────────────────────────────────

    public Map<UUID, TestResult> executeBatch(List<UUID> ids) {
        Map<UUID, TestResult> results = new LinkedHashMap<>();
        for (UUID id : ids) {
            try {
                results.put(id, execute(id));
            } catch (Exception ex) {
                TestCase tc = testCaseRepository.findById(id).orElse(null);
                if (tc != null) {
                    tc.setStatus(TestCaseStatus.FAILED);
                    testCaseRepository.save(tc);
                    results.put(id, saveResult(tc, TestOutcome.ERROR, null, ex.getMessage(), 0));
                }
            }
            // Nghỉ nhỏ giữa các TC để tránh quá tải
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
        }
        return results;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE: xử lý từng bước
    // ─────────────────────────────────────────────────────────────────────────

    private void processStep(String step, ExecutionContext ctx, StringBuilder log) {
        String upper = step.toUpperCase();

        // ── HTTP Call ──
        if (upper.startsWith("HTTP ")) {
            handleHttpStep(step, ctx, log);
            return;
        }

        // ── EXPECT_STATUS ──
        if (upper.startsWith("EXPECT_STATUS")) {
            handleExpectStatus(step, ctx, log);
            return;
        }

        // ── EXPECT_BODY_CONTAINS ──
        if (upper.startsWith("EXPECT_BODY_CONTAINS")) {
            String expected = step.substring(step.indexOf(' ') + 1).trim().replaceAll("\"", "");
            if (ctx.lastResponseBody == null || !ctx.lastResponseBody.toLowerCase().contains(expected.toLowerCase())) {
                throw new AssertionError(
                        "Body không chứa: \"" + expected + "\"\n  Actual body: " + summarize(ctx.lastResponseBody));
            }
            log.append("  ✓ body contains \"").append(expected).append("\"\n");
            return;
        }

        // ── EXPECT_BODY_FIELD ──
        if (upper.startsWith("EXPECT_BODY_FIELD")) {
            String field = step.substring(step.indexOf(' ') + 1).trim();
            assertBodyField(field, ctx, log);
            return;
        }

        // ── INPUT ──
        if (upper.startsWith("INPUT ")) {
            String payload = step.substring(6).trim();
            String[] parts = payload.split("=", 2);
            if (parts.length == 2) {
                ctx.variables.put(parts[0].trim(), parts[1].trim().replaceAll("\"", ""));
                log.append("  → set variable: ").append(parts[0].trim()).append(" = ").append(parts[1].trim())
                        .append("\n");
            }
            return;
        }

        // ── HEADER ──
        if (upper.startsWith("HEADER ")) {
            String h = step.substring(7).trim();
            String[] hParts = h.split(":", 2);
            if (hParts.length == 2) {
                ctx.headers.set(hParts[0].trim(), hParts[1].trim());
                log.append("  → header set: ").append(hParts[0].trim()).append("\n");
            }
            return;
        }

        // ── SET_BODY ──
        if (upper.startsWith("SET_BODY ")) {
            ctx.pendingBody = step.substring(9).trim();
            log.append("  → body set\n");
            return;
        }

        // ── Mô tả thuần (VD, INPUT description, etc.) ──
        log.append("  (mô tả) ").append(step).append("\n");
    }

    private void handleHttpStep(String step, ExecutionContext ctx, StringBuilder log) {
        // Format: HTTP {METHOD} {path} [body: {json}]
        String[] tokens = step.split("\\s+", 4);
        if (tokens.length < 3) {
            log.append("  ⚠ HTTP step không đúng format: ").append(step).append("\n");
            return;
        }

        String method = tokens[1].toUpperCase();
        String rawPath = tokens[2];

        // Thay thế placeholder {resource}, {endpoint}, {id} bằng biến trong ctx
        String path = resolvePlaceholders(rawPath, ctx.variables);

        // Body từ step hoặc pendingBody
        String body = ctx.pendingBody;
        if (step.toLowerCase().contains("body:")) {
            int idx = step.toLowerCase().indexOf("body:");
            body = step.substring(idx + 5).trim();
        }

        String url = baseUrl + (path.startsWith("/") ? path : "/" + path);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // Copy custom headers
        ctx.headers.forEach((name, values) -> headers.put(name, values));

        HttpEntity<String> entity = new HttpEntity<>(body != null && !body.isEmpty() ? body : null, headers);

        log.append("  → ").append(method).append(" ").append(url).append("\n");
        if (body != null && !body.isEmpty()) {
            log.append("  → body: ").append(summarize(body)).append("\n");
        }

        try {
            ResponseEntity<String> response = switch (method) {
                case "GET" -> restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                case "POST" -> restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                case "PUT" -> restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
                case "DELETE" -> restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
                case "PATCH" -> restTemplate.exchange(url, HttpMethod.PATCH, entity, String.class);
                default -> throw new IllegalArgumentException("HTTP method không hợp lệ: " + method);
            };

            ctx.lastStatusCode = response.getStatusCode().value();
            ctx.lastResponseBody = response.getBody();
            log.append("  ← status: ").append(ctx.lastStatusCode).append("\n");
            log.append("  ← body: ").append(summarize(ctx.lastResponseBody)).append("\n");

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            ctx.lastStatusCode = ex.getStatusCode().value();
            ctx.lastResponseBody = ex.getResponseBodyAsString();
            log.append("  ← status: ").append(ctx.lastStatusCode).append(" (error response)\n");
            log.append("  ← body: ").append(summarize(ctx.lastResponseBody)).append("\n");
        }

        ctx.pendingBody = null;
    }

    private void handleExpectStatus(String step, ExecutionContext ctx, StringBuilder log) {
        // Có thể là "EXPECT_STATUS 200" hoặc "EXPECT_STATUS 400 hoặc 401 hoặc 422"
        String rest = step.replaceAll("(?i)expect_status", "").trim();
        // Lấy tất cả số trong chuỗi
        List<Integer> expectedCodes = new ArrayList<>();
        for (String token : rest.split("[^0-9]+")) {
            if (!token.isEmpty()) {
                try {
                    expectedCodes.add(Integer.parseInt(token));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (expectedCodes.isEmpty()) {
            log.append("  ⚠ Không parse được expected status\n");
            return;
        }

        if (ctx.lastStatusCode == null) {
            log.append("  ⚠ Chưa có HTTP request nào được thực hiện — bỏ qua kiểm tra status\n");
            return;
        }

        if (!expectedCodes.contains(ctx.lastStatusCode)) {
            throw new AssertionError(
                    "HTTP status mismatch. Expected: " + expectedCodes + ", Actual: " + ctx.lastStatusCode);
        }
        log.append("  ✓ status ").append(ctx.lastStatusCode).append(" ∈ ").append(expectedCodes).append("\n");
    }

    private void assertBodyField(String fieldPath, ExecutionContext ctx, StringBuilder log) {
        if (ctx.lastResponseBody == null) {
            log.append("  ⚠ Không có response body để kiểm tra field: ").append(fieldPath).append("\n");
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(ctx.lastResponseBody);
            JsonNode node = root;
            for (String key : fieldPath.split("\\.")) {
                if (node == null || node.isMissingNode())
                    break;
                node = node.get(key);
            }
            if (node == null || node.isNull() || node.isMissingNode()) {
                throw new AssertionError("Field không tồn tại trong response: " + fieldPath);
            }
            log.append("  ✓ field \"").append(fieldPath).append("\" tồn tại: ").append(summarize(node.toString()))
                    .append("\n");
        } catch (AssertionError ae) {
            throw ae;
        } catch (Exception e) {
            log.append("  ⚠ Không parse được JSON body: ").append(e.getMessage()).append("\n");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String resolvePlaceholders(String path, Map<String, String> vars) {
        // Thay {resource}, {endpoint}, {id} etc. bằng giá trị thực nếu có
        String resolved = path;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            resolved = resolved.replace("{" + e.getKey() + "}", e.getValue());
        }
        return resolved;
    }

    private String summarize(String s) {
        if (s == null)
            return "(null)";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private String extractKeyword(String expected) {
        for (String kw : new String[] { "thành công", "success", "token", "data" }) {
            if (expected.toLowerCase().contains(kw))
                return kw;
        }
        String[] words = expected.trim().split("\\s+");
        return words.length > 0 ? words[words.length - 1].toLowerCase() : "";
    }

    private TestResult saveResult(TestCase tc, TestOutcome outcome,
            String actualResult, String errorMsg, int durationMs) {
        TestResult r = new TestResult();
        r.setTestCase(tc);
        r.setOutcome(outcome);
        r.setActualResult(actualResult);
        r.setErrorMessage(errorMsg);
        r.setDurationMs(durationMs);
        return testResultRepository.save(r);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INNER CLASS: ngữ cảnh thực thi
    // ─────────────────────────────────────────────────────────────────────────

    private static class ExecutionContext {
        /** Biến được set bởi lệnh INPUT */
        Map<String, String> variables = new HashMap<>();
        /** Custom headers */
        HttpHeaders headers = new HttpHeaders();
        /** Status code của HTTP request cuối */
        Integer lastStatusCode = null;
        /** Body của HTTP response cuối */
        String lastResponseBody = null;
        /** Body chờ gửi cho HTTP request tiếp theo */
        String pendingBody = null;
    }
}