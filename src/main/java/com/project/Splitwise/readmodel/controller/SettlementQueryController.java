package com.project.Splitwise.readmodel.controller;

import com.project.Splitwise.readmodel.SettlementView;
import com.project.Splitwise.readmodel.repository.SettlementViewRepository;
import com.project.Splitwise.security.GroupAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/settlements")
public class SettlementQueryController {
    private final SettlementViewRepository repository;
    private final GroupAccess groupAccess;

    public SettlementQueryController(SettlementViewRepository repository, GroupAccess groupAccess) {
        this.repository = repository;
        this.groupAccess = groupAccess;
    }

    /** A group's settlement plan is visible only to its members. */
    @GetMapping("/{groupId}")
    public List<SettlementView> getSettlements(@PathVariable Long groupId) {
        groupAccess.requireMember(groupId);
        return repository.findByGroupId(groupId);
    }
}
