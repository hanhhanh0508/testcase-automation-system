package com.testcase.backend.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.testcase.backend.enums.UseCasePriority;
import com.testcase.backend.enums.UseCaseStatus;

@Entity
@Table(name = "use_cases")
public class UseCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id", nullable = false)
    private UseCaseDiagram diagram;

    @Column(nullable = false)
    private String xmiId; // ví dụ "UC1" — dùng để map relationship

    @Column(nullable = false)
    private String name; // ví dụ "Login", "Checkout"

    @Column(columnDefinition = "TEXT")
    private String description;

    // Lưu list dạng JSON string trong 1 column
    // ví dụ: ["User đã đăng ký", "Hệ thống đang hoạt động"]
    @ElementCollection
    @CollectionTable(name = "use_case_preconditions", joinColumns = @JoinColumn(name = "use_case_id"))
    @Column(name = "precondition", columnDefinition = "TEXT")
    private List<String> preconditions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "use_case_postconditions", joinColumns = @JoinColumn(name = "use_case_id"))
    @Column(name = "postcondition", columnDefinition = "TEXT")
    private List<String> postconditions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private UseCasePriority priority = UseCasePriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UseCaseStatus status = UseCaseStatus.PENDING;

    // Quan hệ 1:N với TestCase
    @OneToMany(mappedBy = "useCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestCase> testCases = new ArrayList<>();

    // ── Constructors ──────────────────────────────────────────
    public UseCase() {
    }

    public UseCase(String xmiId, String name) {
        this.xmiId = xmiId;
        this.name = name;
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

    public List<String> getPreconditions() {
        return preconditions;
    }

    public void setPreconditions(List<String> preconditions) {
        this.preconditions = preconditions;
    }

    public List<String> getPostconditions() {
        return postconditions;
    }

    public void setPostconditions(List<String> postconditions) {
        this.postconditions = postconditions;
    }

    public UseCasePriority getPriority() {
        return priority;
    }

    public void setPriority(UseCasePriority priority) {
        this.priority = priority;
    }

    public UseCaseStatus getStatus() {
        return status;
    }

    public void setStatus(UseCaseStatus status) {
        this.status = status;
    }

    public List<TestCase> getTestCases() {
        return testCases;
    }
}