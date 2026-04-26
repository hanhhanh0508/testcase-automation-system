package com.testcase.backend.service;

import com.testcase.backend.dto.AuthResponseDTO;
import com.testcase.backend.dto.LoginRequestDTO;
import com.testcase.backend.dto.RegisterRequestDTO;
import com.testcase.backend.entity.User;
import com.testcase.backend.repository.UserRepository;
import com.testcase.backend.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponseDTO register(RegisterRequestDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("Username đã tồn tại");
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email đã tồn tại");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername());
        return new AuthResponseDTO(token, user.getUsername(), user.getEmail());
    }

    public AuthResponseDTO login(LoginRequestDTO dto) {
        // Tìm theo username hoặc email
        var user = userRepository.findByUsername(dto.getUsername())
                .or(() -> userRepository.findByEmail(dto.getUsername()))
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Mật khẩu không đúng");
        }

        String token = jwtUtil.generateToken(user.getUsername());
        return new AuthResponseDTO(token, user.getUsername(), user.getEmail());
    }
}