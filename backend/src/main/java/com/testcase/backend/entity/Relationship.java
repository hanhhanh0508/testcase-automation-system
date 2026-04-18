package com.testcase.backend.entity;

import jakarta.persistence.*;
import java.util.UUID;

import com.testcase.backend.enums.RelationType;

@Entity
@Table(name = "relationships")
public class Relationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id", nullable = false)
    private UseCaseDiagram diagram;

    // Dùng xmiId thay vì UUID vì lúc parse file,
    // các relationship tham chiếu qua xmi.id gốc ("A1", "UC1")
    // Sau khi save hết vào DB mới resolve sang UUID thật nếu cần
    @Column(nullable = false)
    private String sourceXmiId; // xmiId của phần tử nguồn

    @Column(nullable = false)
    private String targetXmiId; // xmiId của phần tử đích

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationType type; // ASSOCIATION, INCLUDE, EXTEND, GENERALIZATION

    private String label; // Nhãn tùy chọn trên đường kết nối

    // ── Constructors ──────────────────────────────────────────
    public Relationship() {
    }

    public Relationship(String sourceXmiId, String targetXmiId, RelationType type) {
        this.sourceXmiId = sourceXmiId;
        this.targetXmiId = targetXmiId;
        this.type = type;
    }

    // ── Getters & Setters ─────────────────────────────────────
    public UUID getId() {
        return id;
    }

    public UseCaseDiagram getDiagram() {
        return diagram;
    }

    public void setDiagram(UseCaseDiagram diagram) {
        this.diagram = diagram;
    }

    public String getSourceXmiId() {
        return sourceXmiId;
    }

    public void setSourceXmiId(String sourceXmiId) {
        this.sourceXmiId = sourceXmiId;
    }

    public String getTargetXmiId() {
        return targetXmiId;
    }

    public void setTargetXmiId(String targetXmiId) {
        this.targetXmiId = targetXmiId;
    }

    public RelationType getType() {
        return type;
    }

    public void setType(RelationType type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}