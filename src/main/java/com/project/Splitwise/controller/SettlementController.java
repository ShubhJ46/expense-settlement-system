package com.project.Splitwise.controller;

import com.project.Splitwise.dto.SettlementResponse;
import com.project.Splitwise.service.SettlementService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups")
@Slf4j
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);
    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/{groupId}/settlements" )
    public SettlementResponse getSettlements(@PathVariable("groupId") Long groupId) {
        //log.info("Settlement endpoint hit for groupId={}", groupId);
        SettlementResponse response = settlementService.getSettlements(groupId);
        log.info("Settlement response={}", response);
        return response;
    }
}
