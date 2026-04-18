package com.testcase.backend.repository;

import com.testcase.backend.entity.UseCaseDiagram;
import com.testcase.backend.enums.DiagramStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UseCaseDiagramRepository extends JpaRepository<UseCaseDiagram, UUID> {

    // Tìm tất cả diagram theo status (ví dụ: PARSED, ERROR)
    List<UseCaseDiagram> findByStatus(DiagramStatus status);

    // Tìm diagram theo tên (search)
    List<UseCaseDiagram> findByNameContainingIgnoreCase(String name);
}