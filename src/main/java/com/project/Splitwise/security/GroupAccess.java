package com.project.Splitwise.security;

import com.project.Splitwise.repository.GroupMemberRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single place group authorization is decided.
 *
 * <p>Every group-scoped operation funnels through here rather than each service inventing
 * its own check, so there is exactly one rule to audit and no endpoint can be added that
 * quietly forgets it.
 */
@Component
public class GroupAccess {

    private final GroupMemberRepository members;
    private final CurrentUser currentUser;

    public GroupAccess(GroupMemberRepository members, CurrentUser currentUser) {
        this.members = members;
        this.currentUser = currentUser;
    }

    /**
     * Asserts the caller belongs to the group.
     *
     * <p>The message deliberately does not distinguish "this group does not exist" from
     * "you are not in it". Doing so would let an outsider enumerate which group ids are
     * real by watching the difference.
     */
    public Long requireMember(Long groupId) {
        Long userId = currentUser.id();
        if (!members.existsByGroupIdAndUserId(groupId, userId)) {
            throw new AccessDeniedException("No access to group " + groupId);
        }
        return userId;
    }

    /**
     * Asserts every named user belongs to the group.
     *
     * <p>Without this, an authenticated member of one group could still write an expense
     * that charged a share to somebody who was never in it.
     */
    public void requireAllMembers(Long groupId, Collection<Long> userIds) {
        Set<Long> actual = new HashSet<>(members.findUserIdsByGroupId(groupId));

        List<Long> strangers = userIds.stream()
                .distinct()
                .filter(id -> !actual.contains(id))
                .toList();

        if (!strangers.isEmpty()) {
            throw new AccessDeniedException(
                    "Not members of group " + groupId + ": " + strangers);
        }
    }
}
