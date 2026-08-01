# Expense Settlement System

[![Splitwise CI](https://github.com/ShubhJ46/Java-Projects/actions/workflows/splitwise.yml/badge.svg)](https://github.com/ShubhJ46/Java-Projects/actions/workflows/splitwise.yml)

An event-driven backend for splitting shared expenses, in the style of Splitwise. Built with
Java 21, Spring Boot 3.5, Kafka and PostgreSQL.

The interesting parts are not the CRUD. They are: keeping a derived read model correct when
delivery is at-least-once, never losing an event to a dual write, and splitting money that
does not divide evenly.

---

## Architecture

```mermaid
flowchart LR
    C[Client] -->|POST /expenses| API[ExpenseService]
    C -->|POST /groups/id/settlements| PAY[PaymentService]
    API -->|same transaction| DB[(expenses<br/>expense_shares<br/>outbox_events)]
    PAY -->|same transaction| DB
    R[OutboxRelay] -->|poll FOR UPDATE SKIP LOCKED| DB
    R -->|expense-created| K1{{Kafka}}
    R -->|payment-recorded| K1
    K1 --> BC[BalanceEventConsumer]
    K1 --> PC[PaymentEventConsumer]
    BC -->|dedupe on eventId| BAL[(balances<br/>processed_events)]
    PC -->|dedupe on eventId| BAL
    BC -->|after commit| K2{{balance-updated}}
    PC -->|after commit| K2
    K2 --> BV[BalanceViewConsumer] --> GBV[(group_balance_view)]
    K2 --> SP[SettlementProjector]
    SP -->|settlement-calculated| K3{{Kafka}}
    K3 --> SV[SettlementViewConsumer] --> SVT[(settlement_view)]
    K1 -.failed after retries.-> DLT[expense-created.DLT] --> PM[(poison_messages)]
```

Writes go through the command side and land in Postgres. Reads are served from projections
that Kafka consumers maintain. The two are eventually consistent, and the system is designed
around that rather than pretending otherwise.

---

## The three problems worth explaining

### 1. The dual write, and why the outbox exists

The obvious implementation saves the expense and then publishes to Kafka:

```java
expenseRepo.save(expense);
kafkaTemplate.send("expense-created", event);   // <-- different system, no shared transaction
```

This is broken in both directions. If the broker times out after the DB commits, the balance
update never happens and no one notices. If the DB rolls back after a successful send,
consumers process an expense that does not exist.

Instead the event is written as a row in `outbox_events` in the *same* transaction as the
expense. Either both are durable or neither is. `OutboxRelay` then polls and publishes:

```sql
SELECT * FROM outbox_events
WHERE published_at IS NULL
ORDER BY created_at
LIMIT :batchSize
FOR UPDATE SKIP LOCKED
```

`SKIP LOCKED` is what makes the relay horizontally scalable — concurrent instances claim
disjoint batches instead of blocking on each other's locks.

Delivery is still at-least-once: the broker can accept a record and the relay can die before
marking the row published. That is handled by the next section, not by pretending it cannot
happen.

### 2. Effectively-once, not exactly-once

Consumers deduplicate on `eventId` using a `processed_events` table. The dedup row and the
balance mutation commit in one transaction, so a redelivered record is a no-op.

This is **effectively-once processing**, not Kafka exactly-once semantics. Real EOS needs a
transactional producer and `read_committed` consumers so that offset commits and writes are
one atomic unit. This system deliberately does not do that: the dedup table gets the same
practical guarantee for balance arithmetic at a fraction of the operational cost.

Ordering is handled separately. Every topic is keyed by `groupId`, so all events for a group
land on one partition where offset order is total order. Without that key, an unkeyed
full-group snapshot could be applied after a newer one and the read model would move
backwards in time.

### 3. Money that does not divide evenly

Split 10.00 three ways. Round each part to 3.33 and a paisa vanishes; round each to 3.34 and
you invent one. Either way group balances stop summing to zero, and because balances are
cumulative the error compounds with every expense until settlements no longer clear.

`ShareAllocator` uses the largest-remainder method: work in integer minor units, give
everyone the floor, then distribute the leftover units one at a time.

```
10.00 / 3  ->  [3.34, 3.33, 3.33]   sums to exactly 10.00
```

No participant is ever off by more than one paisa, and the total is exact by construction
rather than by rounding luck. Amounts carrying sub-paisa precision are rejected rather than
silently rounded, because quietly turning a caller's 10.005 into 10.01 invents money.

---

## Settlement algorithm

Balances are stored as one net figure per user per group, not as a debt graph. That choice
means cycles (A owes B owes C owes A) cancel automatically instead of needing to be detected
and unwound.

Settlement is a greedy largest-creditor / largest-debtor match over two heaps.

**It is a heuristic, not an optimal minimiser.** Minimising the number of transfers is
NP-hard — any subset of users whose balances sum to zero can be settled internally, and
finding those subsets is subset-sum. What greedy does guarantee is an upper bound of `n-1`
transfers for `n` participants with non-zero balances, since every iteration drives at least
one participant to exactly zero. It runs in O(n log n) and always clears every debt.

Exact minimisation is exponential and not worth it at realistic group sizes. If it ever
became worth it, the approach would be subset-partitioning before the greedy pass.

---

## Authentication and authorization

Authentication is a stateless HS256 JWT: `/auth/login` verifies a BCrypt hash and returns a
token whose subject is the user id. A filter turns that into the request's principal. Nothing
else in the service reads an identity from a request body or a path variable — who you *are*
comes only from a verified signature, while who a request *names* is untrusted input.

Authorization is group membership, and every group-scoped operation funnels through one
component:

```java
groupAccess.requireMember(groupId);                   // may the caller touch this group?
groupAccess.requireAllMembers(groupId, participants); // does everyone named belong to it?
```

The second check matters as much as the first. Without it a legitimate member of a group
could still write an expense charging a share to somebody who was never in it — authenticated,
authorized for the group, and still corrupting a stranger's balance.

Two smaller decisions worth naming:

- **The filter chain denies by default.** `anyRequest().authenticated()`, with an explicit
  short list of open endpoints. The previous configuration listed the business endpoints as
  `permitAll` and left `anyRequest().authenticated()` covering nothing that existed, so a new
  controller is now protected on the day it is written rather than the day somebody notices.
- **A refusal does not say whether the group exists.** "No access to group 41" is returned
  whether or not group 41 is real, so the endpoint cannot be used to enumerate ids.

---

## Failure modes

| Failure | Behaviour |
|---|---|
| Broker unreachable when an expense is created | Expense still commits; event stays in the outbox and publishes when the broker returns |
| Relay dies mid-publish | Row stays unpublished, next poll resends; consumer dedupe absorbs the duplicate |
| Consumer crashes after DB commit, before offset commit | Record is redelivered; `processed_events` makes it a no-op |
| Malformed event (shares do not sum to amount, missing id) | Non-retryable, routed straight to `expense-created.DLT` and persisted in `poison_messages` — the consumer group keeps moving |
| Transient consumer failure | 3 retries at 2s, then DLT |
| An expense and a payment hit the same balance row at once | Optimistic lock fails one of them; the error handler retries it and the second attempt reads the committed value. The rolled-back attempt takes its `processed_events` row with it, so the retry is not mistaken for a duplicate |
| Entity/schema drift | Application refuses to start (`ddl-auto=validate`) |
| `JWT_SECRET` unset, or shorter than 32 bytes | Application refuses to start rather than signing tokens with a weak or published key |

---

## Running it

```bash
docker compose -f docker/docker-compose.yml up -d
export JWT_SECRET='a-long-random-string-of-at-least-32-bytes'
./mvnw spring-boot:run
```

Flyway applies the schema on startup. Database and broker settings fall back to the compose
defaults, so no setup is needed locally; override via `DB_URL`, `DB_USER`, `DB_PASSWORD` and
`KAFKA_BOOTSTRAP_SERVERS` anywhere else.

`JWT_SECRET` has **no default on purpose**. A deployment that forgets it fails at startup
rather than signing every token with a value published in this repository's git history.

### API

Everything except registration, login and the health probes requires
`Authorization: Bearer <token>`.

```http
POST /auth/register                 # -> 201 with a token
POST /auth/login                    # -> 200 with a token

POST /groups                        # create a group; the creator is its first member
GET  /groups                        # the caller's own groups, and only those
GET  /groups/{groupId}              # one group, if the caller is in it
POST /groups/{groupId}/members      # add a user to the group

POST /expenses                      # create an expense (EQUAL or EXACT split)
GET  /expenses?groupId=&page=&size= # one group's expenses, paged
GET  /balances/{groupId}            # net balances, served from the projection
GET  /settlements/{groupId}         # settlement plan, served from the projection
GET  /groups/{groupId}/settlements  # settlement plan, computed live from the write model
POST /groups/{groupId}/settlements  # record a payment that actually happened -> 202
GET  /groups/{groupId}/payments     # payments already recorded, newest first

GET  /actuator/health | /actuator/prometheus
```

A full round trip:

```bash
TOKEN=$(curl -sX POST localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"a@example.com","password":"correct-horse-battery","displayName":"A"}' \
  | jq -r .token)

curl -X POST localhost:8080/groups -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Trip"}'
```

Equal split — the server does the division, so rounding is handled server-side:

```json
{
  "groupId": 1,
  "paidBy": 1,
  "amount": 10.00,
  "splitType": "EQUAL",
  "participants": [1, 2, 3]
}
```

Exact split — caller supplies shares, which must sum to `amount`:

```json
{
  "groupId": 1,
  "paidBy": 1,
  "amount": 300.00,
  "splitType": "EXACT",
  "shares": [
    { "userId": 1, "amount": 100.00 },
    { "userId": 2, "amount": 100.00 },
    { "userId": 3, "amount": 100.00 }
  ]
}
```

Settling up — `GET` proposes a plan, `POST` records that someone acted on it:

```json
{
  "fromUserId": 2,
  "toUserId": 1,
  "amount": 100.00,
  "note": "paid in cash"
}
```

Returns `202`, not `200`. The payment row is committed synchronously, but the balances it
moves are updated by a consumer after the relay publishes, so `GET /balances/{groupId}`
converges a moment later. Nothing requires a payment to match a leg of the suggested plan,
and overpaying simply flips the payer's net position rather than clamping at zero.

---

## Tests

```bash
./mvnw test      # 66 unit tests, no Docker required
./mvnw verify    # adds Testcontainers integration tests (needs a Docker daemon)
```

The split is deliberate: `mvn test` must stay green on a fresh clone with nothing installed.

Unit tests cover the settlement invariants (every debt clears, `n-1` bound, determinism, no
input mutation) across 200 randomised group configurations, and the allocation invariant
(parts always sum to the total) across a range of awkward amounts.

Integration tests run the real broker and real Postgres to cover what unit tests cannot:
end-to-end convergence of the projections, the outbox draining, redelivery of an identical
event not double-counting, a poison message not stalling the consumer group, and the
settle-up loop clearing a debt it created.

`AuthorizationIT` is the regression net for the access boundary, and asserts the negative
cases rather than only the happy path: every business endpoint refuses an anonymous caller,
a forged token is rejected, a non-member can neither read nor write another group's data, a
member cannot charge a share to somebody outside the group, `GET /groups` returns only the
caller's own groups, and no response ever carries a password hash.

`SchemaDdlGenerator` regenerates `target/generated-schema.sql` from the entity model without
needing a database. The Flyway migration is derived from that output rather than hand-written,
which is what keeps `ddl-auto=validate` honest:

```bash
./mvnw test -Dtest=SchemaDdlGenerator
```

---

## Known limitations

Being explicit about what this does not do:

- **Greedy settlement is not optimal**, as described above.
- **Tokens cannot be revoked before they expire.** Authentication is a stateless signed JWT
  with a 12-hour TTL and no server-side session, so a logout is a client-side delete. Real
  revocation needs either short-lived access tokens with refresh, or a deny-list the filter
  checks — which trades away the stateless property that makes the current design cheap.
- **No rate limiting on login.** Nothing slows down repeated password guesses; BCrypt's work
  factor is the only cost imposed on an attacker.
- **Flat membership.** Any member can add another member or record a payment they are party
  to. There are no roles, no invitations, and no removal.
- **The balance projection can briefly reorder.** Expenses and payments arrive on separate
  topics, so their consumers can commit two mutations to the same group close together and
  then publish their after-commit snapshots in the opposite order, leaving the read model
  showing the earlier of the two. The authoritative `balances` table is always correct —
  optimistic locking guarantees that — and the next mutation to the group repairs the
  projection. Making it self-healing would mean giving the snapshot a monotonic sequence
  and having the projector reject anything older.
- **Single-region assumptions.** No multi-datacenter replication or partition-tolerance story
  beyond what Kafka provides out of the box.
