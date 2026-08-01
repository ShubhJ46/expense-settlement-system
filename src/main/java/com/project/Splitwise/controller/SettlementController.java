package com.project.Splitwise.controller;

import com.project.Splitwise.dto.RecordPaymentRequest;
import com.project.Splitwise.dto.SettlementResponse;
import com.project.Splitwise.model.Payment;
import com.project.Splitwise.service.PaymentService;
import com.project.Splitwise.service.SettlementService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/groups")
@Slf4j
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);
    private final SettlementService settlementService;
    private final PaymentService paymentService;

    public SettlementController(SettlementService settlementService, PaymentService paymentService) {
        this.settlementService = settlementService;
        this.paymentService = paymentService;
    }

    /** The suggested plan: who <em>should</em> pay whom to zero the group out. */
    @GetMapping("/{groupId}/settlements")
    public SettlementResponse getSettlements(@PathVariable("groupId") Long groupId) {
        SettlementResponse response = settlementService.getSettlements(groupId);
        log.info("Settlement response={}", response);
        return response;
    }

    /**
     * Records that a payment actually happened, which is what discharges the debt.
     *
     * <p>Returns 202 rather than 200: the payment row is committed, but the balances it
     * moves are updated by a consumer after the outbox relay publishes the event, so the
     * figures from {@code GET /balances/{groupId}} converge shortly afterwards rather than
     * immediately.
     */
    @PostMapping("/{groupId}/settlements")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Payment recordPayment(@PathVariable("groupId") Long groupId,
                                 @RequestBody @Valid RecordPaymentRequest request) {
        log.info("Recording payment in group {}: {} -> {} of {}",
                groupId, request.getFromUserId(), request.getToUserId(), request.getAmount());
        return paymentService.recordPayment(groupId, request);
    }

    /** The payments already recorded for this group, newest first. */
    @GetMapping("/{groupId}/payments")
    public List<Payment> getPayments(@PathVariable("groupId") Long groupId) {
        return paymentService.getPayments(groupId);
    }
}
