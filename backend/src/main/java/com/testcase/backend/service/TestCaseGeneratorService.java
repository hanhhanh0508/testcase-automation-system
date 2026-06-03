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

        Map<String, UseCase> ucByXmiId = useCases.stream()
                .collect(Collectors.toMap(UseCase::getXmiId, uc -> uc, (a, b) -> a));

        Map<String, List<String>> includeMap = buildRelationMap(relationships, RelationType.INCLUDE);
        Map<String, List<String>> extendMap = buildRelationMap(relationships, RelationType.EXTEND);
        Map<String, List<String>> generalizationMap = buildRelationMap(relationships, RelationType.GENERALIZATION);

        long existingCount = testCaseRepository.count();
        AtomicInteger counter = new AtomicInteger((int) existingCount + 1);

        List<TestCase> generated = new ArrayList<>();

        for (UseCase uc : useCases) {
            if (uc.getStatus() == UseCaseStatus.GENERATED)
                continue;

            List<UseCase> includedUCs = resolveUCs(
                    includeMap.getOrDefault(uc.getXmiId(), List.of()), ucByXmiId);
            List<UseCase> parentUCs = resolveUCs(
                    generalizationMap.getOrDefault(uc.getXmiId(), List.of()), ucByXmiId);

            generated.add(buildHappyPath(uc, includedUCs, parentUCs, counter));
            generated.add(buildNegative(uc, counter));
            generated.add(buildBoundary(uc, counter));

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
    // RESOLVE HTTP METHOD & ENDPOINT from use case name
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Suy ra HTTP method phù hợp từ tên use case.
     */
    private String resolveHttpMethod(UseCase uc) {
        String name = uc.getName().toLowerCase();
        if (name.contains("create") || name.contains("tạo") || name.contains("thêm")
                || name.contains("add") || name.contains("register") || name.contains("đăng ký")
                || name.contains("signup") || name.contains("login") || name.contains("đăng nhập")
                || name.contains("signin") || name.contains("logout") || name.contains("đăng xuất")
                || name.contains("upload") || name.contains("tải lên") || name.contains("borrow")
                || name.contains("mượn") || name.contains("return") || name.contains("trả")
                || name.contains("send") || name.contains("gửi") || name.contains("checkout")
                || name.contains("payment") || name.contains("thanh toán")) {
            return "POST";
        }
        if (name.contains("update") || name.contains("edit") || name.contains("sửa")
                || name.contains("cập nhật") || name.contains("manage")) {
            return "PUT";
        }
        if (name.contains("delete") || name.contains("xóa") || name.contains("remove")) {
            return "DELETE";
        }
        // default: GET
        return "GET";
    }

    /**
     * Suy ra endpoint REST phù hợp từ tên use case.
     */
    private String resolveEndpoint(UseCase uc) {
        String name = uc.getName().toLowerCase();

        // Auth endpoints
        if (name.contains("login") || name.contains("đăng nhập") || name.contains("signin"))
            return "/api/auth/login";
        if (name.contains("register") || name.contains("đăng ký") || name.contains("signup"))
            return "/api/auth/register";
        if (name.contains("logout") || name.contains("đăng xuất"))
            return "/api/auth/logout";

        // Resource-based endpoints: derive resource name from use case name
        String resource = deriveResourceName(uc.getName());

        if (name.contains("view") || name.contains("xem") || name.contains("list")
                || name.contains("danh sách") || name.contains("search") || name.contains("tìm")
                || name.contains("get") || name.contains("history") || name.contains("lịch sử")
                || name.contains("order") || name.contains("đơn hàng")) {
            return "/api/" + resource;
        }
        if (name.contains("create") || name.contains("tạo") || name.contains("thêm")
                || name.contains("add") || name.contains("upload") || name.contains("tải lên")) {
            return "/api/" + resource;
        }
        if (name.contains("update") || name.contains("edit") || name.contains("sửa")
                || name.contains("cập nhật") || name.contains("manage")) {
            return "/api/" + resource + "/{id}";
        }
        if (name.contains("delete") || name.contains("xóa") || name.contains("remove")) {
            return "/api/" + resource + "/{id}";
        }
        if (name.contains("borrow") || name.contains("mượn")) {
            return "/api/borrows";
        }
        if (name.contains("return") || name.contains("trả")) {
            return "/api/borrows/{id}/return";
        }
        if (name.contains("send") || name.contains("gửi") || name.contains("notification")
                || name.contains("thông báo")) {
            return "/api/notifications";
        }
        if (name.contains("checkout")) {
            return "/api/orders/checkout";
        }
        if (name.contains("payment") || name.contains("thanh toán")) {
            return "/api/payments";
        }
        if (name.contains("cart") || name.contains("giỏ hàng")) {
            return "/api/cart";
        }

        return "/api/" + resource;
    }

    /**
     * Tách tên resource từ tên use case (bỏ động từ, giữ danh từ, chuyển thành
     * kebab-case).
     */
    private String deriveResourceName(String ucName) {
        String name = ucName.toLowerCase()
                // Bỏ các động từ thường gặp
                .replaceAll(
                        "\\b(manage|view|create|add|update|edit|delete|remove|search|filter|get|list|send|upload|download|export|import)\\s*",
                        "")
                .replaceAll("\\b(tạo|thêm|xem|sửa|xóa|tìm|lọc|tải|xuất|gửi|quản lý|cập nhật)\\s*", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9\\-]", "");

        if (name.isEmpty() || name.equals("-")) {
            // Fallback: dùng 2 từ đầu của tên gốc
            String[] parts = ucName.toLowerCase().split("\\s+");
            if (parts.length >= 2)
                name = parts[1];
            else
                name = parts[0];
            name = name.replaceAll("[^a-z0-9]", "");
        }

        // Xử lý một số trường hợp đặc biệt
        if (name.contains("book"))
            return "books";
        if (name.contains("product") || name.contains("sản-phẩm"))
            return "products";
        if (name.contains("order") || name.contains("đơn"))
            return "orders";
        if (name.contains("user") || name.contains("account") || name.contains("tài-khoản"))
            return "users";
        if (name.contains("diagram"))
            return "diagrams";
        if (name.contains("testcase") || name.contains("test-case"))
            return "testcases";
        if (name.contains("borrow") || name.contains("mượn"))
            return "borrows";

        // Đảm bảo số nhiều
        if (!name.isEmpty() && !name.endsWith("s") && !name.endsWith("-")) {
            name = name + "s";
        }

        return name.isEmpty() ? "resources" : name;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BUILDERS
    // ═════════════════════════════════════════════════════════════════════════

    private TestCase buildHappyPath(UseCase uc,
            List<UseCase> includedUCs,
            List<UseCase> parentUCs,
            AtomicInteger counter) {

        String expectedResult = "Hệ thống xử lý thành công. "
                + "HTTP status 200/201. Response body chứa dữ liệu hợp lệ.";

        TestCase tc = newTc(uc, counter, TestType.HAPPY_PATH,
                uc.getName() + " — Happy Path", expectedResult);

        List<String> steps = new ArrayList<>();

        steps.add("=== [CHUẨN BỊ] ===");
        steps.add("Xác nhận môi trường test đang hoạt động tại base URL.");

        if (!uc.getPreconditions().isEmpty()) {
            steps.add("Kiểm tra điều kiện tiên quyết: " + String.join(" | ", uc.getPreconditions()));
        }

        if (!parentUCs.isEmpty()) {
            for (UseCase parent : parentUCs) {
                steps.add("[GENERALIZATION] UC này kế thừa từ «" + parent.getName()
                        + "» — kiểm tra hành vi cơ sở của UC cha trước.");
            }
        }

        steps.add("Chuẩn bị dữ liệu đầu vào HỢP LỆ cho: " + uc.getName());

        steps.add("=== [THỰC HIỆN - " + uc.getName().toUpperCase() + "] ===");
        steps.addAll(buildApiSteps(uc, "valid"));

        for (UseCase inc : includedUCs) {
            steps.add("--- [INCLUDE] «" + inc.getName() + "» (bắt buộc thực hiện) ---");
            steps.add("Bước include này luôn được gọi khi thực thi «" + uc.getName() + "».");
            steps.addAll(buildApiSteps(inc, "valid"));
            steps.add("Xác nhận «" + inc.getName() + "» hoàn thành thành công.");
        }

        steps.add("=== [XÁC NHẬN KẾT QUẢ] ===");
        steps.add("EXPECT_STATUS 200 hoặc 201");
        steps.add("EXPECT_BODY_FIELD data");
        steps.add("EXPECT_BODY_CONTAINS success");

        if (!uc.getPostconditions().isEmpty()) {
            steps.add("Kiểm tra post-condition: " + String.join(" | ", uc.getPostconditions()));
        }

        tc.setSteps(steps);
        return tc;
    }

    private TestCase buildNegative(UseCase uc, AtomicInteger counter) {
        String expectedResult = "Hệ thống trả về HTTP 400/401/403/422. "
                + "Response body chứa thông báo lỗi rõ ràng. "
                + "Không có dữ liệu nào bị thay đổi sai trong DB.";

        TestCase tc = newTc(uc, counter, TestType.NEGATIVE,
                uc.getName() + " — Dữ liệu không hợp lệ", expectedResult);

        List<String> steps = new ArrayList<>();

        steps.add("=== [CHUẨN BỊ DỮ LIỆU LỖI] ===");
        steps.add("Chuẩn bị các tập dữ liệu KHÔNG hợp lệ cho: " + uc.getName());
        steps.add("Các trường hợp kiểm tra:");
        steps.add("  • Dữ liệu rỗng/null cho các trường bắt buộc");
        steps.add("  • Định dạng sai (email sai format, số âm, ngày không hợp lệ)");
        steps.add("  • Dữ liệu vượt giới hạn (chuỗi quá dài, số quá lớn)");
        steps.add("  • Token JWT không hợp lệ hoặc đã hết hạn");

        steps.add("=== [KIỂM THỬ NEGATIVE - " + uc.getName().toUpperCase() + "] ===");
        steps.addAll(buildApiSteps(uc, "invalid"));

        steps.add("=== [KIỂM THỬ THIẾU XÁC THỰC] ===");
        steps.addAll(buildApiSteps(uc, "no_auth"));

        steps.add("=== [XÁC NHẬN LỖI] ===");
        steps.add("EXPECT_STATUS 400 hoặc 401 hoặc 403 hoặc 422");
        steps.add("EXPECT_BODY_FIELD message");
        steps.add("Xác nhận dữ liệu trong DB KHÔNG bị thay đổi sai.");

        tc.setSteps(steps);
        return tc;
    }

    private TestCase buildBoundary(UseCase uc, AtomicInteger counter) {
        String expectedResult = "Hệ thống xử lý đúng tại biên giới min/max. "
                + "Giá trị hợp lệ tại biên → 200. "
                + "Giá trị vượt biên → 400/422.";

        TestCase tc = newTc(uc, counter, TestType.BOUNDARY,
                uc.getName() + " — Giá trị biên", expectedResult);

        String method = resolveHttpMethod(uc);
        String endpoint = resolveEndpoint(uc);

        List<String> steps = new ArrayList<>();

        steps.add("=== [XÁC ĐỊNH GIÁ TRỊ BIÊN] ===");
        steps.add("Liệt kê tất cả trường có ràng buộc giá trị trong: " + uc.getName());
        steps.add("Ví dụ: username (3-50 ký tự), password (6-128 ký tự), age (0-150), price (0-999999)");

        steps.add("=== [TEST MIN BOUNDARY — Giá trị tối thiểu] ===");
        steps.add("INPUT: thiết lập mỗi trường = giá trị TỐI THIỂU hợp lệ cho «" + uc.getName() + "».");
        steps.add("Ví dụ: string = 1 ký tự, number = 0, date = ngày hôm nay.");
        steps.add("HEADER Authorization: Bearer {token}");
        steps.add("HTTP " + method + " " + endpoint + " body: {min value data}");
        steps.add("EXPECT_STATUS 200 — hệ thống phải chấp nhận giá trị tại min.");

        steps.add("=== [TEST MAX BOUNDARY — Giá trị tối đa] ===");
        steps.add("INPUT: thiết lập mỗi trường = giá trị TỐI ĐA hợp lệ cho «" + uc.getName() + "».");
        steps.add("Ví dụ: string = MAX_LENGTH ký tự, number = MAX_INT, date = ngày xa nhất.");
        steps.add("HEADER Authorization: Bearer {token}");
        steps.add("HTTP " + method + " " + endpoint + " body: {max value data}");
        steps.add("EXPECT_STATUS 200 — hệ thống phải chấp nhận giá trị tại max.");

        steps.add("=== [TEST DƯỚI MIN — Phải bị từ chối] ===");
        steps.add("INPUT: string field = \"\" (rỗng), number field = -1 (dưới giá trị min).");
        steps.add("HTTP " + method + " " + endpoint);
        steps.add("EXPECT_STATUS 400 hoặc 422 — hệ thống PHẢI từ chối giá trị dưới min.");

        steps.add("=== [TEST TRÊN MAX — Phải bị từ chối] ===");
        steps.add("INPUT: string field = chuỗi 1000+ ký tự, number = 9999999999.");
        steps.add("HTTP " + method + " " + endpoint);
        steps.add("EXPECT_STATUS 400 hoặc 422 — hệ thống PHẢI từ chối giá trị trên max.");

        tc.setSteps(steps);
        return tc;
    }

    private TestCase buildExtensionCase(UseCase baseUC, UseCase extUC, AtomicInteger counter) {
        String expectedResult = "Luồng mở rộng «" + extUC.getName() + "» được kích hoạt và thực thi thành công.";

        TestCase tc = newTc(baseUC, counter, TestType.HAPPY_PATH,
                baseUC.getName() + " + [EXTEND] «" + extUC.getName() + "»", expectedResult);

        List<String> steps = new ArrayList<>();

        steps.add("=== [KỊCH BẢN MỞ RỘNG (EXTEND)] ===");
        steps.add("Base Use Case  : " + baseUC.getName());
        steps.add("Extension UC   : " + extUC.getName());
        steps.add("Ghi chú: Extension chỉ được kích hoạt khi điều kiện cụ thể được thỏa mãn.");

        steps.add("=== [BƯỚC 1: Chuẩn bị điều kiện kích hoạt Extension] ===");
        steps.add("Thiết lập trạng thái/dữ liệu để điều kiện của «" + extUC.getName() + "» được thỏa mãn.");

        steps.add("=== [BƯỚC 2: Thực thi Base UC] ===");
        steps.addAll(buildApiSteps(baseUC, "valid"));

        steps.add("=== [BƯỚC 3: Extension Point — Điều kiện kích hoạt «" + extUC.getName() + "»] ===");
        steps.add("Xác nhận điều kiện extension đã được kích hoạt.");
        steps.addAll(buildApiSteps(extUC, "valid"));

        steps.add("=== [BƯỚC 4: Xác nhận kết quả tổng hợp] ===");
        steps.add("EXPECT_STATUS 200");
        steps.add("Kiểm tra luồng extension «" + extUC.getName() + "» đã được thực thi.");

        tc.setSteps(steps);
        return tc;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SINH BƯỚC HTTP
    // ═════════════════════════════════════════════════════════════════════════

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
            return buildSearchSteps(uc, dataMode);
        if (name.contains("delete") || name.contains("xóa") || name.contains("remove"))
            return buildDeleteSteps(uc, dataMode);
        if (name.contains("update") || name.contains("edit") || name.contains("sửa")
                || name.contains("cập nhật"))
            return buildUpdateSteps(uc, dataMode);
        if (name.contains("create") || name.contains("tạo") || name.contains("thêm")
                || name.contains("add") || name.contains("borrow") || name.contains("mượn")
                || name.contains("checkout") || name.contains("payment") || name.contains("thanh toán")
                || name.contains("send") || name.contains("gửi"))
            return buildCreateSteps(uc, dataMode);
        if (name.contains("view") || name.contains("xem") || name.contains("list")
                || name.contains("danh sách") || name.contains("get") || name.contains("manage")
                || name.contains("quản lý") || name.contains("history") || name.contains("lịch sử"))
            return buildViewSteps(uc, dataMode);
        if (name.contains("return") || name.contains("trả"))
            return buildReturnSteps(uc, dataMode);

        return buildGenericSteps(uc, dataMode);
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
                s.add("HEADER Authorization: Bearer invalid_token_xyz_000");
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
            // Sửa trong buildRegisterSteps(), case "valid"
            case "valid" -> {
                long ts = System.currentTimeMillis();
                s.add("SET_BODY {\"username\":\"newuser_" + ts + "\",\"email\":\"newuser"
                        + ts + "@example.com\",\"password\":\"StrongPass@123\"}");
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
                s.add("Register không yêu cầu auth — kiểm tra endpoint không bị chặn.");
                s.add("HTTP GET /api/auth/register");
                s.add("EXPECT_STATUS 405 hoặc 404 hoặc 403"); // thêm 403
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
                s.add("HEADER Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.valid_token");
                s.add("HTTP POST /api/auth/logout");
                s.add("EXPECT_STATUS 200");
                s.add("Xóa token khỏi client storage.");
            }
            case "invalid" -> {
                s.add("HEADER Authorization: Bearer invalid_token_xyz_123");
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

    // ── Create / POST ─────────────────────────────────────────────────────
    private List<String> buildCreateSteps(UseCase uc, String dataMode) {
        String endpoint = resolveEndpoint(uc);
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Lấy JWT token hợp lệ (đã đăng nhập).");
                s.add("INPUT: chuẩn bị dữ liệu HỢP LỆ đầy đủ các trường bắt buộc cho «" + uc.getName() + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("SET_BODY {\"name\":\"test_value\",\"description\":\"test description\"}");
                s.add("HTTP POST " + endpoint);
                s.add("EXPECT_STATUS 200 hoặc 201");
                s.add("EXPECT_BODY_FIELD data");
                s.add("Lưu ID vừa tạo để dùng trong các bước kiểm tra sau.");
            }
            case "invalid" -> {
                s.add("Lấy JWT token hợp lệ.");
                s.add("INPUT: dữ liệu THIẾU các trường bắt buộc / sai kiểu dữ liệu cho «" + uc.getName() + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("SET_BODY {}");
                s.add("HTTP POST " + endpoint);
                s.add("EXPECT_STATUS 400 hoặc 422");
                s.add("EXPECT_BODY_FIELD message");
            }
            case "no_auth" -> {
                s.add("SET_BODY {\"name\":\"test_value\"}");
                s.add("HTTP POST " + endpoint + " (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Update / PUT ──────────────────────────────────────────────────────
    private List<String> buildUpdateSteps(UseCase uc, String dataMode) {
        String endpoint = resolveEndpoint(uc);
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Chuẩn bị ID của resource cần cập nhật (đã tồn tại trong DB).");
                s.add("INPUT: dữ liệu cập nhật HỢP LỆ cho «" + uc.getName() + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("SET_BODY {\"name\":\"updated_value\",\"description\":\"updated desc\"}");
                s.add("HTTP PUT " + endpoint);
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY_FIELD data");
            }
            case "invalid" -> {
                s.add("Sử dụng ID KHÔNG tồn tại.");
                s.add("HEADER Authorization: Bearer {token}");
                // Đảm bảo endpoint có {id} nếu chưa có
                String ep = endpoint.endsWith("/{id}") ? endpoint : endpoint + "/00000000-0000-0000-0000-000000000000";
                s.add("HTTP PUT " + ep.replace("{id}", "00000000-0000-0000-0000-000000000000"));
                s.add("EXPECT_STATUS 404 hoặc 400");
            }
            case "no_auth" -> {
                s.add("HEADER Authorization: Bearer invalid_token_xyz_000");
                s.add("HTTP PUT " + endpoint + " (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Delete ─────────────────────────────────────────────────────────────
    private List<String> buildDeleteSteps(UseCase uc, String dataMode) {
        String endpoint = resolveEndpoint(uc);
        // Đảm bảo endpoint có /{id}
        String endpointWithId = endpoint.endsWith("/{id}") ? endpoint : endpoint + "/{id}";
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Chuẩn bị ID của resource cần xóa: «" + uc.getName() + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP DELETE " + endpointWithId.replace("{id}", "existing-resource-id"));
                s.add("EXPECT_STATUS 200 hoặc 204");
                s.add("HTTP GET " + endpointWithId.replace("{id}", "existing-resource-id") + " (kiểm tra đã xóa)");
                s.add("EXPECT_STATUS 404 — resource không còn tồn tại.");
            }
            case "invalid" -> {
                s.add("HTTP DELETE " + endpointWithId.replace("{id}", "nonexistent-id-000"));
                s.add("EXPECT_STATUS 404 hoặc 400");
            }
            case "no_auth" -> {
                s.add("HEADER Authorization: Bearer invalid_token_xyz_000");
                s.add("HTTP DELETE " + endpointWithId.replace("{id}", "some-id") + " (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── View / List / GET ──────────────────────────────────────────────────
    private List<String> buildViewSteps(UseCase uc, String dataMode) {
        String endpoint = resolveEndpoint(uc);
        // View/List không cần /{id}
        String listEndpoint = endpoint.endsWith("/{id}") ? endpoint.replace("/{id}", "") : endpoint;
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP GET " + listEndpoint);
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY_FIELD data");
                s.add("Kiểm tra cấu trúc dữ liệu trả về đúng với đặc tả: «" + uc.getName() + "».");
            }
            case "invalid" -> {
                s.add("HTTP GET " + listEndpoint + "/nonexistent-id-000");
                s.add("EXPECT_STATUS 404");
            }
            case "no_auth" -> {
                s.add("HEADER Authorization: Bearer invalid_token_xyz_000");
                s.add("HTTP GET " + listEndpoint);
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Search ─────────────────────────────────────────────────────────────
    private List<String> buildSearchSteps(UseCase uc, String dataMode) {
        String endpoint = resolveEndpoint(uc);
        String listEndpoint = endpoint.endsWith("/{id}") ? endpoint.replace("/{id}", "") : endpoint;
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("INPUT keyword = \"test\" (từ khóa hợp lệ, có kết quả).");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP GET " + listEndpoint + "?q=test");
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY_FIELD data");
                s.add("Kiểm tra kết quả liên quan đến keyword «test» cho: «" + uc.getName() + "».");
            }
            case "invalid" -> {
                s.add("INPUT keyword = \"\" (rỗng).");
                s.add("HTTP GET " + listEndpoint + "?q=");
                s.add("EXPECT_STATUS 200 (trả về toàn bộ) hoặc 400 (reject) — tuỳ business rule.");
            }
            case "no_auth" -> {
                s.add("HTTP GET " + listEndpoint + "?q=test (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
        }
        return s;
    }

    // ── Return (trả sách, trả hàng...) ────────────────────────────────────
    private List<String> buildReturnSteps(UseCase uc, String dataMode) {
        String endpoint = resolveEndpoint(uc);
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Chuẩn bị ID của bản ghi cần trả: «" + uc.getName() + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP POST " + endpoint);
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY_FIELD data");
                s.add("Xác nhận bản ghi đã được cập nhật trạng thái đã trả.");
            }
            case "invalid" -> {
                s.add("Sử dụng ID không tồn tại hoặc đã trả trước đó.");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP POST " + endpoint.replace("{id}", "nonexistent-id-000"));
                s.add("EXPECT_STATUS 404 hoặc 400");
            }
            case "no_auth" -> {
                s.add("HTTP POST " + endpoint + " (không có Authorization)");
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
                s.add("EXPECT_BODY_FIELD data");
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
                s.add("HTTP GET /api/export/00000000-0000-0000-0000-000000000000/excel");
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
    private List<String> buildGenericSteps(UseCase uc, String dataMode) {
        String method = resolveHttpMethod(uc);
        String endpoint = resolveEndpoint(uc);
        List<String> s = new ArrayList<>();
        switch (dataMode) {
            case "valid" -> {
                s.add("Chuẩn bị môi trường và dữ liệu HỢP LỆ cho: «" + uc.getName() + "».");
                s.add("INPUT: điền đầy đủ dữ liệu theo đặc tả chức năng.");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("SET_BODY {\"name\":\"test_value\",\"description\":\"test\"}");
                s.add("HTTP " + method + " " + endpoint);
                s.add("EXPECT_STATUS 200");
                s.add("EXPECT_BODY_FIELD data");
            }
            case "invalid" -> {
                s.add("Chuẩn bị dữ liệu KHÔNG hợp lệ / thiếu trường bắt buộc cho: «" + uc.getName() + "».");
                s.add("HEADER Authorization: Bearer {token}");
                s.add("HTTP " + method + " " + endpoint);
                s.add("EXPECT_STATUS 400 hoặc 422");
                s.add("EXPECT_BODY_FIELD message");
            }
            case "no_auth" -> {
                s.add("HTTP " + method + " " + endpoint + " (không có Authorization)");
                s.add("EXPECT_STATUS 401 hoặc 403");
            }
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