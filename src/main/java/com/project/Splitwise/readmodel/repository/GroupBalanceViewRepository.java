package com.project.Splitwise.readmodel.repository;

import com.project.Splitwise.readmodel.GroupBalanceView;
import com.project.Splitwise.readmodel.GroupBalanceViewId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupBalanceViewRepository extends JpaRepository<GroupBalanceView, GroupBalanceViewId> {
    List<GroupBalanceView> findByGroupId(Long groupId);
}
