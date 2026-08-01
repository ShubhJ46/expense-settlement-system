package com.project.Splitwise.repository;

import com.project.Splitwise.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims a batch of unpublished events for this relay instance.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} is what makes the relay horizontally scalable:
     * concurrent instances take disjoint batches instead of contending on the same rows or
     * double-publishing them. Without SKIP LOCKED a second instance would block on the
     * first one's locks and the relay would effectively be single-threaded.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextUnpublished(@Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();
}
