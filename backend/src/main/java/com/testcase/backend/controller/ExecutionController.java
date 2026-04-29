package com.testcase.backend.controller;

import com.testcase.backend.dto.ApiResponseDTO;
import com.testcase.backend.entity.TestResult;
import com.testcase.backend.service.ApiExecutorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Thực thi Test Case qua HTTP (RestTemplate-based, không dùng Selenium).
 *
 * POST /api/execute/{id} → chạy 1 test case
 * POST /api/execute/batch → chạy nhiều test case (body: {"ids":
 * ["uuid1","uuid2"]})
 */
@RestController
@RequestMapping("/api/execute")
public class ExecutionController {

    private final ApiExecutorService executorService;

    public ExecutionController(ApiExecutorService executorService) {
        this.executorService = executorService;
    }

    /** Chạy 1 test case, trả về TestResult */
    @PostMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<TestResult>> executeOne(@PathVariable UUID id) {
        try {
            TestResult result = executorService.execute(id);
            String msg = "PASSED".equals(result.getOutcome().name())
                    ? "Test case PASSED ✓"
                    : "Test case " + result.getOutcome().name();
            return ResponseEntity.ok(ApiResponseDTO.ok(msg, result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseDTO.error("Lỗi thực thi: " + e.getMessage()));
        }
    }

    /**
     * Chạy nhiều test case tuần tự.
     * Body: { "ids": ["uuid1", "uuid2", ...] }
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> executeBatch(
            @RequestBody Map<String, List<String>> body) {
        try {
            List<String> rawIds = body.get("ids");
            if (rawIds == null || rawIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponseDTO.error("Thiếu danh sách 'ids'"));
            }

            List<UUID> ids = rawIds.stream().map(UUID::fromString).toList();
            Map<UUID, TestResult> resultMap = executorService.executeBatch(ids);

            long passed = resultMap.values().stream().filter(r -> "PASSED".equals(r.getOutcome().name())).count();
            long failed = resultMap.values().stream().filter(r -> !"PASSED".equals(r.getOutcome().name())).count();
            int totalMs = resultMap.values().stream()
                    .mapToInt(r -> r.getDurationMs() != null ? r.getDurationMs() : 0).sum();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("total", ids.size());
            response.put("passed", passed);
            response.put("failed", failed);
            response.put("totalMs", totalMs);
            response.put("results", resultMap);

            return ResponseEntity.ok(ApiResponseDTO.ok(
                    "Hoàn thành " + ids.size() + " test case: " + passed + " PASS, " + failed + " FAIL",
                    response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseDTO.error("Lỗi batch execute: " + e.getMessage()));
        }
    }
}