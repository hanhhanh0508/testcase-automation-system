package com.testcase.backend.dto;

// ── Request DTOs ───────────────────────────────────────────────

public class AuthDTOs {

    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String v) {
            this.username = v;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String v) {
            this.email = v;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String v) {
            this.password = v;
        }
    }

    public static class LoginRequest {
        private String username; // username hoặc email
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String v) {
            this.username = v;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String v) {
            this.password = v;
        }
    }

    public static class AuthResponse {
        private String token;
        private String username;
        private String email;
        private String role;

        public AuthResponse(String token, String username, String email, String role) {
            this.token = token;
            this.username = username;
            this.email = email;
            this.role = role;
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }
    }
}