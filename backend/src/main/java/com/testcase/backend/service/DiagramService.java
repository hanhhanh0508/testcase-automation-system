package com.testcase.backend.service;

import com.testcase.backend.dto.UseCaseInputDTO;
import com.testcase.backend.entity.UseCaseDiagram;
import com.testcase.backend.enums.DiagramStatus;
import com.testcase.backend.enums.SourceFormat;
import com.testcase.backend.repository.UseCaseDiagramRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class DiagramService {

    private final UseCaseDiagramRepository diagramRepository;

    // Thư mục lưu file upload
    private static final String UPLOAD_DIR = "uploads/";

    public DiagramService(UseCaseDiagramRepository diagramRepository) {
        this.diagramRepository = diagramRepository;
    }

    // ── Nhận input dạng text ──────────────────────────────────
    public UseCaseDiagram createFromText(UseCaseInputDTO dto) {
        String name = dto.getDiagramName() != null
                ? dto.getDiagramName()
                : "Diagram_" + System.currentTimeMillis();

        UseCaseDiagram diagram = new UseCaseDiagram(name, SourceFormat.JSON);
        diagram.setDescription(dto.getUseCaseText());
        diagram.setStatus(DiagramStatus.UPLOADED);

        return diagramRepository.save(diagram);
    }

    // ── Nhận input dạng file upload ───────────────────────────
    public UseCaseDiagram createFromFile(MultipartFile file, String diagramName) throws IOException {
        // Xác định format từ tên file
        String filename = file.getOriginalFilename();
        SourceFormat format = detectFormat(filename);

        // Lưu file vào thư mục uploads/
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        String savedFileName = UUID.randomUUID() + "_" + filename;
        Path filePath = uploadPath.resolve(savedFileName);
        Files.write(filePath, file.getBytes());

        // Tạo bản ghi trong DB
        String name = (diagramName != null && !diagramName.isBlank())
                ? diagramName
                : filename;

        UseCaseDiagram diagram = new UseCaseDiagram(name, format);
        diagram.setFilePath(filePath.toString());
        diagram.setStatus(DiagramStatus.UPLOADED);

        return diagramRepository.save(diagram);
    }

    // ── Lấy danh sách diagram ─────────────────────────────────
    public List<UseCaseDiagram> getAllDiagrams() {
        return diagramRepository.findAll();
    }

    public UseCaseDiagram getDiagramById(UUID id) {
        return diagramRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Diagram not found: " + id));
    }

    // ── Detect format từ đuôi file ────────────────────────────
    private SourceFormat detectFormat(String filename) {
        if (filename == null)
            return SourceFormat.JSON;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".xmi") || lower.endsWith(".xml"))
            return SourceFormat.XMI;
        if (lower.endsWith(".puml") || lower.endsWith(".plantuml"))
            return SourceFormat.PLANTUML;
        if (lower.endsWith(".drawio"))
            return SourceFormat.DRAWIO;
        return SourceFormat.JSON;
    }
}
