package com.testcase.backend.enums;

public enum RelationType {
    ASSOCIATION, // Đường nối actor - use case
    INCLUDE, // Luôn gọi use case khác
    EXTEND, // Mở rộng có điều kiện
    GENERALIZATION // Kế thừa
}