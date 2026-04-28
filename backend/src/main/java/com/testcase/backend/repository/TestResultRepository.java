package com.testcase.backend.repository;

import com.testcase.backend.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

    // Lấy tất cả kết quả của 1 test case, mới nhất lên trước
    List<TestResult> findByTestCaseIdOrderByExecutedAtDesc(UUID testCaseId);

    // Lấy kết quả mới nhất của 1 test case
    java.util.Optional<TestResult> findTopByTestCaseIdOrderByExecutedAtDesc(UUID testCaseId);

    // Lấy tất cả kết quả của nhiều test case (cho batch summary)
    List<TestResult> findByTestCaseIdIn(List<UUID> testCaseIds);
}