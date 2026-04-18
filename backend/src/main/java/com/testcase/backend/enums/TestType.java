package com.testcase.backend.enums;

public enum TestType {
    HAPPY_PATH, // Luồng chính, đúng như mong đợi
    NEGATIVE, // Luồng sai, nhập sai dữ liệu
    BOUNDARY // Kiểm tra giới hạn (min/max)
}