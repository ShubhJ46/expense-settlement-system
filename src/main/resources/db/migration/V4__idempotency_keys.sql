-- Client-supplied idempotency keys for the write endpoints.
--
-- The outbox makes the database and the broker agree, and processed_events makes redelivery
-- harmless. Neither covers the hop in front of both: a client whose POST times out and
-- retries would create a second, entirely valid expense -- correct by every internal measure
-- and still wrong. This table closes that, using the same mechanism as processed_events, a
-- row written in the same transaction as the work it guards.
--
-- Definitions taken verbatim from SchemaDdlGenerator's output, as in V1-V3.

create table idempotency_keys (
    idempotency_key     varchar(255)                not null,
    user_id             bigint                      not null,
    created_at          timestamp(6) with time zone not null,
    request_fingerprint varchar(64)                 not null,
    resource_id         bigint                      not null,
    resource_type       varchar(255)                not null,
    -- Composite, not a bare key. A client's keys are its own namespace: keyed globally, one
    -- caller could collide with -- or probe for -- another caller's key, which turns a
    -- reliability feature into an information leak.
    primary key (idempotency_key, user_id)
);

alter table idempotency_keys
    add constraint fk_idempotency_keys_user foreign key (user_id) references users (id) on delete cascade;

-- Supports ageing rows out. Keys only need to outlive a client's retry window, so an
-- unbounded table here would be the same slow leak processed_events already has; the index
-- is what lets a cleanup job find old rows without scanning the lot.
create index idx_idempotency_keys_created_at on idempotency_keys (created_at);
