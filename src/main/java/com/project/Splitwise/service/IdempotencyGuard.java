package com.project.Splitwise.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Splitwise.model.IdempotencyRecord;
import com.project.Splitwise.repository.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The single place client-supplied idempotency keys are honoured.
 *
 * <p>Both write endpoints funnel through here for the same reason authorization funnels
 * through {@code GroupAccess}: one rule to audit, and no endpoint can be added that quietly
 * forgets it.
 *
 * <p>The key is optional. Omitting it leaves the endpoint behaving exactly as before, which
 * keeps existing callers working — the guarantee is opt-in, as it is in every payments API
 * that does this.
 */
@Component
public class IdempotencyGuard {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyGuard(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the id of the resource this key already produced, if any.
     *
     * <p>A key seen before with the <em>same</em> body is a retry, and the caller should
     * return the original resource rather than doing the work again. A key seen before with a
     * <em>different</em> body is a client bug: answering it with the first response would
     * silently discard the second request, so it is refused instead.
     */
    public Optional<Long> findReplay(String key, Long userId, String resourceType, Object request) {
        if (isAbsent(key)) {
            return Optional.empty();
        }

        return repository.findByIdempotencyKeyAndUserId(key.trim(), userId)
                .map(record -> {
                    if (!record.getResourceType().equals(resourceType)) {
                        throw new IdempotencyConflictException(
                                "Idempotency key already used for a " + record.getResourceType()
                                        + ", cannot reuse it for a " + resourceType);
                    }
                    if (!record.getRequestFingerprint().equals(fingerprint(request))) {
                        throw new IdempotencyConflictException(
                                "Idempotency key was already used with a different request body");
                    }
                    return record.getResourceId();
                });
    }

    /**
     * Records what this key produced.
     *
     * <p>Must be called inside the transaction that created the resource. That is the whole
     * mechanism: if the write commits, so does the key, and if it rolls back the key goes
     * with it — so a retry can never find a key pointing at a resource that does not exist.
     */
    public void record(String key, Long userId, String resourceType, Long resourceId, Object request) {
        if (isAbsent(key)) {
            return;
        }

        try {
            repository.save(new IdempotencyRecord(
                    key.trim(), userId, resourceType, resourceId, fingerprint(request)));
        } catch (DataIntegrityViolationException e) {
            // Two requests carrying the same key arrived close enough together that neither
            // saw the other's row. The unique constraint is the arbiter: one commits, and
            // this one is refused rather than committing a second resource under a key that
            // is supposed to identify exactly one.
            throw new IdempotencyConflictException(
                    "A request with this idempotency key is already in progress");
        }
    }

    private static boolean isAbsent(String key) {
        return key == null || key.isBlank();
    }

    /**
     * SHA-256 of the serialised request.
     *
     * <p>Hashed rather than stored verbatim so the table cannot become an accidental copy of
     * every request body ever submitted, including whatever a caller puts in a note field.
     */
    private String fingerprint(Object request) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not fingerprint request for idempotency", e);
        }
    }
}
