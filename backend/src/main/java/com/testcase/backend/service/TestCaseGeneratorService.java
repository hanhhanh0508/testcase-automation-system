package com.testcase.backend.service;

import com.testcase.backend.entity.TestCase;
import com.testcase.backend.entity.UseCase;
import com.testcase.backend.entity.UseCaseDiagram;
import com.testcase.backend.enums.TestCaseStatus;
import com.testcase.backend.enums.TestType;
import com.testcase.backend.enums.UseCaseStatus;
import com.testcase.backend.repository.TestCaseRepository;
import com.testcase.backend.repository.UseCaseDiagramRepository;
import com.testcase.backend.repository.UseCaseRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TestCaseGeneratorService {

    private final UseCaseDiagramRepository diagramRepository;
    private final UseCaseRepository useCaseRepository;
    private final TestCaseRepository testCaseRepository;

    public TestCaseGeneratorService(UseCaseDiagramRepository diagramRepository,
                                    UseCaseRepository useCaseRepository,
                                    TestCaseRepository testCaseRepository) {
        this.diagramRepository = diagramRepository;
        this.useCaseRepository = useCaseRepository;
        this.testCaseRepository = testCaseRepository;
    }

    public List<TestCase> generateForDiagram(UUID diagramId) {
        UseCaseDiagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new RuntimeException("Diagram not found: " + diagramId));

        List<UseCase> useCases = useCaseRepository.findByDiagramId(diagramId);
        if (useCases.isEmpty()) {
            throw new RuntimeException("Diagram chưa có use case nào");
        }

        // Đếm số TC hiện có để tạo mã TC-001, TC-002...
        long existingCount = testCaseRepository.count();
        AtomicInteger counter = new AtomicInteger((int) existingCount + 1);

        List<TestCase> generated = new ArrayList<>();

        for (UseCase uc : useCases) {
            // Bỏ qua nếu đã sinh rồi
            if (uc.getStatus() == UseCaseStatus.GENERATED) continue;

            // 1. Happy path
            TestCase happy = new TestCase();
            happy.setUseCase(uc);
            happy.setTcCode(String.format("TC-%03d", counter.getAndIncrement()));
            happy.setName(uc.getName() + " — Happy path");
            happy.setTestType(TestType.HAPPY_PATH);
            happy.setStatus(TestCaseStatus.PENDING);
            happy.setExpectedResult("Hệ thống thực hiện thành công " + uc.getName());
            happy.setSteps(generateHappySteps(uc));
            generated.add(happy);

            // 2. Negative
            TestCase negative = new TestCase();
            negative.setUseCase(uc);
            negative.setTcCode(String.format("TC-%03d", counter.getAndIncrement()));
            negative.setName(uc.getName() + " — Dữ liệu không hợp lệ");
            negative.setTestType(TestType.NEGATIVE);
            negative.setStatus(TestCaseStatus.PENDING);
            negative.setExpectedResult("Hệ thống hiển thị thông báo lỗi phù hợp");
            negative.setSteps(generateNegativeSteps(uc));
            generated.add(negative);

            // 3. Boundary
            TestCase boundary = new TestCase();
            boundary.setUseCase(uc);
            boundary.setTcCode(String.format("TC-%03d", counter.getAndIncrement()));
            boundary.setName(uc.getName() + " — Giá trị biên");
            boundary.setTestType(TestType.BOUNDARY);
            boundary.setStatus(TestCaseStatus.PENDING);
            boundary.setExpectedResult("Hệ thống xử lý đúng các giá trị biên");
            boundary.setSteps(generateBoundarySteps(uc));
            generated.add(boundary);

            // Cập nhật trạng thái use case
            uc.setStatus(UseCaseStatus.GENERATED);
            useCaseRepository.save(uc);
        }

        List<TestCase> saved = testCaseRepository.saveAll(generated);

        // Cập nhật trạng thái diagram
        diagram.setStatus(com.testcase.backend.enums.DiagramStatus.PARSED);
        diagramRepository.save(diagram);

        return saved;
    }

    public List<TestCase> getTestCasesForDiagram(UUID diagramId) {
        return testCaseRepository.findByUseCase_DiagramId(diagramId);
    }

    // ── Rule generators ───────────────────────────────────────

    private List<String> generateHappySteps(UseCase uc) {
        List<String> steps = new ArrayList<>();
        steps.add("Chuẩn bị dữ liệu hợp lệ cho use case: " + uc.getName());

        if (!uc.getPreconditions().isEmpty()) {
            steps.add("Kiểm tra điều kiện tiên quyết: " + String.join(", ", uc.getPreconditions()));
        } else {
            steps.add("Đảm bảo hệ thống đang hoạt động bình thường");
        }

        steps.add("Thực hiện hành động chính của use case: " + uc.getName());
        steps.add("Xác nhận hệ thống phản hồi đúng");

        if (!uc.getPostconditions().isEmpty()) {
            steps.add("Kiểm tra postcondition: " + String.join(", ", uc.getPostconditions()));
        } else {
            steps.add("Kiểm tra kết quả đầu ra khớp với expected result");
        }

        return steps;
    }

    private List<String> generateNegativeSteps(UseCase uc) {
        List<String> steps = new ArrayList<>();
        steps.add("Chuẩn bị dữ liệu KHÔNG hợp lệ cho use case: " + uc.getName());
        steps.add("Bỏ trống các trường bắt buộc hoặc nhập sai định dạng");
        steps.add("Thực hiện hành động của use case: " + uc.getName());
        steps.add("Xác nhận hệ thống hiển thị thông báo lỗi rõ ràng");
        steps.add("Xác nhận hệ thống KHÔNG thực hiện hành động không mong muốn");
        return steps;
    }

    private List<String> generateBoundarySteps(UseCase uc) {
        List<String> steps = new ArrayList<>();
        steps.add("Xác định các giá trị biên liên quan đến use case: " + uc.getName());
        steps.add("Test với giá trị tối thiểu (min value)");
        steps.add("Test với giá trị tối đa (max value)");
        steps.add("Test với giá trị vừa dưới ngưỡng (min - 1)");
        steps.add("Test với giá trị vừa trên ngưỡng (max + 1)");
        steps.add("Xác nhận hệ thống xử lý đúng từng trường hợp biên");
        return steps;
    }
}