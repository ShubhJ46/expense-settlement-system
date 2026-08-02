package com.project.Splitwise.controller;

import com.project.Splitwise.dto.GroupDtos;
import com.project.Splitwise.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Group membership — the data every authorization decision in the service is made against.
 *
 * <p>Shares the {@code /groups} base path with
 * {@link com.project.Splitwise.controller.SettlementController}, which owns the
 * {@code /groups/{id}/settlements} and {@code /groups/{id}/payments} sub-resources. Two
 * controllers on one prefix is fine as long as no two handlers claim the same method and
 * path, and it keeps settlement concerns out of this class.
 *
 * <p>Note what is missing: there is no "list all groups" endpoint. {@code GET /groups}
 * returns the caller's own groups and nothing else, because a global listing would leak the
 * existence and names of every group in the system.
 */
@RestController
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDtos.GroupResponse create(@RequestBody @Valid GroupDtos.CreateGroupRequest request) {
        return groupService.create(request);
    }

    @GetMapping
    public List<GroupDtos.GroupResponse> myGroups() {
        return groupService.myGroups();
    }

    @GetMapping("/{groupId}")
    public GroupDtos.GroupResponse get(@PathVariable("groupId") Long groupId) {
        return groupService.get(groupId);
    }

    @PostMapping("/{groupId}/members")
    public GroupDtos.GroupResponse addMember(@PathVariable("groupId") Long groupId,
                                             @RequestBody @Valid GroupDtos.AddMemberRequest request) {
        return groupService.addMember(groupId, request);
    }
}
