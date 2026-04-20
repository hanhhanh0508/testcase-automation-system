package com.testcase.backend.controller;

import com.testcase.backend.dto.ApiResponseDTO;
import com.testcase.backend.dto.UseCaseInputDTO;
import com.testcase.backend.entity.UseCaseDiagram;
import com.testcase.backend.service.DiagramService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/diagrams")
public class DiagramController {

    private final DiagramService diagramService;

    public DiagramController(DiagramService diagramService) {
        this.diagramService = diagramService;
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