package com.project.Splitwise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class GroupDtos {

    private GroupDtos() {
    }

    public record CreateGroupRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record AddMemberRequest(@NotNull Long userId) {
    }

    public record GroupResponse(Long id, String name, Long createdBy, List<Long> memberIds) {
    }
}
