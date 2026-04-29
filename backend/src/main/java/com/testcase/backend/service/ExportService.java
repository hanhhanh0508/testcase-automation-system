package com.testcase.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.testcase.backend.entity.TestCase;
import com.testcase.backend.entity.UseCaseDiagram;
import com.testcase.backend.enums.TestCaseStatus;
import com.testcase.backend.repository.TestCaseRepository;
import com.testcase.backend.repository.UseCaseDiagramRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Xuất danh sách Test Case ra file Excel (.xlsx) hoặc PDF.
 *
 * Sử dụng:
 * - Apache POI 5.x → Excel
 * - OpenPDF 1.3.x → PDF (fork mã nguồn mở của iText 4)
 */
@Service
public class ExportService {

    private final TestCaseRepository testCaseRepository;
    private final UseCaseDiagramRepository diagramRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public ExportService(TestCaseRepository testCaseRepository,
            UseCaseDiagramRepository diagramRepository) {
        this.testCaseRepository = testCaseRepository;
        this.diagramRepository = diagramRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXCEL
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xuất test cases của một diagram ra file Excel.
     * 
     * @return byte[] nội dung file .xlsx
     */
    public byte[] exportToExcel(UUID diagramId) throws Exception {
        List<TestCase> testCases = testCaseRepository.findByUseCase_DiagramId(diagramId);
        UseCaseDiagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new RuntimeException("Diagram not found"));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // ── Sheet 1: Danh sách Test Case ──
            XSSFSheet sheet = workbook.createSheet("Test Cases");
            sheet.setColumnWidth(0, 3000); // Mã
            sheet.setColumnWidth(1, 10000); // Tên
            sheet.setColumnWidth(2, 4000); // Loại
            sheet.setColumnWidth(3, 3500); // Trạng thái
            sheet.setColumnWidth(4, 6000); // Use Case
            sheet.setColumnWidth(5, 14000); // Kết quả kỳ vọng
            sheet.setColumnWidth(6, 4000); // Ngày tạo

            // Title row
            XSSFRow titleRow = sheet.createRow(0);
            XSSFCell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO TEST CASE — " + diagram.getName().toUpperCase());
            titleCell.setCellStyle(buildTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
            titleRow.setHeightInPoints(30);

            // Sub-title
            XSSFRow subRow = sheet.createRow(1);
            subRow.createCell(0).setCellValue(
                    "Tổng: " + testCases.size() + "  |  Pass: " + countStatus(testCases, TestCaseStatus.PASSED)
                            + "  |  Fail: " + countStatus(testCases, TestCaseStatus.FAILED)
                            + "  |  Pending: " + countStatus(testCases, TestCaseStatus.PENDING));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

            // Header row
            String[] headers = { "Mã TC", "Tên Test Case", "Loại", "Trạng thái", "Use Case", "Kết quả kỳ vọng",
                    "Ngày tạo" };
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

                setCellValue(row, 4, tc.getUseCase() != null ? tc.getUseCase().getName() : "", dataStyle);
                setCellValue(row, 5, tc.getExpectedResult() != null ? tc.getExpectedResult() : "", dataStyle);
                setCellValue(row, 6, tc.getCreatedAt() != null ? tc.getCreatedAt().format(FMT) : "", dataStyle);
            }

            // ── Sheet 2: Chi tiết các bước ──
            XSSFSheet stepsSheet = workbook.createSheet("Chi tiết bước");
            stepsSheet.setColumnWidth(0, 3000);
            stepsSheet.setColumnWidth(1, 10000);
            stepsSheet.setColumnWidth(2, 3000);
            stepsSheet.setColumnWidth(3, 18000);

            XSSFRow stepsHeader = stepsSheet.createRow(0);
            String[] stepsHeaders = { "Mã TC", "Tên", "Bước #", "Nội dung bước" };
            CellStyle sh = buildHeaderStyle(workbook);
            for (int i = 0; i < stepsHeaders.length; i++) {
                XSSFCell c = stepsHeader.createCell(i);
                c.setCellValue(stepsHeaders[i]);
                c.setCellStyle(sh);
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
                    if (s == 0)
                        setCellValue(row, 1, tc.getName(), ds);
                    else
                        setCellValue(row, 1, "", ds);
                    setCellValue(row, 2, String.valueOf(s + 1), ds);
                    setCellValue(row, 3, steps.get(s), ds);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xuất test cases của một diagram ra file PDF.
     * 
     * @return byte[] nội dung file .pdf
     */
    public byte[] exportToPdf(UUID diagramId) throws Exception {
        List<TestCase> testCases = testCaseRepository.findByUseCase_DiagramId(diagramId);
        UseCaseDiagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new RuntimeException("Diagram not found"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 50, 36);

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    PdfContentByte cb = w.getDirectContent();
                    cb.beginText();
                    cb.setFontAndSize(BaseFont.createFont(), 8);
                    cb.showTextAligned(Element.ALIGN_CENTER,
                            "TestCase Gen — " + diagram.getName() + "  |  Trang " + w.getPageNumber(),
                            d.right() / 2 + d.left() / 2, d.bottom() - 15, 0);
                    cb.endText();
                }
            });

            doc.open();

            // ── Header ──
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.decode("#0d1117"));
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.decode("#57606a"));
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.decode("#0d1117"));
            Font codeFont = FontFactory.getFont(FontFactory.COURIER, 8, Color.decode("#0d1117"));

            Paragraph title = new Paragraph("BÁO CÁO TEST CASE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(4);
            doc.add(title);

            Paragraph sub = new Paragraph("Diagram: " + diagram.getName()
                    + "   |   Tổng: " + testCases.size()
                    + "   Pass: " + countStatus(testCases, TestCaseStatus.PASSED)
                    + "   Fail: " + countStatus(testCases, TestCaseStatus.FAILED)
                    + "   Pending: " + countStatus(testCases, TestCaseStatus.PENDING), subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(16);
            doc.add(sub);

            // ── Table ──
            float[] colWidths = { 60, 220, 70, 70, 120, 180, 70 };
            PdfPTable table = new PdfPTable(colWidths);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            String[] colNames = { "Mã TC", "Tên Test Case", "Loại", "Trạng thái", "Use Case", "Kết quả kỳ vọng",
                    "Ngày tạo" };
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
                addPdfCell(table, statusLabel(tc.getStatus()), getStatusFont(tc.getStatus()), statusBg(tc.getStatus()),
                        true);
                addPdfCell(table, tc.getUseCase() != null ? tc.getUseCase().getName() : "", dataFont, bg, false);
                addPdfCell(table, tc.getExpectedResult() != null ? tc.getExpectedResult() : "", dataFont, bg, false);
                addPdfCell(table, tc.getCreatedAt() != null ? tc.getCreatedAt().format(FMT) : "", codeFont, bg, true);
            }

            doc.add(table);

            // ── Chi tiết bước (tóm tắt) ──
            doc.newPage();
            Paragraph stepsTitle = new Paragraph("CHI TIẾT CÁC BƯỚC THỰC HIỆN", titleFont);
            stepsTitle.setAlignment(Element.ALIGN_CENTER);
            stepsTitle.setSpacingAfter(16);
            doc.add(stepsTitle);

            for (TestCase tc : testCases) {
                if (tc.getSteps() == null || tc.getSteps().isEmpty())
                    continue;

                Font tcTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.decode("#2563eb"));
                Paragraph tcHead = new Paragraph(tc.getTcCode() + " — " + tc.getName(), tcTitle);
                tcHead.setSpacingBefore(12);
                tcHead.setSpacingAfter(4);
                doc.add(tcHead);

                com.lowagie.text.List stepList = new com.lowagie.text.List(com.lowagie.text.List.ORDERED);
                stepList.setIndentationLeft(16);
                for (String step : tc.getSteps()) {
                    if (step.startsWith("===") || step.startsWith("---")) {
                        Font secFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.decode("#57606a"));
                        ListItem item = new ListItem(new Phrase(step, secFont));
                        item.setListSymbol("  ");
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

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS — Excel styles
    // ─────────────────────────────────────────────────────────────────────────

    private CellStyle buildTitleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        f.setColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
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
        switch (status) {
            case PASSED -> {
                f.setColor(IndexedColors.DARK_GREEN.getIndex());
            }
            case FAILED -> {
                f.setColor(IndexedColors.RED.getIndex());
            }
            case RUNNING -> {
                f.setColor(IndexedColors.DARK_BLUE.getIndex());
            }
            default -> {
                f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            }
        }
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(false);
        return s;
    }

    private void setCellValue(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS — PDF
    // ─────────────────────────────────────────────────────────────────────────

    private void addPdfCell(PdfPTable table, String text, Font font, Color bg, boolean center) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setBorderColor(Color.decode("#e2e8f0"));
        cell.setHorizontalAlignment(center ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private Font getStatusFont(TestCaseStatus status) {
        return switch (status) {
            case PASSED -> FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.decode("#16a34a"));
            case FAILED -> FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.decode("#dc2626"));
            case RUNNING -> FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.decode("#2563eb"));
            default -> FontFactory.getFont(FontFactory.HELVETICA, 9, Color.decode("#64748b"));
        };
    }

    private Color statusBg(TestCaseStatus status) {
        return switch (status) {
            case PASSED -> Color.decode("#dcfce7");
            case FAILED -> Color.decode("#fee2e2");
            case RUNNING -> Color.decode("#dbeafe");
            default -> Color.decode("#f1f5f9");
        };
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