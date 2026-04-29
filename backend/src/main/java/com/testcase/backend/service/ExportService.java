package com.testcase.backend.service;

// Import thẳng Font của PDF
// --- Thư viện PDF (OpenPDF) ---
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.apache.poi.ss.util.CellRangeAddress;
// --- Thư viện Excel (Apache POI) ---
import org.apache.poi.ss.usermodel.Row; // Import thẳng Row của Excel
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.xssf.usermodel.*;

// --- Thư viện Hệ thống & Java ---
import java.awt.Color; // Import thẳng Color của Java AWT
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

// Các repository và entity của bạn...
import com.testcase.backend.entity.TestCase;
import com.testcase.backend.entity.UseCaseDiagram;
import com.testcase.backend.enums.TestCaseStatus;
import com.testcase.backend.repository.TestCaseRepository;
import com.testcase.backend.repository.UseCaseDiagramRepository;

/**
 * Xuất danh sách Test Case ra file Excel (.xlsx) hoặc PDF.
 *
 * Thư viện sử dụng:
 * - Apache POI 5.x → Excel .xlsx
 * - OpenPDF 1.3.x → PDF (open-source fork của iText 4)
 *
 * Đây là thư viện lập trình thuần túy — không phải "tool" kiểm thử.
 * Tương đương với việc tự viết code để xuất báo cáo.
 */
@Service
public class ExportService {

    private final TestCaseRepository testCaseRepository;
    private final UseCaseDiagramRepository diagramRepository;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public ExportService(TestCaseRepository testCaseRepository, UseCaseDiagramRepository diagramRepository) {
        this.testCaseRepository = testCaseRepository;
        this.diagramRepository = diagramRepository;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // EXCEL — Apache POI
    // ═════════════════════════════════════════════════════════════════════════

    public byte[] exportToExcel(UUID diagramId) throws Exception {
        List<TestCase> testCases = testCaseRepository.findByUseCase_DiagramId(diagramId);
        UseCaseDiagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new RuntimeException("Diagram not found: " + diagramId));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // ── Sheet 1: Danh sách Test Case ──────────────────────────────
            XSSFSheet sheet = workbook.createSheet("Test Cases");
            sheet.setColumnWidth(0, 3200); // Mã TC
            sheet.setColumnWidth(1, 12000); // Tên
            sheet.setColumnWidth(2, 4000); // Loại
            sheet.setColumnWidth(3, 3800); // Trạng thái
            sheet.setColumnWidth(4, 7000); // Use Case
            sheet.setColumnWidth(5, 14000); // Kết quả kỳ vọng
            sheet.setColumnWidth(6, 4200); // Ngày tạo

            // Title row
            XSSFRow titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(32);
            XSSFCell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO TEST CASE — " + diagram.getName().toUpperCase());
            titleCell.setCellStyle(buildTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

            // Sub-title (stats)
            XSSFRow subRow = sheet.createRow(1);
            subRow.setHeightInPoints(16);
            XSSFCell subCell = subRow.createCell(0);
            subCell.setCellValue(
                    "Tổng: " + testCases.size()
                            + "  |  Pass: " + countStatus(testCases, TestCaseStatus.PASSED)
                            + "  |  Fail: " + countStatus(testCases, TestCaseStatus.FAILED)
                            + "  |  Pending: " + countStatus(testCases, TestCaseStatus.PENDING)
                            + "  |  Xuất ngày: "
                            + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            subCell.setCellStyle(buildSubtitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

            // Header row
            String[] headers = { "Mã TC", "Tên Test Case", "Loại", "Trạng thái",
                    "Use Case", "Kết quả kỳ vọng", "Ngày tạo" };
            XSSFRow headerRow = sheet.createRow(2);
            CellStyle headerStyle = buildHeaderStyle(workbook);
            for (int i = 0; i < headers.length; i++) {
                XSSFCell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowNum = 3;
            for (TestCase tc : testCases) {
                XSSFRow row = sheet.createRow(rowNum++);
                CellStyle dataStyle = buildDataStyle(workbook, rowNum % 2 == 0);
                CellStyle statusStyle = buildStatusStyle(workbook, tc.getStatus());

                setCellValue(row, 0, tc.getTcCode(), dataStyle);
                setCellValue(row, 1, tc.getName(), dataStyle);
                setCellValue(row, 2, typeLabel(tc.getTestType()), dataStyle);

                Cell statusCell = row.createCell(3);
                statusCell.setCellValue(statusLabel(tc.getStatus()));
                statusCell.setCellStyle(statusStyle);

                setCellValue(row, 4,
                        tc.getUseCase() != null ? tc.getUseCase().getName() : "", dataStyle);
                setCellValue(row, 5,
                        tc.getExpectedResult() != null ? tc.getExpectedResult() : "", dataStyle);
                setCellValue(row, 6,
                        tc.getCreatedAt() != null ? tc.getCreatedAt().format(FMT) : "", dataStyle);
            }

            // ── Sheet 2: Chi tiết các bước ────────────────────────────────
            XSSFSheet stepsSheet = workbook.createSheet("Chi tiết bước");
            stepsSheet.setColumnWidth(0, 3200);
            stepsSheet.setColumnWidth(1, 10000);
            stepsSheet.setColumnWidth(2, 3000);
            stepsSheet.setColumnWidth(3, 20000);

            String[] stepsHeaders = { "Mã TC", "Tên Test Case", "Bước #", "Nội dung bước" };
            XSSFRow stepsHeader = stepsSheet.createRow(0);
            CellStyle shStyle = buildHeaderStyle(workbook);
            for (int i = 0; i < stepsHeaders.length; i++) {
                XSSFCell c = stepsHeader.createCell(i);
                c.setCellValue(stepsHeaders[i]);
                c.setCellStyle(shStyle);
            }

            int stepRow = 1;
            for (TestCase tc : testCases) {
                List<String> steps = tc.getSteps();
                if (steps == null || steps.isEmpty())
                    continue;
                for (int s = 0; s < steps.size(); s++) {
                    XSSFRow row = stepsSheet.createRow(stepRow++);
                    CellStyle ds = buildDataStyle(workbook, stepRow % 2 == 0);
                    setCellValue(row, 0, tc.getTcCode(), ds);
                    setCellValue(row, 1, s == 0 ? tc.getName() : "", ds);
                    setCellValue(row, 2, String.valueOf(s + 1), ds);
                    setCellValue(row, 3, steps.get(s), ds);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PDF — OpenPDF (open-source iText fork)
    // ═════════════════════════════════════════════════════════════════════════

    public byte[] exportToPdf(UUID diagramId) throws Exception {
        List<TestCase> testCases = testCaseRepository.findByUseCase_DiagramId(diagramId);
        UseCaseDiagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new RuntimeException("Diagram not found: " + diagramId));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 50, 36);

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            // Header/Footer trên mỗi trang
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    try {
                        PdfContentByte cb = w.getDirectContent();
                        BaseFont bf = BaseFont.createFont(
                                BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                        cb.beginText();
                        cb.setFontAndSize(bf, 8);
                        cb.setColorFill(Color.decode("#8b949e"));
                        cb.showTextAligned(Element.ALIGN_CENTER,
                                "TestCase Gen — " + diagram.getName()
                                        + "  |  Trang " + w.getPageNumber()
                                        + "  |  Xuất: " + java.time.LocalDate.now()
                                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                (d.right() + d.left()) / 2,
                                d.bottom() - 15, 0);
                        cb.endText();
                    } catch (Exception ignored) {
                    }
                }
            });

            doc.open();

            // ── Tiêu đề chính ─────────────────────────────────────────────
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16,
                    Color.decode("#0d1117"));
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10,
                    Color.decode("#57606a"));
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9,
                    Color.WHITE);
            Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 9,
                    Color.decode("#0d1117"));
            Font codeFont = FontFactory.getFont(FontFactory.COURIER, 8,
                    Color.decode("#0d1117"));

            Paragraph title = new Paragraph("BÁO CÁO TEST CASE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(4);
            doc.add(title);

            Paragraph sub = new Paragraph(
                    "Diagram: " + diagram.getName()
                            + "   |   Tổng: " + testCases.size()
                            + "   Pass: " + countStatus(testCases, TestCaseStatus.PASSED)
                            + "   Fail: " + countStatus(testCases, TestCaseStatus.FAILED)
                            + "   Pending: " + countStatus(testCases, TestCaseStatus.PENDING)
                            + "   |   Engine: RestTemplate HTTP (Library-based, không dùng Selenium)",
                    subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(16);
            doc.add(sub);

            // ── Bảng Test Case ────────────────────────────────────────────
            float[] colWidths = { 60f, 220f, 70f, 70f, 120f, 190f, 70f };
            PdfPTable table = new PdfPTable(colWidths);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            String[] colNames = { "Mã TC", "Tên Test Case", "Loại", "Trạng thái",
                    "Use Case", "Kết quả kỳ vọng", "Ngày tạo" };
            Color headerBg = Color.decode("#0d1117");
            for (String col : colNames) {
                PdfPCell cell = new PdfPCell(new Phrase(col, headerFont));
                cell.setBackgroundColor(headerBg);
                cell.setPadding(6);
                cell.setBorderColor(Color.decode("#30363d"));
                table.addCell(cell);
            }

            boolean alt = false;
            for (TestCase tc : testCases) {
                Color bg = alt ? Color.decode("#f8fafc") : Color.WHITE;
                alt = !alt;

                addPdfCell(table, tc.getTcCode(), codeFont, bg, true);
                addPdfCell(table, tc.getName(), dataFont, bg, false);
                addPdfCell(table, typeLabel(tc.getTestType()), dataFont, bg, true);
                addPdfCell(table, statusLabel(tc.getStatus()),
                        getStatusFont(tc.getStatus()), statusBg(tc.getStatus()), true);
                addPdfCell(table,
                        tc.getUseCase() != null ? tc.getUseCase().getName() : "",
                        dataFont, bg, false);
                addPdfCell(table,
                        tc.getExpectedResult() != null ? tc.getExpectedResult() : "",
                        dataFont, bg, false);
                addPdfCell(table,
                        tc.getCreatedAt() != null ? tc.getCreatedAt().format(FMT) : "",
                        codeFont, bg, true);
            }
            doc.add(table);

            // ── Trang 2: Chi tiết các bước ────────────────────────────────
            doc.newPage();
            Paragraph stepsTitle = new Paragraph("CHI TIẾT CÁC BƯỚC THỰC HIỆN", titleFont);
            stepsTitle.setAlignment(Element.ALIGN_CENTER);
            stepsTitle.setSpacingAfter(16);
            doc.add(stepsTitle);

            // Ghi chú về engine
            Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9,
                    Color.decode("#2563eb"));
            Paragraph engineNote = new Paragraph(
                    "Ghi chú: Các bước được thực thi bằng RestTemplate HTTP Client "
                            + "(Spring Framework Library). Không sử dụng Selenium hay công cụ kiểm thử tự động.",
                    noteFont);
            engineNote.setAlignment(Element.ALIGN_CENTER);
            engineNote.setSpacingAfter(14);
            doc.add(engineNote);

            for (TestCase tc : testCases) {
                if (tc.getSteps() == null || tc.getSteps().isEmpty())
                    continue;

                Font tcTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10,
                        Color.decode("#2563eb"));
                Paragraph tcHead = new Paragraph(
                        tc.getTcCode() + " — " + tc.getName(), tcTitleFont);
                tcHead.setSpacingBefore(12);
                tcHead.setSpacingAfter(4);
                doc.add(tcHead);

                com.lowagie.text.List stepList = new com.lowagie.text.List(com.lowagie.text.List.ORDERED);
                stepList.setIndentationLeft(16);

                for (String step : tc.getSteps()) {
                    if (step.startsWith("===") || step.startsWith("---")) {
                        Font secFont = FontFactory.getFont(
                                FontFactory.HELVETICA_BOLD, 8, Color.decode("#57606a"));
                        ListItem item = new ListItem(new Phrase(step, secFont));
                        // Thay vì: item.setListSymbol(" ");
                        item.setListSymbol(new com.lowagie.text.Chunk("  "));
                        stepList.add(item);
                    } else {
                        stepList.add(new ListItem(new Phrase(step, dataFont)));
                    }
                }
                doc.add(stepList);
            }

        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HELPERS — Excel styles
    // ═════════════════════════════════════════════════════════════════════════

    private CellStyle buildTitleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        f.setColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle buildSubtitleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle buildHeaderStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setWrapText(true);
        return s;
    }

    private CellStyle buildDataStyle(XSSFWorkbook wb, boolean alt) {
        CellStyle s = wb.createCellStyle();
        if (alt) {
            s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        s.setBorderBottom(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setWrapText(true);
        s.setVerticalAlignment(VerticalAlignment.TOP);
        return s;
    }

    private CellStyle buildStatusStyle(XSSFWorkbook wb, TestCaseStatus status) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 9);

        // Sử dụng Switch cũ nếu Java < 17, hoặc giữ nguyên nếu Java >= 17
        switch (status) {
            case PASSED:
                f.setColor(IndexedColors.DARK_GREEN.getIndex());
                break;
            case FAILED:
                f.setColor(IndexedColors.RED.getIndex());
                break;
            case RUNNING:
                f.setColor(IndexedColors.DARK_BLUE.getIndex());
                break;
            default:
                f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        }
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private void setCellValue(org.apache.poi.ss.usermodel.Row row, int col, String value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS — PDF
    // ─────────────────────────────────────────────────────────────────────────

    private void addPdfCell(PdfPTable table, String text, com.lowagie.text.Font font, java.awt.Color bg,
            boolean center) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        if (bg != null) {
            cell.setBackgroundColor(bg);
        }
        cell.setPadding(5);
        cell.setBorderColor(new java.awt.Color(226, 232, 240)); // Thay Color.decode nếu lỗi
        cell.setHorizontalAlignment(center ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private com.lowagie.text.Font getStatusFont(TestCaseStatus status) {
        java.awt.Color color;
        switch (status) {
            case PASSED:
                color = new java.awt.Color(22, 163, 74);
                break;
            case FAILED:
                color = new java.awt.Color(220, 38, 38);
                break;
            case RUNNING:
                color = new java.awt.Color(37, 99, 235);
                break;
            default:
                color = new java.awt.Color(100, 116, 139);
        }
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, color);
    }

    private java.awt.Color statusBg(TestCaseStatus status) {
        switch (status) {
            case PASSED:
                return new java.awt.Color(220, 252, 231);
            case FAILED:
                return new java.awt.Color(254, 226, 230);
            case RUNNING:
                return new java.awt.Color(219, 234, 254);
            default:
                return new java.awt.Color(241, 245, 249);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LABELS
    // ─────────────────────────────────────────────────────────────────────────

    private String typeLabel(com.testcase.backend.enums.TestType type) {
        if (type == null)
            return "";
        return switch (type) {
            case HAPPY_PATH -> "Happy Path";
            case NEGATIVE -> "Negative";
            case BOUNDARY -> "Boundary";
        };
    }

    private String statusLabel(TestCaseStatus s) {
        if (s == null)
            return "";
        return switch (s) {
            case PASSED -> "PASSED ✓";
            case FAILED -> "FAILED ✗";
            case RUNNING -> "RUNNING";
            case PENDING -> "Pending";
        };
    }

    private long countStatus(List<TestCase> list, TestCaseStatus status) {
        return list.stream().filter(tc -> tc.getStatus() == status).count();
    }
}