package com.project.Splitwise.readmodel.repository;

import com.project.Splitwise.readmodel.SettlementView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementViewRepository
        extends JpaRepository<SettlementView, Long> {

    List<SettlementView> findByGroupId(Long groupId);

    /**
     * Clears a group's plan so a fresh one can replace it.
     *
     * <p>The delete and the re-insert run in one transaction in
     * {@link com.project.Splitwise.readmodel.consumer.SettlementViewConsumer}, which is what
     * makes reprojection idempotent: a replayed event rebuilds the same plan instead of
     * stacking a second copy on top of the first.
     */
    void deleteByGroupId(Long groupId);
}
