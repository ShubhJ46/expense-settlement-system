package com.project.Splitwise.readmodel.controller;

import com.project.Splitwise.readmodel.GroupBalanceView;
import com.project.Splitwise.readmodel.repository.GroupBalanceViewRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/balances")
public class BalanceQueryController {
    private final GroupBalanceViewRepository repository;

    public BalanceQueryController(GroupBalanceViewRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{groupId}")
    public List<GroupBalanceView> getBalances(@PathVariable Long groupId) {
        return repository.findByGroupId(groupId);
    }
}
