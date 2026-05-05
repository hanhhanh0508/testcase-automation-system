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
    private final JsonDiagramParser jsonDiagramParser;

    private static final String UPLOAD_DIR = "uploads/";

    public DiagramService(UseCaseDiagramRepository diagramRepository,
            JsonDiagramParser jsonDiagramParser) {
        this.diagramRepository = diagramRepository;
        this.jsonDiagramParser = jsonDiagramParser;
    }

    // Nhận input dạng text
    public UseCaseDiagram createFromText(UseCaseInputDTO dto) {
        String name = dto.getDiagramName() != null
                ? dto.getDiagramName()
                : "Diagram_" + System.currentTimeMillis();

        UseCaseDiagram diagram = new UseCaseDiagram(name, SourceFormat.JSON);
        diagram.setDescription(dto.getUseCaseText());
        diagram.setStatus(DiagramStatus.UPLOADED);

        return diagramRepository.save(diagram);
    }

    // Nhận input dạng file upload
    public UseCaseDiagram createFromFile(MultipartFile file, String diagramName) throws IOException {
        String filename = file.getOriginalFilename();
        SourceFormat format = detectFormat(filename);

        // Lưu file
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        String savedFileName = UUID.randomUUID() + "_" + filename;
        Path filePath = uploadPath.resolve(savedFileName);
        Files.write(filePath, file.getBytes());

        // Tên diagram
        String name = (diagramName != null && !diagramName.isBlank())
                ? diagramName
                : filename;

        UseCaseDiagram diagram = new UseCaseDiagram(name, format);
        diagram.setFilePath(filePath.toString());
        diagram.setStatus(DiagramStatus.PARSING);

        // Parse nội dung nếu là JSON
        if (format == SourceFormat.JSON) {
            try {
                jsonDiagramParser.parse(file.getInputStream(), diagram);
                diagram.setStatus(DiagramStatus.PARSED);
            } catch (Exception e) {
                diagram.setStatus(DiagramStatus.ERROR);
                diagram.setDescription("Parse error: " + e.getMessage());
            }
        } else {
            // Các format khác (XMI, PlantUML, DrawIO) để UPLOADED
            // — có thể mở rộng parser sau
            diagram.setStatus(DiagramStatus.UPLOADED);
        }

        return diagramRepository.save(diagram);
    }

    public List<UseCaseDiagram> getAllDiagrams() {
        return diagramRepository.findAll();
    }

    public UseCaseDiagram getDiagramById(UUID id) {
        return diagramRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Diagram not found: " + id));
    }

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