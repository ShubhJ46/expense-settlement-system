-- Trace context carried across the outbox.
--
-- The outbox is a deliberate break in the call chain: the HTTP thread commits a row and
-- returns, and a scheduled poll publishes it later on another thread. That break severs a
-- distributed trace as surely as it severs a stack trace, so without this column every relay
-- publish begins its own unrelated trace and the question people actually ask -- where did
-- this expense spend its time between the API call and the balance appearing -- has no answer.
--
-- Holds a W3C traceparent, which is a fixed 55 characters:
--   version(2) - trace-id(32) - parent-id(16) - flags(2), hyphen separated.
--
-- Nullable on purpose. Under any sampling rate below 1.0 most rows will not carry one, and
-- rows staged before this migration never will; both must stay publishable.
alter table outbox_events
    add column trace_parent varchar(55);
