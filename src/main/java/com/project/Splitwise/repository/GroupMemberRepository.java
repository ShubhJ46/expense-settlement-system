package com.project.Splitwise.repository;

import com.project.Splitwise.model.GroupMember;
import com.project.Splitwise.model.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    List<GroupMember> findByGroupId(Long groupId);

    @Query("select m.groupId from GroupMember m where m.userId = :userId")
    List<Long> findGroupIdsByUserId(Long userId);

    @Query("select m.userId from GroupMember m where m.groupId = :groupId")
    List<Long> findUserIdsByGroupId(Long groupId);
}
