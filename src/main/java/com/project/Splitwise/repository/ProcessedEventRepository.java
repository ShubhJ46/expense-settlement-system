package com.project.Splitwise.repository;

import com.project.Splitwise.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The dedup ledger.
 *
 * <p>Needs no query methods of its own: the event id is the primary key, so the inherited
 * {@code existsById} is the entire deduplication check and it resolves against the primary
 * index.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
