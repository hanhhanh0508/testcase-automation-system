package com.testcase.backend.controller;

import com.testcase.backend.dto.ApiResponseDTO;
import com.testcase.backend.dto.UseCaseInputDTO;
import com.testcase.backend.entity.UseCaseDiagram;
import com.testcase.backend.service.DiagramService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.testcase.backend.service.TestCaseGeneratorService;
import java.util.List;

import java.util.UUID;

@RestController
@RequestMapping("/api/diagrams")
public class DiagramController {

   private final DiagramService diagramService;
   private final TestCaseGeneratorService generatorService;

    public DiagramController(DiagramService diagramService,
                            TestCaseGeneratorService generatorService) {
        this.diagramService = diagramService;
        this.generatorService = generatorService;
    }

    // POST /api/diagrams/text — nhận use case dạng text
    @PostMapping("/text")
    public ResponseEntity<ApiResponseDTO<UseCaseDiagram>> receiveText(
            @RequestBody UseCaseInputDTO dto) {
        try {
            UseCaseDiagram diagram = diagramService.createFromText(dto);
            return ResponseEntity.ok(
                    ApiResponseDTO.ok("Nhận use case thành công", diagram));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    ApiResponseDTO.error("Lỗi: " + e.getMessage()));
        }
    }

    // POST /api/diagrams/upload — nhận use case dạng file
    @PostMapping("/upload")
    public ResponseEntity<ApiResponseDTO<UseCaseDiagram>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name) {
        try {
            UseCaseDiagram diagram = diagramService.createFromFile(file, name);
            return ResponseEntity.ok(
                    ApiResponseDTO.ok("Upload file thành công", diagram));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    ApiResponseDTO.error("Lỗi upload: " + e.getMessage()));
        }
    }
    // POST /api/diagrams/{id}/generate — sinh test case
    @PostMapping("/{id}/generate")
    public ResponseEntity<ApiResponseDTO<List<com.testcase.backend.entity.TestCase>>> generateTestCases(
            @PathVariable UUID id) {
        try {
            List<com.testcase.backend.entity.TestCase> testCases = generatorService.generateForDiagram(id);
            return ResponseEntity.ok(
                    ApiResponseDTO.ok("Sinh " + testCases.size() + " test case thành công", testCases));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    ApiResponseDTO.error("Lỗi sinh test case: " + e.getMessage()));
        }
    }

    // GET /api/diagrams/{id}/testcases — lấy danh sách test case
    @GetMapping("/{id}/testcases")
    public ResponseEntity<ApiResponseDTO<List<com.testcase.backend.entity.TestCase>>> getTestCases(
            @PathVariable UUID id) {
        List<com.testcase.backend.entity.TestCase> testCases = generatorService.getTestCasesForDiagram(id);
        return ResponseEntity.ok(
                ApiResponseDTO.ok("OK", testCases));
    }
    // GET /api/diagrams — lấy danh sách
    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<UseCaseDiagram>>> getAllDiagrams() {
        List<UseCaseDiagram> diagrams = diagramService.getAllDiagrams();
        return ResponseEntity.ok(
                ApiResponseDTO.ok("Lấy danh sách thành công", diagrams));
    }

    // GET /api/diagrams/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<UseCaseDiagram>> getDiagram(
            @PathVariable UUID id) {
        try {
            UseCaseDiagram diagram = diagramService.getDiagramById(id);
            return ResponseEntity.ok(
                    ApiResponseDTO.ok("OK", diagram));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}