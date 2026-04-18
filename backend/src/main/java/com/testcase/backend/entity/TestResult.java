package com.testcase.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

import com.testcase.backend.enums.TestOutcome;

@Entity
@Table(name = "test_results")
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestOutcome outcome; // PASSED, FAILED, SKIPPED, ERROR

    @Column(columnDefinition = "TEXT")
    private String actualResult; // Kết quả thực tế nhận được

    @Column(columnDefinition = "TEXT")
    private String errorMessage; // Thông báo lỗi nếu fail

    private Integer durationMs; // Thời gian chạy (milliseconds)

    @Column(nullable = false)
    private LocalDateTime executedAt;

    @PrePersist
    protected void onCreate() {
        this.executedAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────
    public TestResult() {
    }

    public TestResult(TestCase testCase, TestOutcome outcome) {
        this.testCase = testCase;
        this.outcome = outcome;
    }

    // ── Getters & Setters ─────────────────────────────────────
    public UUID getId() {
        return id;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public void setTestCase(TestCase testCase) {
        this.testCase = testCase;
    }

    public TestOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(TestOutcome outcome) {
        this.outcome = outcome;
    }

    public String getActualResult() {
        return actualResult;
    }

    public void setActualResult(String actualResult) {
        this.actualResult = actualResult;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }
}