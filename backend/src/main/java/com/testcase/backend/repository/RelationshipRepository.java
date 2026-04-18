package com.testcase.backend.repository;

import com.testcase.backend.entity.Relationship;
import com.testcase.backend.enums.RelationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RelationshipRepository extends JpaRepository<Relationship, UUID> {

    // Lấy tất cả relationship của 1 diagram
    List<Relationship> findByDiagramId(UUID diagramId);

    // Lấy relationship theo type (ví dụ: tất cả INCLUDE)
    List<Relationship> findByDiagramIdAndType(UUID diagramId, RelationType type);

    // Lấy tất cả relationship có source là xmiId này
    // Dùng khi vẽ lại diagram: "A1 liên kết với những gì?"
    List<Relationship> findByDiagramIdAndSourceXmiId(UUID diagramId, String sourceXmiId);
}