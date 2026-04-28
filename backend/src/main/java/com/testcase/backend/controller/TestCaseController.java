package com.testcase.backend.controller;

import com.testcase.backend.dto.ApiResponseDTO;
import com.testcase.backend.entity.TestCase;
import com.testcase.backend.service.TestCaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/testcases")
public class TestCaseController {

    private final TestCaseService testCaseService;

    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    // GET /api/testcases/{id} — lấy 1 test case theo UUID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<TestCase>> getById(@PathVariable UUID id) {
        try {
            TestCase tc = testCaseService.getById(id);
            return ResponseEntity.ok(ApiResponseDTO.ok("OK", tc));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // PUT /api/testcases/{id}/status — cập nhật trạng thái (PASSED / FAILED /
    // PENDING / RUNNING)
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponseDTO<TestCase>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        try {
            String status = body.get("status");
            if (status == null || status.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponseDTO.error("Thiếu trường 'status'"));
            }
            TestCase tc = testCaseService.updateStatus(id, status);
            return ResponseEntity.ok(ApiResponseDTO.ok("Cập nhật trạng thái thành công", tc));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseDTO.error(e.getMessage()));
        }
    }

    // POST /api/testcases/batch — lấy nhiều test case cùng lúc theo danh sách UUID
    @PostMapping("/batch")
    public ResponseEntity<ApiResponseDTO<List<TestCase>>> getByIds(
            @RequestBody Map<String, List<String>> body) {
        try {
            List<UUID> ids = body.get("ids").stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
            List<TestCase> list = testCaseService.getByIds(ids);
            return ResponseEntity.ok(ApiResponseDTO.ok("OK", list));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseDTO.error(e.getMessage()));
        }
    }

    // PUT /api/testcases/diagram/{diagramId}/reset — reset tất cả về PENDING
    @PutMapping("/diagram/{diagramId}/reset")
    public ResponseEntity<ApiResponseDTO<List<TestCase>>> resetDiagram(
            @PathVariable UUID diagramId) {
        try {
            List<TestCase> list = testCaseService.resetDiagramTestCases(diagramId);
            return ResponseEntity.ok(ApiResponseDTO.ok("Reset " + list.size() + " test case thành công", list));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseDTO.error(e.getMessage()));
        }
    }
}