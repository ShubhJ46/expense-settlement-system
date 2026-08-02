package com.project.Splitwise.repository;

import com.project.Splitwise.model.IdempotencyRecord;
import com.project.Splitwise.model.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {

    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserId(String idempotencyKey, Long userId);
}
