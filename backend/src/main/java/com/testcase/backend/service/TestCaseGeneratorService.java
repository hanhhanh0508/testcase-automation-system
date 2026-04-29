package com.testcase.backend.service;

import com.testcase.backend.entity.Relationship;
import com.testcase.backend.entity.TestCase;
import com.testcase.backend.entity.UseCase;
import com.testcase.backend.entity.UseCaseDiagram;
import com.testcase.backend.enums.*;
import com.testcase.backend.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Bộ sinh Test Case từ Use Case Diagram.
 *
 * Thuật toán:
 * 1. Đọc danh sách UseCase và Relationship của diagram.
 * 2. Xây dựng đồ thị quan hệ (include / extend / association).
 * 3. Với mỗi Use Case:
 * - Happy path: chèn thêm bước của các UseCase được «include».
 * - Negative : sinh kịch bản lỗi với dữ liệu không hợp lệ.
 * - Boundary : sinh kịch bản giá trị biên.
 * 4. Với mỗi quan hệ «extend» → sinh thêm một kịch bản extension.
 * 5. Lưu tất cả TestCase vào DB, cập nhật trạng thái UseCase và Diagram.
 */
@Service
public class TestCaseGeneratorService {

    private final UseCaseDiagramRepository diagramRepository;
    private final UseCaseRepository useCaseRepository;
    private final RelationshipRepository relationshipRepository;
    private final TestCaseRepository testCaseRepository;

    public TestCaseGeneratorService(UseCaseDiagramRepository diagramRepository,
            UseCaseRepository useCaseRepository,
            RelationshipRepository relationshipRepository,
            TestCaseRepository testCaseRepository) {
        this.diagramRepository = diagramRepository;
        this.useCaseRepository = useCaseRepository;
        this.relationshipRepository = relationshipRepository;
        this.testCaseRepository = testCaseRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    public List<TestCase> generateForDiagram(UUID diagramId) {
        UseCaseDiagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new RuntimeException("Diagram not found: " + diagramId));

        List<UseCase> useCases = useCaseRepository.findByDiagramId(diagramId);
        List<Relationship> relationships = relationshipRepository.findByDiagramId(diagramId);

        if (useCases.isEmpty()) {
            throw new RuntimeException("Diagram chưa có use case nào. Hãy nhập dữ liệu use case trước.");
        }

        // ── Xây dựng bảng lookup: xmiId → UseCase ──
        Map<String, UseCase> ucByXmiId = useCases.stream()
                .collect(Collectors.toMap(UseCase::getXmiId, uc -> uc, (a, b) -> a));

        // ── Phân loại quan hệ ──
        Map<String, List<String>> includeMap = buildRelationMap(relationships, RelationType.INCLUDE);
        Map<String, List<String>> extendMap = buildRelationMap(relationships, RelationType.EXTEND);

        // ── Bộ đếm mã TC toàn cục ──
        long existingCount = testCaseRepository.count();
        AtomicInteger counter = new AtomicInteger((int) existingCount + 1);

        List<TestCase> generated = new ArrayList<>();

        for (UseCase uc : useCases) {
            if (uc.getStatus() == UseCaseStatus.GENERATED)
                continue;

            // ── Lấy danh sách include (các UC được gọi bắt buộc) ──
            List<UseCase> includedUCs = resolveUCs(includeMap.getOrDefault(uc.getXmiId(), List.of()), ucByXmiId);

            // 1. Happy Path (bao gồm include steps)
            generated.add(buildHappyPath(uc, includedUCs, counter));

            // 2. Negative Case
            generated.add(buildNegative(uc, counter));

            // 3. Boundary Case
            generated.add(buildBoundary(uc, counter));

            // 4. Extension Cases — mỗi UC mở rộng (extend) → thêm 1 TC riêng
            for (String extXmiId : extendMap.getOrDefault(uc.getXmiId(), List.of())) {
                UseCase extUC = ucByXmiId.get(extXmiId);
                if (extUC != null) {
                    generated.add(buildExtensionCase(uc, extUC, counter));
                }
            }

            uc.setStatus(UseCaseStatus.GENERATED);
            useCaseRepository.save(uc);
        }

        List<TestCase> saved = testCaseRepository.saveAll(generated);

        diagram.setStatus(DiagramStatus.PARSED);
        diagramRepository.save(diagram);

        return saved;
    }

    public List<TestCase> getTestCasesForDiagram(UUID diagramId) {
        return testCaseRepository.findByUseCase_DiagramId(diagramId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUILDERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Happy Path: luồng thành công chính, tích hợp include. */
    private TestCase buildHappyPath(UseCase uc, List<UseCase> includedUCs, AtomicInteger counter) {
        TestCase tc = newTc(uc, counter, TestType.HAPPY_PATH,
                uc.getName() + " — Happy Path",
                "Hệ thống xử lý thành công và trả về kết quả đúng kỳ vọng");

        List<String> steps = new ArrayList<>();
        steps.add("=== CHUẨN BỊ ===");
        steps.add("Chuẩn bị dữ liệu hợp lệ cho: " + uc.getName());

        if (!uc.getPreconditions().isEmpty()) {
            steps.add("Kiểm tra tiên quyết: " + String.join("; ", uc.getPreconditions()));
        }

        // Các bước HTTP cho use case chính
        steps.addAll(buildApiSteps(uc, "valid"));

        // Chèn bước từ include UCs
        for (UseCase inc : includedUCs) {
            steps.add("--- [INCLUDE] " + inc.getName() + " ---");
            steps.addAll(buildApiSteps(inc, "valid"));
        }

        steps.add("=== XÁC NHẬN ===");
        steps.add("Kiểm tra HTTP status = 200 hoặc 201");
        steps.add("Kiểm tra response body chứa dữ liệu hợp lệ");

        if (!uc.getPostconditions().isEmpty()) {
            steps.add("Kiểm tra postcondition: " + String.join("; ", uc.getPostconditions()));
        }

        tc.setSteps(steps);
        return tc;
    }

    /** Negative Case: dữ liệu sai, thiếu, không hợp lệ. */
    private TestCase buildNegative(UseCase uc, AtomicInteger counter) {
        TestCase tc = newTc(uc, counter, TestType.NEGATIVE,
                uc.getName() + " — Dữ liệu không hợp lệ",
                "Hệ thống hiển thị thông báo lỗi rõ ràng và không thực hiện hành động");

        List<String> steps = new ArrayList<>();
        steps.add("=== CHUẨN BỊ DỮ LIỆU LỖI ===");
        steps.add("Chuẩn bị dữ liệu KHÔNG hợp lệ cho: " + uc.getName());

        // Các bước HTTP với dữ liệu lỗi
        steps.addAll(buildApiSteps(uc, "invalid"));

        steps.add("=== XÁC NHẬN LỖI ===");
        steps.add("Kiểm tra HTTP status = 400 hoặc 401 hoặc 422");
        steps.add("Kiểm tra response body chứa thông báo lỗi (message/error field)");
        steps.add("Xác nhận hệ thống KHÔNG thực hiện hành động không mong muốn");
        steps.add("Xác nhận dữ liệu trong DB không bị thay đổi sai");

        tc.setSteps(steps);
        return tc;
    }

    /** Boundary Case: giá trị biên min/max. */
    private TestCase buildBoundary(UseCase uc, AtomicInteger counter) {
        TestCase tc = newTc(uc, counter, TestType.BOUNDARY,
                uc.getName() + " — Giá trị biên",
                "Hệ thống xử lý đúng tất cả các trường hợp tại biên giới min/max");

        List<String> steps = new ArrayList<>();
        steps.add("=== XÁC ĐỊNH GIÁ TRỊ BIÊN ===");
        steps.add("Xác định các trường dữ liệu có ràng buộc trong: " + uc.getName());

        steps.add("=== TEST MIN BOUNDARY ===");
        steps.addAll(buildBoundaryApiSteps(uc, "min"));
        steps.add("Kiểm tra HTTP status và response khi input = min value");

        steps.add("=== TEST MAX BOUNDARY ===");
        steps.addAll(buildBoundaryApiSteps(uc, "max"));
        steps.add("Kiểm tra HTTP status và response khi input = max value");

        steps.add("=== TEST DƯỚI MIN ===");
        steps.add("Gọi API với input = (min - 1) / chuỗi rỗng / null");
        steps.add("Kiểm tra HTTP status = 400 — hệ thống phải reject");

        steps.add("=== TEST TRÊN MAX ===");
        steps.add("Gọi API với input vượt quá max (chuỗi quá dài, số quá lớn)");
        steps.add("Kiểm tra HTTP status = 400 — hệ thống phải reject");

        tc.setSteps(steps);
        return tc;
    }

    /** Extension Case: kịch bản rẽ nhánh từ quan hệ «extend». */
    private TestCase buildExtensionCase(UseCase baseUC, UseCase extUC, AtomicInteger counter) {
        TestCase tc = newTc(baseUC, counter, TestType.HAPPY_PATH,
                baseUC.getName() + " — [EXTEND] " + extUC.getName(),
                "Kịch bản mở rộng khi điều kiện kích hoạt extension point được thỏa mãn");

        List<String> steps = new ArrayList<>();
        steps.add("=== KỊCH BẢN MỞ RỘNG ===");
        steps.add("Base Use Case: " + baseUC.getName());
        steps.add("Extension: " + extUC.getName());
        steps.add("Chuẩn bị điều kiện để kích hoạt extension point");

        // Bước base
        steps.addAll(buildApiSteps(baseUC, "valid"));

        // Điểm rẽ nhánh
        steps.add("--- [EXTENSION POINT] Điều kiện kích hoạt " + extUC.getName() + " ---");
        steps.addAll(buildApiSteps(extUC, "valid"));

        steps.add("=== XÁC NHẬN ===");
        steps.add("Kiểm tra luồng extension đã được thực thi đúng");
        steps.add("Kiểm tra kết quả cuối cùng nhất quán với cả base và extension");

        tc.setSteps(steps);
        return tc;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SINH BƯỚC HTTP (API-based testing với RestTemplate)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sinh các bước API dựa trên tên Use Case.
     * Nhận dạng pattern phổ biến: login, register, logout, create, update, delete,
     * view, search.
     *
     * @param dataMode "valid" | "invalid" | "min" | "max"
     */
    private List<String> buildApiSteps(UseCase uc, String dataMode) {
        String name = uc.getName().toLowerCase();
        List<String> steps = new ArrayList<>();

        if (name.contains("login") || name.contains("đăng nhập") || name.contains("signin")) {
            return buildLoginSteps(dataMode);
        } else if (name.contains("register") || name.contains("đăng ký") || name.contains("signup")) {
            return buildRegisterSteps(dataMode);
        } else if (name.contains("logout") || name.contains("đăng xuất")) {
            return buildLogoutSteps(dataMode);
        } else if (name.contains("create") || name.contains("tạo") || name.contains("thêm") || name.contains("add")) {
            return buildCreateSteps(uc.getName(), dataMode);
        } else if (name.contains("update") || name.contains("edit") || name.contains("sửa")
                || name.contains("cập nhật")) {
            return buildUpdateSteps(uc.getName(), dataMode);
        } else if (name.contains("delete") || name.contains("xóa") || name.contains("remove")) {
            return buildDeleteSteps(uc.getName(), dataMode);
        } else if (name.contains("view") || name.contains("xem") || name.contains("list")
                || name.contains("danh sách")) {
            return buildViewSteps(uc.getName(), dataMode);
        } else if (name.contains("search") || name.contains("tìm")) {
            return buildSearchSteps(uc.getName(), dataMode);
        } else {
            // Generic API steps
            return buildGenericSteps(uc.getName(), dataMode);
        }
    }

    private List<String> buildLoginSteps(String dataMode) {
        List<String> steps = new ArrayList<>();
        if ("valid".equals(dataMode)) {
            steps.add("INPUT username = \"testuser\" (tài khoản tồn tại trong hệ thống)");
            steps.add("INPUT password = \"Password@123\" (mật khẩu đúng)");
            steps.add("HTTP POST /api/auth/login  body: {\"username\":\"testuser\",\"password\":\"Password@123\"}");
            steps.add("EXPECT_STATUS 200");
            steps.add("EXPECT_BODY_FIELD data.token (JWT token phải có mặt)");
        } else if ("invalid".equals(dataMode)) {
            steps.add("INPUT username = \"testuser\"");
            steps.add("INPUT password = \"SaiMatKhau\" (mật khẩu sai)");
            steps.add("HTTP POST /api/auth/login  body: {\"username\":\"testuser\",\"password\":\"SaiMatKhau\"}");
            steps.add("EXPECT_STATUS 400");
            steps.add("EXPECT_BODY_FIELD message chứa thông báo lỗi");
        }
        return steps;
    }

    private List<String> buildRegisterSteps(String dataMode) {
        List<String> steps = new ArrayList<>();
        if ("valid".equals(dataMode)) {
            steps.add("INPUT username = \"newuser_\" + timestamp (unique)");
            steps.add("INPUT email = \"newuser@test.com\"");
            steps.add("INPUT password = \"StrongPass@123\"");
            steps.add("HTTP POST /api/auth/register  body: {username, email, password}");
            steps.add("EXPECT_STATUS 200");
            steps.add("EXPECT_BODY_FIELD data.token");
        } else {
            steps.add("INPUT username = \"\" (bỏ trống)");
            steps.add("INPUT email = \"not-an-email\" (email sai định dạng)");
            steps.add("INPUT password = \"123\" (quá ngắn)");
            steps.add("HTTP POST /api/auth/register  body: {username:\"\", email:\"not-an-email\", password:\"123\"}");
            steps.add("EXPECT_STATUS 400");
        }
        return steps;
    }

    private List<String> buildLogoutSteps(String dataMode) {
        List<String> steps = new ArrayList<>();
        steps.add("Lấy JWT token từ bước đăng nhập trước đó");
        if ("valid".equals(dataMode)) {
            steps.add("HTTP POST /api/auth/logout  Header: Authorization: Bearer {token}");
            steps.add("EXPECT_STATUS 200");
            steps.add("Xóa token khỏi localStorage/session");
        } else {
            steps.add("HTTP POST /api/auth/logout  Header: Authorization: Bearer invalid_token");
            steps.add("EXPECT_STATUS 401");
        }
        return steps;
    }

    private List<String> buildCreateSteps(String ucName, String dataMode) {
        List<String> steps = new ArrayList<>();
        if ("valid".equals(dataMode)) {
            steps.add("Chuẩn bị JWT token hợp lệ (đã đăng nhập)");
            steps.add("INPUT dữ liệu hợp lệ cho: " + ucName);
            steps.add("HTTP POST /api/{resource}  Header: Authorization: Bearer {token}  body: {valid data}");
            steps.add("EXPECT_STATUS 200 hoặc 201");
            steps.add("EXPECT_BODY_FIELD data.id (ID của resource vừa tạo)");
        } else {
            steps.add("Chuẩn bị JWT token hợp lệ");
            steps.add("INPUT dữ liệu thiếu field bắt buộc cho: " + ucName);
            steps.add("HTTP POST /api/{resource}  body: {missing required fields}");
            steps.add("EXPECT_STATUS 400");
        }
        return steps;
    }

    private List<String> buildUpdateSteps(String ucName, String dataMode) {
        List<String> steps = new ArrayList<>();
        if ("valid".equals(dataMode)) {
            steps.add("Chuẩn bị ID của resource cần cập nhật");
            steps.add("INPUT dữ liệu mới hợp lệ cho: " + ucName);
            steps.add("HTTP PUT /api/{resource}/{id}  Header: Authorization: Bearer {token}  body: {updated data}");
            steps.add("EXPECT_STATUS 200");
            steps.add("EXPECT response phản ánh dữ liệu đã được cập nhật");
        } else {
            steps.add("Sử dụng ID không tồn tại (UUID ngẫu nhiên)");
            steps.add("HTTP PUT /api/{resource}/nonexistent-id  body: {}");
            steps.add("EXPECT_STATUS 404 hoặc 400");
        }
        return steps;
    }

    private List<String> buildDeleteSteps(String ucName, String dataMode) {
        List<String> steps = new ArrayList<>();
        if ("valid".equals(dataMode)) {
            steps.add("Chuẩn bị ID của resource cần xóa: " + ucName);
            steps.add("HTTP DELETE /api/{resource}/{id}  Header: Authorization: Bearer {token}");
            steps.add("EXPECT_STATUS 200 hoặc 204");
            steps.add("Gọi GET lại để xác nhận đã bị xóa → EXPECT_STATUS 404");
        } else {
            steps.add("HTTP DELETE /api/{resource}/invalid-id  (không có auth)");
            steps.add("EXPECT_STATUS 401 hoặc 403");
        }
        return steps;
    }

    private List<String> buildViewSteps(String ucName, String dataMode) {
        List<String> steps = new ArrayList<>();
        if ("valid".equals(dataMode)) {
            steps.add("HTTP GET /api/{resource}  Header: Authorization: Bearer {token}");
            steps.add("EXPECT_STATUS 200");
            steps.add("EXPECT_BODY là array JSON hoặc object có data field");
        } else {
            steps.add("HTTP GET /api/{resource}  (không gửi Authorization header)");
            steps.add("EXPECT_STATUS 401 hoặc 403 — phải yêu cầu xác thực");
        }
        return steps;
    }

    private List<String> buildSearchSteps(String ucName, String dataMode) {
        List<String> steps = new ArrayList<>();
        if ("valid".equals(dataMode)) {
            steps.add("INPUT keyword = \"test\" (từ khóa tìm kiếm hợp lệ)");
            steps.add("HTTP GET /api/{resource}?q=test  Header: Authorization: Bearer {token}");
            steps.add("EXPECT_STATUS 200");
            steps.add("EXPECT_BODY chứa kết quả liên quan đến keyword");
        } else {
            steps.add("INPUT keyword = \"\" (từ khóa rỗng)");
            steps.add("HTTP GET /api/{resource}?q=");
            steps.add("EXPECT_STATUS 200 hoặc 400 — kiểm tra hành vi với input rỗng");
        }
        return steps;
    }

    private List<String> buildGenericSteps(String ucName, String dataMode) {
        List<String> steps = new ArrayList<>();
        steps.add("Chuẩn bị môi trường test cho: " + ucName);
        if ("valid".equals(dataMode)) {
            steps.add("INPUT dữ liệu hợp lệ theo đặc tả");
            steps.add("HTTP {METHOD} /api/{endpoint}  Header: Authorization: Bearer {token}");
            steps.add("EXPECT_STATUS 200");
            steps.add("EXPECT_BODY success = true");
        } else {
            steps.add("INPUT dữ liệu không hợp lệ / thiếu trường bắt buộc");
            steps.add("HTTP {METHOD} /api/{endpoint}");
            steps.add("EXPECT_STATUS 400 hoặc 422");
        }
        return steps;
    }

    private List<String> buildBoundaryApiSteps(UseCase uc, String bound) {
        List<String> steps = new ArrayList<>();
        String ucName = uc.getName();
        if ("min".equals(bound)) {
            steps.add("Thiết lập input = giá trị tối thiểu cho " + ucName);
            steps.add("VD: string field = 1 ký tự, number field = 0 hoặc 1");
            steps.add("HTTP {METHOD} /api/{endpoint}  body: {min value data}");
        } else {
            steps.add("Thiết lập input = giá trị tối đa cho " + ucName);
            steps.add("VD: string field = MAX_LENGTH ký tự, number field = MAX_INT");
            steps.add("HTTP {METHOD} /api/{endpoint}  body: {max value data}");
        }
        return steps;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITIES
    // ─────────────────────────────────────────────────────────────────────────

    private TestCase newTc(UseCase uc, AtomicInteger counter, TestType type, String name, String expected) {
        TestCase tc = new TestCase();
        tc.setUseCase(uc);
        tc.setTcCode(String.format("TC-%03d", counter.getAndIncrement()));
        tc.setName(name);
        tc.setTestType(type);
        tc.setStatus(com.testcase.backend.enums.TestCaseStatus.PENDING);
        tc.setExpectedResult(expected);
        return tc;
    }

    /**
     * Xây bảng ánh xạ: sourceXmiId → [targetXmiId, ...] theo loại quan hệ.
     */
    private Map<String, List<String>> buildRelationMap(List<Relationship> rels, RelationType type) {
        Map<String, List<String>> map = new HashMap<>();
        for (Relationship r : rels) {
            if (r.getType() == type) {
                map.computeIfAbsent(r.getSourceXmiId(), k -> new ArrayList<>())
                        .add(r.getTargetXmiId());
            }
        }
        return map;
    }

    private List<UseCase> resolveUCs(List<String> xmiIds, Map<String, UseCase> lookup) {
        return xmiIds.stream()
                .map(lookup::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}