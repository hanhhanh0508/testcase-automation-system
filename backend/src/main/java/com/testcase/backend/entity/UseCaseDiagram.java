package com.testcase.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.testcase.backend.enums.DiagramStatus;
import com.testcase.backend.enums.SourceFormat;

@Entity
@Table(name = "use_case_diagrams")
public class UseCaseDiagram {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceFormat sourceFormat;

    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiagramStatus status = DiagramStatus.UPLOADED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Quan hệ 1:N với Actor, UseCase, Relationship
    // cascade = ALL: khi xóa diagram thì xóa luôn con
    // orphanRemoval = true: khi remove khỏi list thì xóa DB
    @OneToMany(mappedBy = "diagram", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Actor> actors = new ArrayList<>();

    @OneToMany(mappedBy = "diagram", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UseCase> useCases = new ArrayList<>();

    @OneToMany(mappedBy = "diagram", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Relationship> relationships = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────
    public UseCaseDiagram() {
    }

    public UseCaseDiagram(String name, SourceFormat sourceFormat) {
        this.name = name;
        this.sourceFormat = sourceFormat;
    }

    // ── Helper methods ────────────────────────────────────────
    public void addActor(Actor actor) {
        actor.setDiagram(this);
        this.actors.add(actor);
    }

    public void addUseCase(UseCase useCase) {
        useCase.setDiagram(this);
        this.useCases.add(useCase);
    }

    public void addRelationship(Relationship relationship) {
        relationship.setDiagram(this);
        this.relationships.add(relationship);
    }

    // ── Getters & Setters ─────────────────────────────────────
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SourceFormat getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(SourceFormat sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public DiagramStatus getStatus() {
        return status;
    }

    public void setStatus(DiagramStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<Actor> getActors() {
        return actors;
    }

    public List<UseCase> getUseCases() {
        return useCases;
    }

    public List<Relationship> getRelationships() {
        return relationships;
    }
}