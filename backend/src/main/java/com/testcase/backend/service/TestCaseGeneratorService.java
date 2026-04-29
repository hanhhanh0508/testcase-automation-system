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
 * Engine sinh Test Case từ Use Case Diagram.
 *
 * Thuật toán cốt lõi:
 * 1. Đọc UseCases + Relationships từ DB.
 * 2. Xây dựng đồ thị quan hệ (include / extend / association / generalization).
 * 3. Với mỗi Use Case:
 * - Happy Path : chèn thêm bước của các UC được «include» (tự động mở rộng).
 * - Negative : sinh kịch bản lỗi với dữ liệu không hợp lệ / thiếu auth.
 * - Boundary : sinh kịch bản giá trị biên min / max.
 * 4. Với quan hệ «extend» → sinh thêm kịch bản extension riêng.
 * 5. Với quan hệ «generalization» (UC con kế thừa UC cha) → sinh kịch bản kế
 * thừa.
 * 6. Lưu TestCase, cập nhật trạng thái UseCase & Diagram.
 *
 * Sử dụng RestTemplate (Spring HTTP Client) để thực thi — không dùng Selenium.
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

    // ═════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═════════════════════════════════════════════════════════════════════════

    public List<TestCase> generateForDiagram(UUID diagramId) {
        UseCaseDiagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new RuntimeException("Diagram not found: " + diagramId));

        List<UseCase> useCases = useCaseRepository.findByDiagramId(diagramId);
        List<Relationship> relationships = relationshipRepository.findByDiagramId(diagramId);

        if (useCases.isEmpty()) {
            throw new RuntimeException(
                    "Diagram chưa có use case nào. Hãy nhập dữ liệu use case trước.");
        }

        // ── Bảng tra xmiId → UseCase ──────────────────────────────────────
        Map<String, UseCase> ucByXmiId = useCases.stream()
                .collect(Collectors.toMap(UseCase::getXmiId, uc -> uc, (a, b) -> a));

        // ── Phân loại quan hệ theo type ───────────────────────────────────
        Map<String, List<String>> includeMap = buildRelationMap(relationships, RelationType.INCLUDE);
        Map<String, List<String>> extendMap = buildRelationMap(relationships, RelationType.EXTEND);
        Map<String, List<String>> generalizationMap = buildRelationMap(relationships, RelationType.GENERALIZATION);

        // ── Counter toàn cục cho mã TC-001, TC-002, ... ──────────────────
        long existingCount = testCaseRepository.count();
        AtomicInteger counter = new AtomicInteger((int) existingCount + 1);

        List<TestCase> generated = new ArrayList<>();

        for (UseCase uc : useCases) {
            if (uc.getStatus() == UseCaseStatus.GENERATED)
                continue;

            // Các UC bắt buộc phải đi kèm (include)
            List<UseCase> includedUCs = resolveUCs(
                    includeMap.getOrDefault(uc.getXmiId(), List.of()), ucByXmiId);

            // Các UC cha được kế thừa (generalization)
            List<UseCase> parentUCs = resolveUCs(
                    generalizationMap.getOrDefault(uc.getXmiId(), List.of()), ucByXmiId);

            // 1. Happy Path (bao gồm include steps)
            generated.add(buildHappyPath(uc, includedUCs, parentUCs, counter));

            // 2. Negative Case
            generated.add(buildNegative(uc, counter));

            // 3. Boundary Case
            generated.add(buildBoundary(uc, counter));

            // 4. Extension Cases — mỗi «extend» → 1 TC riêng
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

    // ═════════════════════════════════════════════════════════════════════════
    // BUILDERS — tạo từng loại TestCase
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Happy Path: luồng thành công chính.
     * Tự động chèn thêm các bước của UC được «include» (UML Include).
     * Nếu UC kế thừa từ UC cha (UML Generalization), đưa thêm ghi chú.
     */
    private TestCase buildHappyPath(UseCase uc,
            List<UseCase> includedUCs,
            List<UseCase> parentUCs,
            AtomicInteger counter) {

        String expectedResult = "Hệ thống xử lý thành công. "
                + "HTTP status 200/201. Response body chứa dữ liệu hợp lệ.";

        TestCase tc = newTc(uc, counter, TestType.HAPPY_PATH,
                uc.getName() + " — Happy Path", expectedResult);

        List<String> steps = new ArrayList<>();

        // ── PHẦN 1: Chuẩn bị ──────────────────────────────────────────────
        steps.add("=== [CHUẨN BỊ] ===");
        steps.add("Xác nhận môi trường test đang hoạt động tại base URL.");

        if (!uc.getPreconditions().isEmpty()) {
            steps.add("Kiểm tra điều kiện tiên quyết: " + String.join(" | ", uc.getPreconditions()));
        }

        // Nếu có kế thừa (generalization), ghi chú hành vi cha
        if (!parentUCs.isEmpty()) {
            for (UseCase parent : parentUCs) {
                steps.add("[GENERALIZATION] UC này kế thừa từ «" + parent.getName()
                        + "» — kiểm tra hành vi cơ sở của UC cha trước.");
            }
        }

        steps.add("Chuẩn bị dữ liệu đầu vào HỢP LỆ cho: " + uc.getName());

        // ── PHẦN 2: Các bước HTTP chính ──────────────────────────────────
        steps.add("=== [THỰC HIỆN - " + uc.getName().toUpperCase() + "] ===");
        steps.addAll(buildApiSteps(uc, "valid"));

        // ── PHẦN 3: Chèn bước từ các UC được INCLUDE ────────────────────
        for (UseCase inc : includedUCs) {
            steps.add("--- [INCLUDE] «" + inc.getName() + "» (bắt buộc thực hiện) ---");
            steps.add("Bước include này luôn được gọi khi thực thi «" + uc.getName() + "».");
            steps.addAll(buildApiSteps(inc, "valid"));
            steps.add("Xác nhận «" + inc.getName() + "» hoàn thành thành công.");
        }

        // ── PHẦN 4: Xác nhận kết quả ─────────────────────────────────────
        steps.add("=== [XÁC NHẬN KẾT QUẢ] ===");
        steps.add("EXPECT_STATUS 200 hoặc 201");
        steps.add("EXPECT_BODY_FIELD data (response phải có trường data)");
        steps.add("EXPECT_BODY_CONTAINS success (hoặc từ khóa thành công phù hợp)");

        if (!uc.getPostconditions().isEmpty()) {
            steps.add("Kiểm tra post-condition: " + String.join(" | ", uc.getPostconditions()));
        }

        tc.setSteps(steps);
        return tc;
    }

    /**
     * Negative Case: dữ liệu sai, thiếu, không xác thực.
     * Đảm bảo hệ thống trả về lỗi rõ ràng và KHÔNG thực hiện hành động.
     */
    private TestCase buildNegative(UseCase uc, AtomicInteger counter) {
        String expectedResult = "Hệ thống trả về HTTP 400/401/403/422. "
                + "Response body chứa thông báo lỗi rõ ràng. "
                + "Không có dữ liệu nào bị thay đổi sai trong DB.";

        TestCase tc = newTc(uc, counter, TestType.NEGATIVE,
                uc.getName() + " — Dữ liệu không hợp lệ", expectedResult);

        List<String> steps = new ArrayList<>();

        steps.add("=== [CHUẨN BỊ DỮ LIỆU LỖI] ===");
        steps.add("Chuẩn bị các tập dữ liệu KHÔNG hợp lệ cho: " + uc.getName());
        steps.add("Liệt kê các trường bắt buộc và loại lỗi cần kiểm tra:");
        steps.add("  • Dữ liệu rỗng/null cho các trường bắt buộc");
        steps.add("  • Định dạng sai (email sai format, số âm, ngày không hợp lệ)");
        steps.add("  • Dữ liệu vượt giới hạn (chuỗi quá dài, số quá lớn)");
        steps.add("  • Token JWT không hợp lệ hoặc đã hết hạn");

        steps.add("=== [KIỂM THỬ NEGATIVE - " + uc.getName().toUpperCase() + "] ===");
        steps.addAll(buildApiSteps(uc, "invalid"));

        steps.add("=== [KIỂM THỬ THIẾU XÁC THỰC] ===");
        steps.add("Gửi request KHÔNG có Authorization header.");
        steps.addAll(buildApiSteps(uc, "no_auth"));

        steps.add("=== [XÁC NHẬN LỖI] ===");
        steps.add("EXPECT_STATUS 400 hoặc 401 hoặc 403 hoặc 422");
        steps.add("EXPECT_BODY_FIELD message (thông báo lỗi phải tồn tại)");
        steps.add("Xác nhận dữ liệu trong DB KHÔNG bị thay đổi sai.");
        steps.add("Xác nhận hệ thống KHÔNG thực hiện hành động không mong muốn.");

        tc.setSteps(steps);
        return tc;
    }

    /**
     * Boundary Case: kiểm tra tại biên giới min/max của từng trường.
     */
    private TestCase buildBoundary(UseCase uc, AtomicInteger counter) {
        String expectedResult = "Hệ thống xử lý đúng tại biên giới min/max. "
                + "Giá trị hợp lệ tại biên → 200. "
                + "Giá trị vượt biên → 400/422.";

        TestCase tc = newTc(uc, counter, TestType.BOUNDARY,
                uc.getName() + " — Giá trị biên", expectedResult);

        List<String> steps = new ArrayList<>();

        steps.add("=== [XÁC ĐỊNH GIÁ TRỊ BIÊN] ===");
        steps.add("Liệt kê tất cả trường có ràng buộc giá trị trong: " + uc.getName());
        steps.add("Ví dụ: username (3-50 ký tự), password (6-128 ký tự), age (0-150), price (0-999999)");

        steps.add("=== [TEST MIN BOUNDARY — Giá trị tối thiểu] ===");
        steps.addAll(buildBoundaryApiSteps(uc, "min"));
        steps.add("EXPECT_STATUS 200 — hệ thống phải chấp nhận giá trị tại min.");

        steps.add("=== [TEST MAX BOUNDARY — Giá trị tối đa] ===");
        steps.addAll(buildBoundaryApiSteps(uc, "max"));
        steps.add("EXPECT_STATUS 200 — hệ thống phải chấp nhận giá trị tại max.");

        steps.add("=== [TEST DƯỚI MIN — Phải bị từ chối] ===");
        steps.add("INPUT: string field = \"\" (rỗng), number field = (min - 1) hoặc giá trị âm.");
        steps.add("HTTP {METHOD} /api/{endpoint} body: {below-min data}");
        steps.add("EXPECT_STATUS 400 hoặc 422 — hệ thống PHẢI từ chối giá trị dưới min.");

        steps.add("=== [TEST TRÊN MAX — Phải bị từ chối] ===");
        steps.add("INPUT: string field = chuỗi " + uc.getName().length() * 20 + "+ ký tự, number = MAX_LONG.");
        steps.add("HTTP {METHOD} /api/{endpoint} body: {above-max data}");
        steps.add("EXPECT_STATUS 400 hoặc 422 — hệ thống PHẢI từ chối giá trị trên max.");

        tc.setSteps(steps);
        return tc;
    }

    /**
     * Extension Case: kịch bản khi điều kiện «extend» được thỏa mãn.
     * Đây là trường hợp rẽ nhánh từ Use Case gốc.
     */
    private TestCase buildExtensionCase(UseCase baseUC,
            UseCase extUC,
            AtomicInteger counter) {
        String expectedResult = "Luồng mở rộng «" + extUC.getName() + "» được kích hoạt và thực thi thành công. "
                + "Kết quả cuối cùng nhất quán với cả base UC và extension UC.";

        TestCase tc = newTc(baseUC, counter, TestType.HAPPY_PATH,
                baseUC.getName() + " + [EXTEND] «" + extUC.getName() + "»", expectedResult);

        List<String> steps = new ArrayList<>();

        steps.add("=== [KỊCH BẢN MỞ RỘNG (EXTEND)] ===");
        steps.add("Base Use Case  : " + baseUC.getName());
        steps.add("Extension UC   : " + extUC.getName());
        steps.add("Ghi chú: Extension chỉ được kích hoạt khi điều kiện cụ thể được thỏa mãn.");

        steps.add("=== [BƯỚC 1: Chuẩn bị điều kiện kích hoạt Extension] ===");
        steps.add("Thiết lập trạng thái/dữ liệu để điều kiện của «" + extUC.getName() + "» được thỏa mãn.");
        steps.add("Ví dụ: user chọn tuỳ chọn nâng cao, trạng thái tài khoản đặc biệt, v.v.");

        steps.add("=== [BƯỚC 2: Thực thi Base UC] ===");
        steps.addAll(buildApiSteps(baseUC, "valid"));

        steps.add("=== [BƯỚC 3: Extension Point — Điều kiện kích hoạt «" + extUC.getName() + "»] ===");
        steps.add("Xác nhận điều kiện extension đã được kích hoạt.");
        steps.addAll(buildApiSteps(extUC, "valid"));

        steps.add("=== [BƯỚC 4: Xác nhận kết quả tổng hợp] ===");
        steps.add("EXPECT_STATUS 200");
        steps.add("Kiểm tra luồng extension «" + extUC.getName() + "» đã được thực thi.");
        steps.add("Kiểm tra kết quả cuối cùng nhất quán với base và extension.");

        tc.setSteps(steps);
        return tc;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SINH BƯỚC HTTP — Library-based (RestTemplate), không dùng Selenium
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Sinh danh sách step dựa trên tên Use Case và dataMode.
     * Engine nhận dạng pattern phổ biến: login, register, logout,
     * create, update, delete, view/list, search, upload, export.
     *
     * @param dataMode "valid" | "invalid" | "no_auth" | "min" | "max"
     */
    private List<String> buildApiSteps(UseCase uc, String dataMode) {
        String name = uc.getName().toLowerCase();

        if (name.contains("login") || name.contains("đăng nhập") || name.contains("signin"))
            return buildLoginSteps(dataMode);
        if (name.contains("register") || name.contains("đăng ký") || name.contains("signup"))
            return buildRegisterSteps(dataMode);
        if (name.contains("logout") || name.contains("đăng xuất"))
            return buildLogoutSteps(dataMode);
        if (name.contains("upload") || name.contains("tải lên") || name.contains("import"))
            return buildUploadSteps(uc.getName(), dataMode);
        if (name.contains("export") || name.contains("xuất") || name.contains("download"))
            return buildExportSteps(uc.getName(), dataMode);
        if (name.contains("search") || name.contains("tìm") || name.contains("filter"))
            return buildSearchSteps(uc.getName(), dataMode);
        if (name.contains("delete") || name.contains("xóa") || name.contains("remove"))
            return buildDeleteSteps(uc.getName(), dataMode);
        if (name.contains("update") || name.contains("edit") || name.contains("sửa")
                || name.contains("cập nhật"))
            return buildUpdateSteps(uc.getName(), dataMode);
        if (name.contains("create") || name.contains("tạo") || name.contains("thêm") || name.contains("add"))
            return buildCreateSteps(uc.getName(), dataMode);
        if (name.contains("view") || name.contains("xem") || name.contains("list")
                || name.contains("danh sách") || name.contains("get"))
            return buildViewSteps(uc.getName(), dataMode);

        // Generic fallback
        return buildGenericSteps(uc.getName(), dataMode);
    }

    // ── Login ──────────────────────────────────────────────────────────────
    private List<String> buildLoginSteps(String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("INPUT username = \"testuser\" (tài khoản đã tồn tại trong DB)");
                s.add("INPUT password = \"Password@123\" (mật khẩu đúng)");
                s.add("SET_BODY {\"username\":\"testuser\",\"password\":\"Password@123\"}");
                s.add("HTTP POST /api/auth/login");
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY_FIELD data.token");
                s.add("Lưu token để dùng cho các bước tiếp theo.");
            }
            case "invalid" -> {
                s.add("INPUT username = \"testuser\"");
                s.add("INPUT password = \"SaiMatKhau999\" (mật khẩu SAI)");
                s.add("SET_BODY {\"username\":\"testuser\",\"password\":\"SaiMatKhau999\"}");
                s.add("HTTP POST /api/auth/login");
                s.add("EXPECT_STATUS 400");
                s.add("EXPECT_BODY_FIELD message");
            }
            case "no_auth" -> {
                s.add("Gửi request đến endpoint yêu cầu auth mà KHÔNG có token.");
                s.add("HTTP GET /api/diagrams");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Register ───────────────────────────────────────────────────────────
    private List<String> buildRegisterSteps(String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("INPUT username = \"newuser_\" + System.currentTimeMillis() (đảm bảo unique)");
                s.add("INPUT email    = \"newuser@example.com\"");
                s.add("INPUT password = \"StrongPass@123\"");
                s.add("SET_BODY {\"username\":\"newuser_ts\",\"email\":\"newuser@example.com\",\"password\":\"StrongPass@123\"}");
                s.add("HTTP POST /api/auth/register");
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY_FIELD data.token");
            }
            case "invalid" -> {
                s.add("INPUT username = \"\" (bỏ trống — trường bắt buộc)");
                s.add("INPUT email    = \"not-valid-email\" (sai định dạng)");
                s.add("INPUT password = \"123\" (quá ngắn)");
                s.add("SET_BODY {\"username\":\"\",\"email\":\"not-valid-email\",\"password\":\"123\"}");
                s.add("HTTP POST /api/auth/register");
                s.add("EXPECT_STATUS 400");
                s.add("EXPECT_BODY_FIELD message");
            }
            case "no_auth" -> {
                s.add("Register không yêu cầu auth — bỏ qua bước này.");
            }
        }
        return s;
    }

    // ── Logout ─────────────────────────────────────────────────────────────
    private List<String> buildLogoutSteps(String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Lấy JWT token hợp lệ từ bước đăng nhập trước đó.");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP POST /api/auth/logout");
                s.add("EXPECT_STATUS 200");
                s.add("Xóa token khỏi client storage.");
            }
            case "invalid" -> {
                s.add("HEADER Authorization: Bearer invalid_token_xyz");
                s.add("HTTP POST /api/auth/logout");
                s.add("EXPECT_STATUS 401");
            }
            case "no_auth" -> {
                s.add("HTTP POST /api/auth/logout (không có header Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Create ─────────────────────────────────────────────────────────────
    private List<String> buildCreateSteps(String ucName, String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Lấy JWT token hợp lệ (đã đăng nhập).");
                s.add("INPUT: chuẩn bị dữ liệu HỢP LỆ đầy đủ các trường bắt buộc cho «" + ucName + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("SET_BODY {dữ liệu hợp lệ theo đặc tả}");
                s.add("HTTP POST /api/{resource}");
                s.add("EXPECT_STATUS 200 hoặc 201");
                s.add("EXPECT_BODY_FIELD data.id (ID của resource vừa tạo)");
                s.add("Lưu ID vừa tạo để dùng trong các bước kiểm tra sau.");
            }
            case "invalid" -> {
                s.add("Lấy JWT token hợp lệ.");
                s.add("INPUT: dữ liệu THIẾU các trường bắt buộc / sai kiểu dữ liệu cho «" + ucName + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("SET_BODY {thiếu required fields}");
                s.add("HTTP POST /api/{resource}");
                s.add("EXPECT_STATUS 400 hoặc 422");
                s.add("EXPECT_BODY_FIELD message");
            }
            case "no_auth" -> {
                s.add("SET_BODY {dữ liệu hợp lệ}");
                s.add("HTTP POST /api/{resource} (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Update ─────────────────────────────────────────────────────────────
    private List<String> buildUpdateSteps(String ucName, String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Chuẩn bị ID của resource cần cập nhật (đã tồn tại trong DB).");
                s.add("INPUT: dữ liệu cập nhật HỢP LỆ cho «" + ucName + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("SET_BODY {updated data}");
                s.add("HTTP PUT /api/{resource}/{id}");
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY phản ánh giá trị đã được cập nhật đúng.");
            }
            case "invalid" -> {
                s.add("Sử dụng ID KHÔNG tồn tại (UUID ngẫu nhiên: 00000000-0000-0000-0000-000000000000).");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP PUT /api/{resource}/00000000-0000-0000-0000-000000000000");
                s.add("EXPECT_STATUS 404 hoặc 400");
            }
            case "no_auth" -> {
                s.add("HTTP PUT /api/{resource}/{id} (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Delete ─────────────────────────────────────────────────────────────
    private List<String> buildDeleteSteps(String ucName, String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Chuẩn bị ID của resource cần xóa: «" + ucName + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP DELETE /api/{resource}/{id}");
                s.add("EXPECT_STATUS 200 hoặc 204");
                s.add("HTTP GET /api/{resource}/{id} (kiểm tra đã xóa)");
                s.add("EXPECT_STATUS 404 — resource không còn tồn tại.");
            }
            case "invalid" -> {
                s.add("HTTP DELETE /api/{resource}/nonexistent-id");
                s.add("EXPECT_STATUS 404 hoặc 400");
            }
            case "no_auth" -> {
                s.add("HTTP DELETE /api/{resource}/{id} (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── View / List ────────────────────────────────────────────────────────
    private List<String> buildViewSteps(String ucName, String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP GET /api/{resource}");
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY là JSON array hoặc object chứa data field.");
                s.add("Kiểm tra cấu trúc dữ liệu trả về đúng với đặc tả: «" + ucName + "».");
            }
            case "invalid" -> {
                s.add("HTTP GET /api/{resource}/nonexistent-id");
                s.add("EXPECT_STATUS 404");
            }
            case "no_auth" -> {
                s.add("HTTP GET /api/{resource} (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Search ─────────────────────────────────────────────────────────────
    private List<String> buildSearchSteps(String ucName, String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("INPUT keyword = \"test\" (từ khóa hợp lệ, có kết quả).");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP GET /api/{resource}?q=test");
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY chứa kết quả liên quan đến keyword «test».");
                s.add("Kiểm tra kết quả được sắp xếp/lọc đúng cho: «" + ucName + "».");
            }
            case "invalid" -> {
                s.add("INPUT keyword = \"\" (rỗng).");
                s.add("HTTP GET /api/{resource}?q=");
                s.add("EXPECT_STATUS 200 (trả về toàn bộ) hoặc 400 (reject) — tuỳ business rule.");
                s.add("Kiểm tra hành vi của hệ thống với input rỗng.");
            }
            case "no_auth" -> {
                s.add("HTTP GET /api/{resource}?q=test (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Upload ─────────────────────────────────────────────────────────────
    private List<String> buildUploadSteps(String ucName, String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Chuẩn bị file hợp lệ: .xmi / .puml / .json (< 10MB) cho «" + ucName + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HEADER Content-Type: multipart/form-data");
                s.add("HTTP POST /api/diagrams/upload (multipart: file + optional name)");
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY_FIELD data.id (diagram ID vừa tạo)");
                s.add("Lưu diagram ID để dùng cho các bước tiếp theo.");
            }
            case "invalid" -> {
                s.add("Chuẩn bị file KHÔNG hợp lệ: .exe, .zip, file > 10MB, file rỗng.");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP POST /api/diagrams/upload (multipart: file sai định dạng)");
                s.add("EXPECT_STATUS 400 — hệ thống từ chối file không hợp lệ.");
            }
            case "no_auth" -> {
                s.add("HTTP POST /api/diagrams/upload (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Export ─────────────────────────────────────────────────────────────
    private List<String> buildExportSteps(String ucName, String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Chuẩn bị diagram ID có test case đã được sinh cho «" + ucName + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP GET /api/export/{diagramId}/excel");
                s.add("EXPECT_STATUS 200");
                s.add("Kiểm tra Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                s.add("HTTP GET /api/export/{diagramId}/pdf");
                s.add("EXPECT_STATUS 200");
                s.add("Kiểm tra Content-Type: application/pdf");
            }
            case "invalid" -> {
                s.add("HTTP GET /api/export/nonexistent-diagram-id/excel");
                s.add("EXPECT_STATUS 404 hoặc 500 — diagram không tồn tại.");
            }
            case "no_auth" -> {
                s.add("HTTP GET /api/export/{diagramId}/excel (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Generic fallback ───────────────────────────────────────────────────
    private List<String> buildGenericSteps(String ucName, String dataMode) {
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Chuẩn bị môi trường và dữ liệu HỢP LỆ cho: «" + ucName + "».");
                s.add("INPUT: điền đầy đủ dữ liệu theo đặc tả chức năng.");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("SET_BODY {dữ liệu hợp lệ}");
                s.add("HTTP {METHOD} /api/{endpoint}");
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY_FIELD data");
            }
            case "invalid" -> {
                s.add("Chuẩn bị dữ liệu KHÔNG hợp lệ / thiếu trường bắt buộc cho: «" + ucName + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP {METHOD} /api/{endpoint}");
                s.add("EXPECT_STATUS 400 hoặc 422");
                s.add("EXPECT_BODY_FIELD message");
            }
            case "no_auth" -> {
                s.add("HTTP {METHOD} /api/{endpoint} (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Boundary steps ─────────────────────────────────────────────────────
    private List<String> buildBoundaryApiSteps(UseCase uc, String bound) {
        List<String> s = new ArrayList<>();
        String ucName = uc.getName();
        if ("min".equals(bound)) {
            s.add("INPUT: thiết lập mỗi trường = giá trị TỐI THIỂU hợp lệ cho «" + ucName + "».");
            s.add("Ví dụ: string = 1 ký tự, number = 0, date = ngày hôm nay.");
            s.add("HEADER Authorization: Bearer {token}");
            s.add("HTTP {METHOD} /api/{endpoint} body: {min value data}");
        } else {
            s.add("INPUT: thiết lập mỗi trường = giá trị TỐI ĐA hợp lệ cho «" + ucName + "».");
            s.add("Ví dụ: string = MAX_LENGTH ký tự, number = MAX_INT, date = ngày xa nhất.");
            s.add("HEADER Authorization: Bearer {token}");
            s.add("HTTP {METHOD} /api/{endpoint} body: {max value data}");
        }
        return s;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═════════════════════════════════════════════════════════════════════════

    private TestCase newTc(UseCase uc, AtomicInteger counter,
            TestType type, String name, String expected) {
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
    private Map<String, List<String>> buildRelationMap(List<Relationship> rels,
            RelationType type) {
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