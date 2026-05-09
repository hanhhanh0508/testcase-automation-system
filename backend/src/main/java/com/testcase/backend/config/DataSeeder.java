package com.testcase.backend.config;

import com.testcase.backend.entity.User;
import com.testcase.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedTestUser(UserRepository userRepo, PasswordEncoder encoder) {
        return args -> {
            if (!userRepo.existsByUsername("testuser")) {
                User u = new User();
                u.setUsername("testuser");
                u.setEmail("testuser@example.com");
                u.setPasswordHash(encoder.encode("Password@123"));
                userRepo.save(u);
                System.out.println("✅ Đã tạo tài khoản test: testuser / Password@123");
            }
        };
    }
}
