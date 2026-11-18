package com.stackcraft.retroflow.repository;

import com.stackcraft.retroflow.entity.ActionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActionItemRepository extends JpaRepository<ActionItem, Long> {

    Optional<ActionItem> findByIdAndDeletedFalse(Long id);

    List<ActionItem> findAllByRetrospectiveIdAndDeletedFalseOrderByIdAsc(Long retrospectiveId);
}
