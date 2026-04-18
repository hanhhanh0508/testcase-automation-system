package com.testcase.backend.repository;

import com.testcase.backend.entity.UseCase;
import com.testcase.backend.enums.UseCaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UseCaseRepository extends JpaRepository<UseCase, UUID> {

    // Lấy tất cả use case của 1 diagram
    List<UseCase> findByDiagramId(UUID diagramId);

    // Tìm use case theo xmiId trong 1 diagram
    // Dùng khi resolve relationship: "UC1" → UseCase object
    Optional<UseCase> findByDiagramIdAndXmiId(UUID diagramId, String xmiId);

    // Lấy use case chưa sinh test case (để batch generate)
    List<UseCase> findByDiagramIdAndStatus(UUID diagramId, UseCaseStatus status);
}