package com.testcase.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.testcase.backend.enums.TestCaseStatus;
import com.testcase.backend.enums.TestType;

@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "use_case_id", nullable = false)
    private UseCase useCase;

    // Mã hiển thị dễ đọc, ví dụ "TC-001", "TC-002"
    @Column(nullable = false, unique = true)
    private String tcCode;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestType testType; // HAPPY_PATH, NEGATIVE, BOUNDARY

    // Danh sách các bước thực hiện
    // ví dụ: ["Mở trang login", "Nhập email hợp lệ", "Nhấn Submit"]
    @ElementCollection
    @CollectionTable(name = "test_case_steps", joinColumns = @JoinColumn(name = "test_case_id"))
    @OrderColumn(name = "step_order") // giữ đúng thứ tự
    @Column(name = "step", columnDefinition = "TEXT")
    private List<String> steps = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String expectedResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestCaseStatus status = TestCaseStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Quan hệ 1:N với TestResult
    @OneToMany(mappedBy = "testCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestResult> testResults = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────
    public TestCase() {
    }

    public TestCase(String tcCode, String name, TestType testType) {
        this.tcCode = tcCode;
        this.name = name;
        this.testType = testType;
    }

    // ── Getters & Setters ─────────────────────────────────────
    public UUID getId() {
        return id;
    }

    public UseCase getUseCase() {
        return useCase;
    }

    public void setUseCase(UseCase useCase) {
        this.useCase = useCase;
    }

    public String getTcCode() {
        return tcCode;
    }

    public void setTcCode(String tcCode) {
        this.tcCode = tcCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TestType getTestType() {
        return testType;
    }

    public void setTestType(TestType testType) {
        this.testType = testType;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }

    public TestCaseStatus getStatus() {
        return status;
    }

    public void setStatus(TestCaseStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<TestResult> getTestResults() {
        return testResults;
    }
}
