package com.project.Splitwise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Splitwise.dto.RecordPaymentRequest;
import com.project.Splitwise.model.IdempotencyRecord;
import com.project.Splitwise.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyGuardTest {

    private static final Long USER = 1L;
    private static final String KEY = "key-abc";

    @Mock
    private IdempotencyRecordRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IdempotencyGuard guard() {
        return new IdempotencyGuard(repository, objectMapper);
    }

    private static RecordPaymentRequest request(String amount) {
        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setFromUserId(1L);
        req.setToUserId(2L);
        req.setAmount(new BigDecimal(amount));
        return req;
    }

    private IdempotencyRecord recordFor(Object request, String type, Long resourceId) {
        // Round-trips through the guard so the fingerprint is computed the same way.
        IdempotencyGuard g = guard();
        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        g.record(KEY, USER, type, resourceId, request);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("no key means no idempotency, and no row")
    void absentKeyIsANoOp() {
        assertTrue(guard().findReplay(null, USER, "Payment", request("10.00")).isEmpty());

        guard().record(null, USER, "Payment", 1L, request("10.00"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a blank key is treated as absent rather than as a valid key")
    void blankKeyIsANoOp() {
        assertTrue(guard().findReplay("   ", USER, "Payment", request("10.00")).isEmpty());
    }

    @Test
    @DisplayName("an unseen key is not a replay")
    void unseenKeyIsNotAReplay() {
        when(repository.findByIdempotencyKeyAndUserId(KEY, USER)).thenReturn(Optional.empty());

        assertTrue(guard().findReplay(KEY, USER, "Payment", request("10.00")).isEmpty());
    }

    @Test
    @DisplayName("the same key with the same body returns the original resource")
    void sameKeySameBodyIsAReplay() {
        RecordPaymentRequest req = request("10.00");
        IdempotencyRecord stored = recordFor(req, "Payment", 42L);
        when(repository.findByIdempotencyKeyAndUserId(KEY, USER)).thenReturn(Optional.of(stored));

        assertEquals(42L, guard().findReplay(KEY, USER, "Payment", request("10.00")).orElseThrow());
    }

    @Test
    @DisplayName("the same key with a different body is refused, not answered with the old response")
    void sameKeyDifferentBodyConflicts() {
        IdempotencyRecord stored = recordFor(request("10.00"), "Payment", 42L);
        when(repository.findByIdempotencyKeyAndUserId(KEY, USER)).thenReturn(Optional.of(stored));

        IdempotencyConflictException thrown = assertThrows(IdempotencyConflictException.class,
                () -> guard().findReplay(KEY, USER, "Payment", request("99.00")));

        assertTrue(thrown.getMessage().contains("different request body"), thrown.getMessage());
    }

    @Test
    @DisplayName("a key from one resource type cannot be reused for another")
    void keyCannotCrossResourceTypes() {
        IdempotencyRecord stored = recordFor(request("10.00"), "Payment", 42L);
        when(repository.findByIdempotencyKeyAndUserId(KEY, USER)).thenReturn(Optional.of(stored));

        assertThrows(IdempotencyConflictException.class,
                () -> guard().findReplay(KEY, USER, "Expense", request("10.00")));
    }

    @Test
    @DisplayName("a concurrent insert on the same key is refused rather than duplicating the resource")
    void concurrentInsertConflicts() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        IdempotencyConflictException thrown = assertThrows(IdempotencyConflictException.class,
                () -> guard().record(KEY, USER, "Payment", 42L, request("10.00")));

        assertTrue(thrown.getMessage().contains("already in progress"), thrown.getMessage());
    }

    @Test
    @DisplayName("the request body is hashed, not stored")
    void bodyIsFingerprintedNotRetained() {
        RecordPaymentRequest req = request("10.00");
        req.setNote("dinner at the place on the corner");

        IdempotencyRecord stored = recordFor(req, "Payment", 42L);

        assertEquals(64, stored.getRequestFingerprint().length(), "expected a SHA-256 hex digest");
        assertTrue(!stored.getRequestFingerprint().contains("dinner"),
                "the note must not be recoverable from the stored fingerprint");
    }
}
