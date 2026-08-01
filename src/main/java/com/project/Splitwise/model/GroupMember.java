package com.project.Splitwise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Membership is the authorization boundary for the whole service: every group-scoped
 * request is allowed or refused on the basis of a row in this table.
 */
@Entity
@Table(name = "group_members")
@IdClass(GroupMemberId.class)
@Data
public class GroupMember {

    @Id
    private Long groupId;

    @Id
    private Long userId;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime joinedAt;

    protected GroupMember() {
    }

    public GroupMember(Long groupId, Long userId) {
        this.groupId = groupId;
        this.userId = userId;
    }
}
