package com.testcase.backend.controller;

import com.testcase.backend.dto.ApiResponseDTO;
import com.testcase.backend.dto.AuthResponseDTO;
import com.testcase.backend.dto.LoginRequestDTO;
import com.testcase.backend.dto.RegisterRequestDTO;
import com.testcase.backend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponseDTO<AuthResponseDTO>> register(
            @RequestBody RegisterRequestDTO dto) {
        try {
            AuthResponseDTO data = authService.register(dto);
            return ResponseEntity.ok(ApiResponseDTO.ok("Đăng ký thành công", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseDTO.error(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<AuthResponseDTO>> login(
            @RequestBody LoginRequestDTO dto) {
        try {
            AuthResponseDTO data = authService.login(dto);
            return ResponseEntity.ok(ApiResponseDTO.ok("Đăng nhập thành công", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseDTO.error(e.getMessage()));
        }
    }
}