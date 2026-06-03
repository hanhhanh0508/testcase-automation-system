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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine thực thi Test Case sử dụng RestTemplate (Spring HTTP Client Library).
 */
@Service
public class ApiExecutorService {

    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Pattern nhận diện các placeholder dạng {name} trong URL
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    @Value("${app.test.target.baseUrl:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.test.username:testuser}")
    private String testUsername;

    @Value("${app.test.password:Password@123}")
    private String testPassword;

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
        ExecutionContext ctx = new ExecutionContext();

        try {
            List<String> steps = tc.getSteps();

            log.append("╔══════════════════════════════════════════════════════════╗\n");
            log.append("║  TESTCASE: ").append(tc.getTcCode()).append(" — ").append(tc.getName()).append("\n");
            log.append("║  ENGINE  : RestTemplate HTTP Client (Library-based)\n");
            log.append("║  TARGET  : ").append(baseUrl).append("\n");
            log.append("╚══════════════════════════════════════════════════════════╝\n\n");

            log.append("  🔐  Đang tự động đăng nhập để lấy JWT token...\n");
            autoLogin(ctx, log);

            for (int i = 0; i < steps.size(); i++) {
                String step = steps.get(i).trim();
                if (step.isEmpty())
                    continue;

                if (step.startsWith("===") || step.startsWith("---")) {
                    log.append("\n").append(step).append("\n");
                    continue;
                }

                // --- SỬA TẠI ĐÂY: Xử lý biến động (Runtime Dynamic Data) trước khi Log và
                // Process ---
                step = injectRuntimeDynamicData(step, log);

                log.append("[Step ").append(i + 1).append("] ").append(step).append("\n");
                processStep(step, ctx, log);
            }

            String expected = tc.getExpectedResult();
            if (expected != null && !expected.isBlank() && ctx.lastResponseBody != null) {
                String keyword = extractKeyword(expected);
                if (!keyword.isBlank() && !ctx.lastResponseBody.toLowerCase().contains(keyword)) {
                    log.append("[WARN] Expected keyword «").append(keyword)
                            .append("» không tìm thấy trong response cuối.\n");
                }
            }

            int durationMs = (int) (System.currentTimeMillis() - startMs);
            log.append("\n✅ RESULT: PASSED (").append(durationMs).append("ms)\n");

            tc.setStatus(TestCaseStatus.PASSED);
            testCaseRepository.save(tc);
            return saveResult(tc, TestOutcome.PASSED, log.toString(), null, durationMs);

        } catch (AssertionError ae) {
            int durationMs = (int) (System.currentTimeMillis() - startMs);
            log.append("\n❌ RESULT: FAILED — ").append(ae.getMessage()).append("\n");
            tc.setStatus(TestCaseStatus.FAILED);
            testCaseRepository.save(tc);
            return saveResult(tc, TestOutcome.FAILED, log.toString(), ae.getMessage(), durationMs);

        } catch (Exception ex) {
            int durationMs = (int) (System.currentTimeMillis() - startMs);
            log.append("\n⚠️ RESULT: ERROR — ").append(ex.getMessage()).append("\n");
            tc.setStatus(TestCaseStatus.FAILED);
            testCaseRepository.save(tc);
            return saveResult(tc, TestOutcome.ERROR, log.toString(), ex.getMessage(), durationMs);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE: Bộ xử lý chuỗi ngẫu nhiên tại Runtime (Fix TC-329)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tự động quét và thay thế các placeholder động bằng dữ liệu thực tế khi thực
     * thi.
     * Hỗ trợ các định dạng: __RANDOM__, ${RANDOM}, __TIMESTAMP__
     */
    private String injectRuntimeDynamicData(String step, StringBuilder log) {
        String processedStep = step;

        // 1. Xử lý placeholder RANDOM (Chuỗi 8 ký tự ngẫu nhiên)
        if (processedStep.contains("__RANDOM__") || processedStep.contains("${RANDOM}")) {
            String runtimeRandom = UUID.randomUUID().toString().substring(0, 8);
            processedStep = processedStep.replace("__RANDOM__", runtimeRandom)
                    .replace("${RANDOM}", runtimeRandom);
        }

        // 2. Xử lý placeholder TIMESTAMP (Thời gian chạy thực tế bằng mili-giây)
        if (processedStep.contains("__TIMESTAMP__") || processedStep.contains("${TIMESTAMP}")) {
            String runtimeTimestamp = String.valueOf(System.currentTimeMillis());
            processedStep = processedStep.replace("__TIMESTAMP__", runtimeTimestamp)
                    .replace("${TIMESTAMP}", runtimeTimestamp);
        }

        return processedStep;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: batch execution
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
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
        }
        return results;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE: xử lý từng step
    // ─────────────────────────────────────────────────────────────────────────

    private void processStep(String step, ExecutionContext ctx, StringBuilder log) {
        String upper = step.toUpperCase().trim();

        if (upper.startsWith("HTTP ")) {
            handleHttpStep(step, ctx, log);
            return;
        }
        if (upper.startsWith("EXPECT_STATUS")) {
            handleExpectStatus(step, ctx, log);
            return;
        }
        if (upper.startsWith("EXPECT_BODY_CONTAINS")) {
            handleBodyContains(step, ctx, log);
            return;
        }
        if (upper.startsWith("EXPECT_BODY_FIELD")) {
            handleBodyField(step, ctx, log);
            return;
        }
        if (upper.startsWith("INPUT ")) {
            handleInput(step, ctx, log);
            return;
        }
        if (upper.startsWith("HEADER ")) {
            handleHeader(step, ctx, log);
            return;
        }
        if (upper.startsWith("SET_BODY ")) {
            handleSetBody(step, ctx, log);
            return;
        }

        // Mô tả thuần — log và bỏ qua
        log.append("  ℹ️  ").append(step).append("\n");
    }

    private void handleHttpStep(String step, ExecutionContext ctx, StringBuilder log) {
        String[] tokens = step.split("\\s+", 4);
        if (tokens.length < 3) {
            log.append("  ⚠️  HTTP step sai format: ").append(step).append("\n");
            return;
        }

        String method = tokens[1].toUpperCase();
        String rawPath = tokens[2];

        if (rawPath.toUpperCase().equals("{METHOD}") || method.startsWith("{")) {
            log.append("  ⚠️  HTTP method chưa được resolve: ").append(method)
                    .append(" — bỏ qua step này\n");
            return;
        }

        Set<String> validMethods = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
        if (!validMethods.contains(method)) {
            log.append("  ⚠️  HTTP method không hợp lệ: ").append(method)
                    .append(" — bỏ qua step này\n");
            return;
        }

        String path = resolvePlaceholders(rawPath, ctx.variables);

        // Lấy Body từ SET_BODY hoặc inline "body:"
        String body = ctx.pendingBody;
        if (step.toLowerCase().contains("body:")) {
            int idx = step.toLowerCase().indexOf("body:");
            body = step.substring(idx + 5).trim();
        }

        String safePath = escapePlaceholders(path);
        String url = baseUrl + (safePath.startsWith("/") ? safePath : "/" + safePath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));

        ctx.headers.forEach((key, values) -> {
            for (String value : values) {
                String resolved = resolvePlaceholders(value, ctx.variables);
                headers.set(key, resolved);
                log.append("  🔍  Header sau resolve: ").append(key)
                        .append(" = ").append(resolved).append("\n");
            }
        });

        String currentAuth = headers.getFirst("Authorization");
        if (currentAuth != null && currentAuth.contains("{token}")) {
            if (ctx.variables.containsKey("token")) {
                headers.set("Authorization", "Bearer " + ctx.variables.get("token"));
                log.append("  ✅  Đã resolve {token} trong Authorization header.\n");
            } else {
                headers.remove("Authorization");
                log.append("  ⚠️  Không có token — xóa Authorization header.\n");
            }
        }

        if (headers.getFirst("Authorization") == null && ctx.variables.containsKey("token")) {
            headers.set("Authorization", "Bearer " + ctx.variables.get("token"));
            log.append("  ℹ  Auto-inject Authorization từ token đã lưu.\n");
        }

        HttpEntity<String> entity = new HttpEntity<>(
                (body != null && !body.isEmpty()) ? body : null, headers);

        log.append("  ➤  ").append(method).append(" ").append(url).append("\n");
        if (body != null && !body.isEmpty()) {
            log.append("  ➤  body: ").append(truncate(body, 200)).append("\n");
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
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            ctx.lastStatusCode = ex.getStatusCode().value();
            ctx.lastResponseBody = ex.getResponseBodyAsString();
        }

        log.append("  ◀  status: ").append(ctx.lastStatusCode).append("\n");
        log.append("  ◀  body  : ").append(truncate(ctx.lastResponseBody, 300)).append("\n");
        ctx.pendingBody = null;
    }

    private String escapePlaceholders(String path) {
        Matcher m = PLACEHOLDER_PATTERN.matcher(path);
        if (!m.find())
            return path;

        m.reset();
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String placeholder = m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    placeholder.replace("{", "%7B").replace("}", "%7D")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void handleExpectStatus(String step, ExecutionContext ctx, StringBuilder log) {
        String rest = step.replaceAll("(?i)expect_status", "").trim();
        List<Integer> expected = new ArrayList<>();
        for (String t : rest.split("[^0-9]+")) {
            if (!t.isEmpty()) {
                try {
                    expected.add(Integer.parseInt(t));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (expected.isEmpty()) {
            log.append("  ⚠️  Không parse được expected status\n");
            return;
        }
        if (ctx.lastStatusCode == null) {
            log.append("  ⚠️  Chưa có HTTP request — bỏ qua\n");
            return;
        }

        if (!expected.contains(ctx.lastStatusCode)) {
            throw new AssertionError("HTTP status mismatch. Expected: " + expected
                    + ", Actual: " + ctx.lastStatusCode
                    + "\nBody: " + truncate(ctx.lastResponseBody, 400));
        }
        log.append("  ✅  status ").append(ctx.lastStatusCode).append(" ∈ ").append(expected).append("\n");
    }

    private void handleBodyContains(String step, ExecutionContext ctx, StringBuilder log) {
        String expected = step.substring(step.indexOf(' ') + 1).trim().replaceAll("\"", "");
        if (ctx.lastResponseBody == null || !ctx.lastResponseBody.toLowerCase().contains(expected.toLowerCase())) {
            throw new AssertionError("Body không chứa: «" + expected + "»\nBody: "
                    + truncate(ctx.lastResponseBody, 400));
        }
        log.append("  ✅  body contains «").append(expected).append("»\n");
    }

    private void handleBodyField(String step, ExecutionContext ctx, StringBuilder log) {
        String fieldPath = step.substring(step.indexOf(' ') + 1).trim();
        if (ctx.lastResponseBody == null) {
            log.append("  ⚠️  Không có body để kiểm tra field: ").append(fieldPath).append("\n");
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
                throw new AssertionError("Field «" + fieldPath + "» không tồn tại trong response.");
            }
            log.append("  ✅  field «").append(fieldPath).append("» = ")
                    .append(truncate(node.toString(), 100)).append("\n");
        } catch (AssertionError ae) {
            throw ae;
        } catch (Exception e) {
            log.append("  ⚠️  Không parse được JSON: ").append(e.getMessage()).append("\n");
        }
    }

    private void handleInput(String step, ExecutionContext ctx, StringBuilder log) {
        String payload = step.substring(6).trim();
        String[] parts = payload.split("=", 2);
        if (parts.length == 2) {
            String key = parts[0].trim();
            String val = parts[1].trim().replaceAll("\"", "");
            ctx.variables.put(key, val);
            log.append("  ➤  INPUT ").append(key).append(" = ").append(val).append("\n");
        }
    }

    private void handleHeader(String step, ExecutionContext ctx, StringBuilder log) {
        String h = step.substring(7).trim();
        String[] hParts = h.split(":", 2);
        if (hParts.length == 2) {
            String key = hParts[0].trim();
            String value = resolvePlaceholders(hParts[1].trim(), ctx.variables);
            ctx.headers.set(key, value);
            log.append("  ➤  HEADER ").append(key).append(" set\n");
        }
    }

    private void handleSetBody(String step, ExecutionContext ctx, StringBuilder log) {
        ctx.pendingBody = step.substring(9).trim();
        log.append("  ➤  SET_BODY ").append(truncate(ctx.pendingBody, 100)).append("\n");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String resolvePlaceholders(String path, Map<String, String> vars) {
        String resolved = path;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            resolved = resolved.replace("{" + e.getKey() + "}", e.getValue());
        }
        return resolved;
    }

    private String truncate(String s, int max) {
        if (s == null)
            return "(null)";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private String extractKeyword(String expected) {
        for (String kw : new String[] { "thành công", "success", "token", "data", "ok" }) {
            if (expected.toLowerCase().contains(kw))
                return kw;
        }
        String[] words = expected.trim().split("\\s+");
        return words.length > 0 ? words[words.length - 1].toLowerCase().replaceAll("[.,!?]", "") : "";
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

    private void autoLogin(ExecutionContext ctx, StringBuilder log) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);

            String body = String.format(
                    "{\"username\":\"%s\",\"password\":\"%s\"}",
                    testUsername, testPassword);

            ResponseEntity<String> res = restTemplate.exchange(
                    baseUrl + "/api/auth/login",
                    HttpMethod.POST,
                    new HttpEntity<>(body, h),
                    String.class);

            if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                JsonNode tokenNode = objectMapper.readTree(res.getBody())
                        .path("data")
                        .path("token");
                if (!tokenNode.isMissingNode() && !tokenNode.isNull()) {
                    ctx.variables.put("token", tokenNode.asText());
                    log.append("  ✅  Auto-login thành công — token đã được lưu.\n");
                    return;
                }
            }
            log.append("  ⚠️  Auto-login thất bại: không tìm thấy token trong response.\n");

        } catch (Exception e) {
            log.append("  ⚠️  Auto-login lỗi: ").append(e.getMessage()).append("\n");
        }
    }

    private static class ExecutionContext {
        Map<String, String> variables = new HashMap<>();
        HttpHeaders headers = new HttpHeaders();
        Integer lastStatusCode = null;
        String lastResponseBody = null;
        String pendingBody = null;
    }
}