package com.project.Splitwise.security;

import com.project.Splitwise.repository.GroupMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupAccessTest {

    private static final Long GROUP = 7L;

    @Mock
    private GroupMemberRepository members;
    @Mock
    private CurrentUser currentUser;
    @InjectMocks
    private GroupAccess groupAccess;

    @Test
    void returnsTheCallerWhenTheyAreAMember() {
        when(currentUser.id()).thenReturn(1L);
        when(members.existsByGroupIdAndUserId(GROUP, 1L)).thenReturn(true);

        assertEquals(1L, groupAccess.requireMember(GROUP));
    }

    @Test
    void refusesANonMember() {
        when(currentUser.id()).thenReturn(99L);
        when(members.existsByGroupIdAndUserId(GROUP, 99L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> groupAccess.requireMember(GROUP));
    }

    @Test
    @DisplayName("the refusal does not reveal whether the group exists")
    void refusalIsUninformative() {
        when(currentUser.id()).thenReturn(99L);
        when(members.existsByGroupIdAndUserId(GROUP, 99L)).thenReturn(false);

        AccessDeniedException thrown = assertThrows(AccessDeniedException.class,
                () -> groupAccess.requireMember(GROUP));

        String message = thrown.getMessage().toLowerCase();
        assertTrue(message.contains("no access"), message);
        assertTrue(!message.contains("exist") && !message.contains("found"),
                "message should not distinguish a missing group from a forbidden one: " + message);
    }

    @Test
    void acceptsWhenEveryNamedUserIsAMember() {
        when(members.findUserIdsByGroupId(GROUP)).thenReturn(List.of(1L, 2L, 3L));

        groupAccess.requireAllMembers(GROUP, List.of(1L, 2L));
    }

    @Test
    @DisplayName("a single stranger among the participants refuses the whole request")
    void refusesWhenAnyUserIsNotAMember() {
        when(members.findUserIdsByGroupId(GROUP)).thenReturn(List.of(1L, 2L));

        AccessDeniedException thrown = assertThrows(AccessDeniedException.class,
                () -> groupAccess.requireAllMembers(GROUP, List.of(1L, 2L, 99L)));

        assertTrue(thrown.getMessage().contains("99"), thrown.getMessage());
    }

    @Test
    @DisplayName("duplicates in the participant list do not change the outcome")
    void toleratesDuplicateParticipants() {
        when(members.findUserIdsByGroupId(GROUP)).thenReturn(List.of(1L, 2L));

        groupAccess.requireAllMembers(GROUP, List.of(1L, 1L, 2L, 2L));
    }
}
