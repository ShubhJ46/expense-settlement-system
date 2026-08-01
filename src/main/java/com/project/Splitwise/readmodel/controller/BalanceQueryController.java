package com.project.Splitwise.readmodel.controller;

import com.project.Splitwise.readmodel.GroupBalanceView;
import com.project.Splitwise.readmodel.repository.GroupBalanceViewRepository;
import com.project.Splitwise.security.GroupAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/balances")
public class BalanceQueryController {
    private final GroupBalanceViewRepository repository;
    private final GroupAccess groupAccess;

    public BalanceQueryController(GroupBalanceViewRepository repository, GroupAccess groupAccess) {
        this.repository = repository;
        this.groupAccess = groupAccess;
    }

    /** A group's balances are visible only to its members. */
    @GetMapping("/{groupId}")
    public List<GroupBalanceView> getBalances(@PathVariable Long groupId) {
        groupAccess.requireMember(groupId);
        return repository.findByGroupId(groupId);
    }
}
