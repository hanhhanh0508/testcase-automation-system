package com.testcase.backend.controller;

import com.testcase.backend.service.ExportService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller xuất dữ liệu Test Case.
 *
 * GET /api/export/{diagramId}/excel → file .xlsx
 * GET /api/export/{diagramId}/pdf → file .pdf
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/{diagramId}/excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable UUID diagramId) {
        try {
            byte[] data = exportService.exportToExcel(diagramId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename("testcases-" + diagramId.toString().substring(0, 8) + ".xlsx")
                            .build());
            headers.setContentLength(data.length);

            return ResponseEntity.ok().headers(headers).body(data);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{diagramId}/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID diagramId) {
        try {
            byte[] data = exportService.exportToPdf(diagramId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename("testcases-" + diagramId.toString().substring(0, 8) + ".pdf")
                            .build());
            headers.setContentLength(data.length);

            return ResponseEntity.ok().headers(headers).body(data);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}