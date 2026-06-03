package com.testcase.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MockBorrowController {

    @PostMapping("/borrows")
    public ResponseEntity<?> mockCreateBorrow(@RequestBody(required = false) String body) {
        // Nếu kịch bản test gửi body rỗng hoặc chuỗi rỗng "{}" -> Trả về 400 đúng kỳ
        // vọng của test case lỗi
        if (body == null || body.trim().isEmpty() || body.trim().equals("{}") || body.contains("invalid")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Dữ liệu không hợp lệ (Mock Validation)"));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Mock thành công", "data", body));
    }

    @GetMapping({ "/borrows", "/borrow-historys", "/borrow-history" })
    public ResponseEntity<?> mockGetHistory() {
        return ResponseEntity.ok(Map.of("success", true, "message", "Mock history thành công"));
    }
}