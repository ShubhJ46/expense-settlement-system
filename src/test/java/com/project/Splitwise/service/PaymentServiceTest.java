package com.project.Splitwise.service;

import com.project.Splitwise.domain.event.PaymentRecordedEvent;
import com.project.Splitwise.dto.RecordPaymentRequest;
import com.project.Splitwise.model.Payment;
import com.project.Splitwise.outbox.OutboxWriter;
import com.project.Splitwise.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepo;
    @Mock
    private OutboxWriter outboxWriter;
    @InjectMocks
    private PaymentService paymentService;

    private static RecordPaymentRequest request(long from, long to, String amount) {
        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setFromUserId(from);
        req.setToUserId(to);
        req.setAmount(new BigDecimal(amount));
        return req;
    }

    private void stubSave() {
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(42L);
            return p;
        });
    }

    @Test
    @DisplayName("stages the event on the group's partition key so payments order with expenses")
    void stagesEventKeyedByGroup() {
        stubSave();

        paymentService.recordPayment(7L, request(1L, 2L, "50.00"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxWriter).append(eq("Payment"), eq("42"), eq("payment-recorded"),
                eq("7"), payload.capture());

        PaymentRecordedEvent event = (PaymentRecordedEvent) payload.getValue();
        assertNotNull(event.getEventId(), "eventId is the consumer's dedup key");
        assertEquals(7L, event.getGroupId());
        assertEquals(1L, event.getFromUserId());
        assertEquals(2L, event.getToUserId());
        assertEquals(0, new BigDecimal("50.00").compareTo(event.getAmount()));
    }

    @Test
    @DisplayName("a self-transfer is rejected before anything is written")
    void rejectsSelfTransfer() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> paymentService.recordPayment(7L, request(3L, 3L, "50.00")));

        assertEquals(true, thrown.getMessage().contains("cannot settle with themselves"));
        verify(paymentRepo, never()).save(any());
        verify(outboxWriter, never()).append(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sub-paisa payments are rejected rather than silently rounded")
    void rejectsSubPaisaAmount() {
        assertThrows(ArithmeticException.class,
                () -> paymentService.recordPayment(7L, request(1L, 2L, "50.005")));

        verify(paymentRepo, never()).save(any());
        verify(outboxWriter, never()).append(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("nothing is published outside the transaction that wrote the payment")
    void writesOnlyThroughOutbox() {
        stubSave();

        paymentService.recordPayment(7L, request(1L, 2L, "50.00"));

        // The only publication path is the outbox row; the service holds no Kafka template.
        verify(outboxWriter).append(any(), any(), any(), any(), any());
        verify(paymentRepo).save(any(Payment.class));
    }
}
