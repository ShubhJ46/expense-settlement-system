package com.project.Splitwise.service;

import com.project.Splitwise.dto.GroupDtos;
import com.project.Splitwise.model.ExpenseGroup;
import com.project.Splitwise.model.GroupMember;
import com.project.Splitwise.repository.ExpenseGroupRepository;
import com.project.Splitwise.repository.GroupMemberRepository;
import com.project.Splitwise.repository.UserRepository;
import com.project.Splitwise.security.CurrentUser;
import com.project.Splitwise.security.GroupAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupService {

    private final ExpenseGroupRepository groups;
    private final GroupMemberRepository members;
    private final UserRepository users;
    private final GroupAccess groupAccess;
    private final CurrentUser currentUser;

    public GroupService(ExpenseGroupRepository groups,
                        GroupMemberRepository members,
                        UserRepository users,
                        GroupAccess groupAccess,
                        CurrentUser currentUser) {
        this.groups = groups;
        this.members = members;
        this.users = users;
        this.groupAccess = groupAccess;
        this.currentUser = currentUser;
    }

    /** The creator is joined to the group in the same transaction; a group with no members would be unreachable. */
    @Transactional
    public GroupDtos.GroupResponse create(GroupDtos.CreateGroupRequest req) {
        Long creator = currentUser.id();

        ExpenseGroup group = new ExpenseGroup();
        group.setName(req.name());
        group.setCreatedBy(creator);
        ExpenseGroup saved = groups.save(group);

        members.save(new GroupMember(saved.getId(), creator));

        return toResponse(saved, List.of(creator));
    }

    /**
     * Adds a member. Any existing member may do this — there is no owner role, which matches
     * how the product actually behaves and avoids inventing a permission model the rest of
     * the service does not use.
     */
    @Transactional
    public GroupDtos.GroupResponse addMember(Long groupId, GroupDtos.AddMemberRequest req) {
        groupAccess.requireMember(groupId);

        if (!users.existsById(req.userId())) {
            throw new IllegalArgumentException("No such user: " + req.userId());
        }

        if (!members.existsByGroupIdAndUserId(groupId, req.userId())) {
            members.save(new GroupMember(groupId, req.userId()));
        }

        ExpenseGroup group = groups.findById(groupId).orElseThrow();
        return toResponse(group, members.findUserIdsByGroupId(groupId));
    }

    @Transactional(readOnly = true)
    public GroupDtos.GroupResponse get(Long groupId) {
        groupAccess.requireMember(groupId);
        ExpenseGroup group = groups.findById(groupId).orElseThrow();
        return toResponse(group, members.findUserIdsByGroupId(groupId));
    }

    /** Only the caller's own groups; there is no endpoint that lists every group in the system. */
    @Transactional(readOnly = true)
    public List<GroupDtos.GroupResponse> myGroups() {
        return groups.findAllById(members.findGroupIdsByUserId(currentUser.id())).stream()
                .map(g -> toResponse(g, members.findUserIdsByGroupId(g.getId())))
                .toList();
    }

    private GroupDtos.GroupResponse toResponse(ExpenseGroup group, List<Long> memberIds) {
        return new GroupDtos.GroupResponse(
                group.getId(), group.getName(), group.getCreatedBy(), memberIds);
    }
}
