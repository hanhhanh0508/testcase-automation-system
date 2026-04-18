package com.testcase.backend.entity;

import jakarta.persistence.*;
import java.util.UUID;

import com.testcase.backend.enums.ActorType;

@Entity
@Table(name = "actors")
public class Actor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FK về UseCaseDiagram — dùng @ManyToOne thay vì lưu UUID thô
    // để JPA tự join được, lazy load tránh N+1
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id", nullable = false)
    private UseCaseDiagram diagram;

    // ID gốc trong file XMI, ví dụ "A1" — dùng để map relationship
    @Column(nullable = false)
    private String xmiId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private ActorType actorType = ActorType.PRIMARY;

    // ── Constructors ──────────────────────────────────────────
    public Actor() {
    }

    public Actor(String xmiId, String name, ActorType actorType) {
        this.xmiId = xmiId;
        this.name = name;
        this.actorType = actorType;
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

    public String getXmiId() {
        return xmiId;
    }

    public void setXmiId(String xmiId) {
        this.xmiId = xmiId;
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

    public ActorType getActorType() {
        return actorType;
    }

    public void setActorType(ActorType actorType) {
        this.actorType = actorType;
    }
}