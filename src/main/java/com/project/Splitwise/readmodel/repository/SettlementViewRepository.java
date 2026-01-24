package com.project.Splitwise.readmodel.repository;

import com.project.Splitwise.readmodel.SettlementView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementViewRepository
        extends JpaRepository<SettlementView, Long> {

    List<SettlementView> findByGroupId(Long groupId);
}
