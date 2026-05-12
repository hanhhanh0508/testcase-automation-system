package com.testcase.backend.controller;

import com.testcase.backend.dto.ApiResponseDTO;
import com.testcase.backend.service.SeleniumRunnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/selenium")
public class SeleniumController {

    private final SeleniumRunnerService seleniumRunnerService;

    public SeleniumController(SeleniumRunnerService seleniumRunnerService) {
        this.seleniumRunnerService = seleniumRunnerService;
    }

    /**
     * POST /api/selenium/run
     * Body: { "diagramId": "uuid", "ids": ["uuid1", "uuid2"] }
     */
    @PostMapping("/run")
    public ResponseEntity<ApiResponseDTO<String>> triggerRun(
            @RequestBody Map<String, Object> body) {
        try {
            String diagramId = (String) body.get("diagramId");
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) body.get("ids");

            // Chạy async để không block HTTP response
            new Thread(() -> seleniumRunnerService.runAndPrompt(diagramId, ids)).start();

            return ResponseEntity.ok(
                    ApiResponseDTO.ok("Selenium đã khởi động!", "Đang mở browser..."));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseDTO.error("Lỗi: " + e.getMessage()));
        }
    }
}