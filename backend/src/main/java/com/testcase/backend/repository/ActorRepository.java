package com.testcase.backend.repository;

import com.testcase.backend.entity.Actor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActorRepository extends JpaRepository<Actor, UUID> {

    // Lấy tất cả actor của 1 diagram
    List<Actor> findByDiagramId(UUID diagramId);

    // Tìm actor theo xmiId trong 1 diagram
    // Dùng khi map relationship: "A1" → Actor object
    Optional<Actor> findByDiagramIdAndXmiId(UUID diagramId, String xmiId);
}