package com.project.Splitwise.readmodel.controller;

import com.project.Splitwise.readmodel.SettlementView;
import com.project.Splitwise.readmodel.repository.SettlementViewRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/settlements")
public class SettlementQueryController {
    private final SettlementViewRepository repository;

    public SettlementQueryController(SettlementViewRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{groupId} ")
    public List<SettlementView> getSettlements(@PathVariable Long groupId) {
        return repository.findByGroupId(groupId);
    }
}
