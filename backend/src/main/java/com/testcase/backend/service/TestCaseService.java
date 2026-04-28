package com.testcase.backend.service;

import com.testcase.backend.entity.TestCase;
import com.testcase.backend.enums.TestCaseStatus;
import com.testcase.backend.repository.TestCaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;

    public TestCaseService(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    // Lấy test case theo ID
    public TestCase getById(UUID id) {
        return testCaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("TestCase không tồn tại: " + id));
    }

    // Lấy nhiều test case theo danh sách ID
    public List<TestCase> getByIds(List<UUID> ids) {
        return testCaseRepository.findAllById(ids);
    }

    // Cập nhật trạng thái test case (PENDING / RUNNING / PASSED / FAILED)
    public TestCase updateStatus(UUID id, String status) {
        TestCase tc = getById(id);
        try {
            TestCaseStatus newStatus = TestCaseStatus.valueOf(status.toUpperCase());
            tc.setStatus(newStatus);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Trạng thái không hợp lệ: " + status
                    + ". Các giá trị hợp lệ: PENDING, RUNNING, PASSED, FAILED");
        }
        return testCaseRepository.save(tc);
    }

    // Reset tất cả test case của một diagram về PENDING
    public List<TestCase> resetDiagramTestCases(UUID diagramId) {
        List<TestCase> list = testCaseRepository.findByUseCase_DiagramId(diagramId);
        list.forEach(tc -> tc.setStatus(TestCaseStatus.PENDING));
        return testCaseRepository.saveAll(list);
    }
}
