package com.testcase.backend.service;

import com.testcase.backend.entity.TestCase;
import com.testcase.backend.entity.TestResult;
import com.testcase.backend.enums.TestCaseStatus;
import com.testcase.backend.enums.TestOutcome;
import com.testcase.backend.repository.TestCaseRepository;
import com.testcase.backend.repository.TestResultRepository;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Thực thi test case bằng Selenium WebDriver.
 *
 * Luồng:
 * 1. Nhận TestCase (có steps dạng text)
 * 2. Tạo ChromeDriver (headless)
 * 3. Parse từng step → gọi Selenium action tương ứng
 * 4. Lưu TestResult vào DB, cập nhật status TestCase
 */
@Service
public class SeleniumExecutorService {

    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;

    // URL gốc của app đang được test (cấu hình trong application.properties)
    @Value("${selenium.target.baseUrl:http://localhost:5173}")
    private String baseUrl;

    @Value("${selenium.timeout.seconds:10}")
    private int timeoutSeconds;

    public SeleniumExecutorService(TestCaseRepository testCaseRepository,
            TestResultRepository testResultRepository) {
        this.testCaseRepository = testCaseRepository;
        this.testResultRepository = testResultRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: chạy 1 test case
    // ─────────────────────────────────────────────────────────────────────────

    public TestResult execute(UUID testCaseId) {
        TestCase tc = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new RuntimeException("TestCase không tồn tại: " + testCaseId));

        // Đánh dấu RUNNING
        tc.setStatus(TestCaseStatus.RUNNING);
        testCaseRepository.save(tc);

        long startMs = System.currentTimeMillis();
        WebDriver driver = null;

        try {
            driver = buildDriver();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

            // Chạy từng step
            List<String> steps = tc.getSteps();
            StringBuilder log = new StringBuilder();

            for (int i = 0; i < steps.size(); i++) {
                String step = steps.get(i).trim();
                log.append("[Step ").append(i + 1).append("] ").append(step).append("\n");
                executeStep(driver, wait, step, log);
            }

            // Kiểm tra expected result nếu có
            String expected = tc.getExpectedResult();
            if (expected != null && !expected.isBlank()) {
                verifyExpectedResult(driver, expected, log);
            }

            // PASS
            long durationMs = System.currentTimeMillis() - startMs;
            tc.setStatus(TestCaseStatus.PASSED);
            testCaseRepository.save(tc);
            return saveResult(tc, TestOutcome.PASSED, log.toString(), null, (int) durationMs);

        } catch (AssertionError ae) {
            long durationMs = System.currentTimeMillis() - startMs;
            tc.setStatus(TestCaseStatus.FAILED);
            testCaseRepository.save(tc);
            return saveResult(tc, TestOutcome.FAILED, null, "Assertion failed: " + ae.getMessage(), (int) durationMs);

        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startMs;
            tc.setStatus(TestCaseStatus.FAILED);
            testCaseRepository.save(tc);
            return saveResult(tc, TestOutcome.ERROR, null, ex.getMessage(), (int) durationMs);

        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: chạy nhiều test case tuần tự, trả về map id → result
    // ─────────────────────────────────────────────────────────────────────────

    public Map<UUID, TestResult> executeBatch(List<UUID> ids) {
        Map<UUID, TestResult> results = new LinkedHashMap<>();
        for (UUID id : ids) {
            try {
                results.put(id, execute(id));
            } catch (Exception ex) {
                // Ghi lỗi nhưng tiếp tục chạy các TC còn lại
                TestCase tc = testCaseRepository.findById(id).orElse(null);
                if (tc != null) {
                    tc.setStatus(TestCaseStatus.FAILED);
                    testCaseRepository.save(tc);
                    results.put(id, saveResult(tc, TestOutcome.ERROR, null, ex.getMessage(), 0));
                }
            }
        }
        return results;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Tạo ChromeDriver headless */
    private WebDriver buildDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new", // headless mode Chrome 112+
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1280,800",
                "--disable-extensions");
        return new ChromeDriver(options);
    }

    /**
     * Parse step text → Selenium action.
     *
     * Định dạng step được nhận dạng (không phân biệt hoa thường):
     * "Mở trang /login" → navigate to baseUrl + path
     * "Mở trang http://..." → navigate trực tiếp
     * "Nhập {selector} = {value}" → sendKeys
     * "Click {selector}" → click
     * "Chờ {selector}" → waitForVisible
     * "Kiểm tra {selector} chứa {text}" → assertContains
     * Các bước text mô tả khác → log rồi bỏ qua (không crash)
     */
    private void executeStep(WebDriver driver, WebDriverWait wait,
            String step, StringBuilder log) {

        String lower = step.toLowerCase();

        // ── Navigate ──
        if (lower.startsWith("mở trang") || lower.startsWith("navigate to") || lower.startsWith("go to")) {
            String url = extractAfterKeyword(step, "mở trang", "navigate to", "go to").trim();
            if (!url.startsWith("http")) {
                url = baseUrl + (url.startsWith("/") ? url : "/" + url);
            }
            driver.get(url);
            log.append("  → navigate: ").append(url).append("\n");
            return;
        }

        // ── Fill/Input ──
        if (lower.contains("nhập") || lower.contains("điền") || lower.contains("input")) {
            // Pattern: "Nhập <selector> = <value>" hoặc "Nhập <selector> là <value>"
            String[] parts = step.split("=|\\blà\\b", 2);
            if (parts.length == 2) {
                String selector = extractSelector(parts[0]);
                String value = parts[1].trim();
                WebElement el = findElement(driver, wait, selector);
                el.clear();
                el.sendKeys(value);
                log.append("  → sendKeys [").append(selector).append("] = \"").append(value).append("\"\n");
            } else {
                log.append("  ⚠ Bỏ qua step (không parse được selector): ").append(step).append("\n");
            }
            return;
        }

        // ── Click ──
        if (lower.startsWith("click") || lower.startsWith("nhấn") || lower.startsWith("bấm")) {
            String selector = extractSelector(step.replaceFirst("(?i)click|nhấn|bấm", "").trim());
            WebElement el = findElement(driver, wait, selector);
            el.click();
            log.append("  → click [").append(selector).append("]\n");
            return;
        }

        // ── Wait/Assert visible ──
        if (lower.startsWith("chờ") || lower.startsWith("wait for")) {
            String selector = extractSelector(step.replaceFirst("(?i)chờ|wait for", "").trim());
            findElement(driver, wait, selector);
            log.append("  → waitVisible [").append(selector).append("]\n");
            return;
        }

        // ── Assert text ──
        if (lower.contains("kiểm tra") || lower.contains("verify") || lower.contains("assert")) {
            String[] parts = step.split("(?i)chứa|contains|có text", 2);
            if (parts.length == 2) {
                String selector = extractSelector(parts[0].replaceAll("(?i)kiểm tra|verify|assert", "").trim());
                String expected = parts[1].trim().replaceAll("^\"|\"$", "");
                WebElement el = findElement(driver, wait, selector);
                String actual = el.getText();
                if (!actual.toLowerCase().contains(expected.toLowerCase())) {
                    throw new AssertionError(
                            "Expected [" + selector + "] contains \"" + expected
                                    + "\" but got \"" + actual + "\"");
                }
                log.append("  ✓ assert [").append(selector).append("] contains \"").append(expected).append("\"\n");
            } else {
                log.append("  ⚠ Bỏ qua assert (không parse được): ").append(step).append("\n");
            }
            return;
        }

        // ── Submit form ──
        if (lower.contains("submit") || lower.contains("gửi form")) {
            String selector = extractSelector(step.replaceAll("(?i)submit|gửi form", "").trim());
            if (!selector.isBlank()) {
                WebElement el = findElement(driver, wait, selector);
                el.submit();
                log.append("  → submit [").append(selector).append("]\n");
            } else {
                log.append("  ⚠ Bỏ qua submit (không tìm được selector)\n");
            }
            return;
        }

        // ── Fallthrough: step mô tả không có action ──
        log.append("  ℹ (mô tả) ").append(step).append("\n");
    }

    /** Verify expected result text trên trang hiện tại */
    private void verifyExpectedResult(WebDriver driver, String expected, StringBuilder log) {
        String pageText = driver.findElement(By.tagName("body")).getText();
        // Lấy từ khóa quan trọng từ expected (bỏ stop words)
        String keyword = extractKeyword(expected);
        if (!keyword.isBlank() && !pageText.toLowerCase().contains(keyword.toLowerCase())) {
            throw new AssertionError(
                    "Expected result \"" + keyword + "\" không tìm thấy trên trang.\n"
                            + "Page text (200 chars): " + pageText.substring(0, Math.min(200, pageText.length())));
        }
        log.append("  ✓ expected result verified: \"").append(keyword).append("\"\n");
    }

    /** Tìm element theo CSS selector hoặc XPath */
    private WebElement findElement(WebDriver driver, WebDriverWait wait, String selector) {
        By by;
        if (selector.startsWith("//") || selector.startsWith("(//")) {
            by = By.xpath(selector);
        } else if (selector.startsWith("#")) {
            by = By.id(selector.substring(1));
        } else {
            by = By.cssSelector(selector);
        }
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    /** Lấy phần sau keyword đầu tiên khớp */
    private String extractAfterKeyword(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            int idx = lower.indexOf(kw.toLowerCase());
            if (idx >= 0)
                return text.substring(idx + kw.length()).trim();
        }
        return text;
    }

    /**
     * Trích selector từ text.
     * Ưu tiên nội dung trong ngoặc [] hoặc "", còn lại trim dùng nguyên.
     * Ví dụ: "Nhập [#email]" → "#email"
     * "Click nút 'Đăng nhập'" → "Đăng nhập" → By.linkText fallback
     */
    private String extractSelector(String text) {
        // [selector]
        if (text.contains("[") && text.contains("]")) {
            return text.substring(text.indexOf('[') + 1, text.lastIndexOf(']')).trim();
        }
        // "selector" hoặc 'selector'
        if ((text.startsWith("\"") && text.endsWith("\"")) ||
                (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        return text.trim();
    }

    /** Lấy keyword ngắn từ expected result để verify */
    private String extractKeyword(String expected) {
        // Lấy phần sau "thành công", "hiển thị", "xuất hiện", v.v.
        for (String kw : new String[] { "thành công", "hiển thị", "xuất hiện", "success", "thông báo" }) {
            int idx = expected.toLowerCase().indexOf(kw);
            if (idx >= 0) {
                String after = expected.substring(idx + kw.length()).trim();
                if (!after.isBlank())
                    return after.split("[,.]")[0].trim();
                return kw;
            }
        }
        // Lấy từ cuối câu
        String[] words = expected.trim().split("\\s+");
        if (words.length > 0)
            return words[words.length - 1].replaceAll("[.,!?]", "");
        return "";
    }

    private TestResult saveResult(TestCase tc, TestOutcome outcome,
            String actualResult, String errorMsg, int durationMs) {
        TestResult r = new TestResult();
        r.setTestCase(tc);
        r.setOutcome(outcome);
        r.setActualResult(actualResult);
        r.setErrorMessage(errorMsg);
        r.setDurationMs(durationMs);
        return testResultRepository.save(r);
    }
}