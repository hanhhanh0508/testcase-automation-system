package com.testcase.backend.dto;

public class AuthResponseDTO {
    private String token;
    private String username;
    private String email;

    public AuthResponseDTO(String token, String username, String email) {
        this.token = token;
        this.username = username;
        this.email = email;
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
}