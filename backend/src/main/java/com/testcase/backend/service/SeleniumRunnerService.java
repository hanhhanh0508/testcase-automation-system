package com.testcase.backend.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class SeleniumRunnerService {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.test.username:testuser}")
    private String username;

    @Value("${app.test.password:Password@123}")
    private String password;

    public void runAndPrompt(String diagramId, List<String> testCaseIds) {
        // Setup Chrome
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless"); // bỏ comment nếu muốn chạy nền
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        try {
            // ── Bước 1: Đăng nhập ──────────────────────────────
            driver.get(frontendUrl + "/login");

            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("input[autocomplete='username']")));

            driver.findElement(By.cssSelector("input[autocomplete='username']"))
                    .sendKeys(username);
            driver.findElement(By.cssSelector("input[autocomplete='current-password']"))
                    .sendKeys(password);
            driver.findElement(By.cssSelector("button.auth-btn")).click();

            // ── Bước 2: Vào trang /testcases, chọn diagram ─────
            wait.until(ExpectedConditions.urlContains("/upload"));
            driver.get(frontendUrl + "/testcases");

            // Chờ dropdown diagram load
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("select.diagram-select")));

            // Chọn đúng diagram
            WebElement diagramSelect = driver.findElement(
                    By.cssSelector("select.diagram-select"));
            new org.openqa.selenium.support.ui.Select(diagramSelect)
                    .selectByValue(diagramId);

            Thread.sleep(1000); // chờ load test cases

            // ── Bước 3: Click "Chạy tất cả →" ─────────────────
            WebElement runBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(),'Chạy tất cả')]")));
            runBtn.click();

            // ── Bước 4: Chờ vào trang /run ─────────────────────
            wait.until(ExpectedConditions.urlContains("/run"));
            Thread.sleep(1000);

            // ── Bước 5: Click nút "Bắt đầu" ────────────────────
            WebElement startBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(),'Bắt đầu')]")));
            startBtn.click();

            // ── Bước 6: Chờ hoàn thành ─────────────────────────
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//span[contains(text(),'All Passed')]")),
                    ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//span[contains(text(),'Failed')]"))));

            // ── Bước 7: Hiện popup hỏi client ──────────────────
            boolean continueExport = showPromptDialog(driver);

            if (continueExport) {
                // Click Export JSON report
                WebElement exportBtn = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[contains(text(),'Export JSON report')]")));
                exportBtn.click();
                Thread.sleep(2000); // chờ download
                showAlertDialog(driver, "✅ Đã export file thành công!");
            } else {
                showAlertDialog(driver, "⏹ Đã dừng. Cảm ơn!");
            }

        } catch (Exception e) {
            System.err.println("SeleniumRunner error: " + e.getMessage());
        } finally {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
            driver.quit();
        }
    }

    /**
     * Hiện JavaScript confirm dialog:
     * "Chạy xong! Bạn muốn export file không?"
     * OK = export, Cancel = dừng
     */
    private boolean showPromptDialog(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Object result = js.executeScript(
                "return window.confirm('✅ Chạy test hoàn thành!\\n\\n" +
                        "Nhấn OK để Export file báo cáo.\\n" +
                        "Nhấn Cancel để kết thúc.');");
        return Boolean.TRUE.equals(result);
    }

    /**
     * Hiện JavaScript alert thông báo
     */
    private void showAlertDialog(WebDriver driver, String message) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.alert('" + message + "');");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.alertIsPresent());
            driver.switchTo().alert().accept();
        } catch (Exception ignored) {
        }
    }
}