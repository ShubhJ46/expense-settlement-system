# Splitwise Backend – Event‑Driven Architecture

A production‑grade **Splitwise‑like backend** built using **Spring Boot, Kafka, PostgreSQL, and Domain‑Driven Design (DDD)** principles.
This project demonstrates **event‑driven architecture, idempotent consumers, eventual consistency, and read/write model separation**, exactly how such systems are designed in real companies.

---

## 🚀 High‑Level Overview

This backend allows users to:

* Add group expenses
* Automatically compute net balances
* Generate simplified settlements (minimum transactions)
* Handle failures safely using Kafka semantics

The system is **asynchronous, scalable, and resilient**.

---

## 🧠 Core Architectural Concepts

### 1️⃣ Event‑Driven Architecture

Instead of tightly coupling services, **domain events** are published to Kafka:

* `ExpenseCreatedEvent`
* `BalanceUpdatedEvent`
* `SettlementCalculatedEvent`

Each event represents a **fact** that already happened.

---

### 2️⃣ CQRS (Command Query Responsibility Segregation)

| Layer           | Responsibility                         |
| --------------- | -------------------------------------- |
| **Write Model** | Accept commands, validate, emit events |
| **Read Model**  | Build query‑optimized projections      |

This allows:

* Independent scaling
* Eventual consistency
* Simple, fast queries

---

### 3️⃣ Why Kafka?

Kafka is used as the **system backbone**:

* Durable event log
* Replayable history
* At‑least‑once delivery
* Horizontal scalability

All business logic is driven by **events**, not direct calls.

---

## 🧱 Domain Flow (End‑to‑End)

### ➕ Creating an Expense

```text
Client → REST API → DB → Kafka → Consumers
```

1. Client sends `POST /expenses`
2. Expense is persisted in DB
3. `ExpenseCreatedEvent` is published
4. Balance service consumes the event
5. Balances are updated
6. `BalanceUpdatedEvent` is published
7. Query model updates projections

---

### 💰 Balance Calculation

Balances are stored as **net values per user per group**:

* Positive → user should receive money
* Negative → user owes money

This design **automatically cancels cycles** (A→B→C→A).

---

### 🔁 Settlement Calculation

Settlements are derived from net balances:

1. Split users into:

    * Creditors (positive balance)
    * Debtors (negative balance)
2. Greedily match them
3. Generate minimum number of transactions

This mirrors how **real Splitwise works internally**.

---

## 🗂️ Key Components

### 📦 Controllers

* `ExpenseController` – create expenses
* `BalanceController` – fetch balances
* `SettlementController` – fetch settlements

---

### 🧠 Services

* `ExpenseService` – command handling
* `BalanceService` – balance updates (idempotent)
* `SettlementService` – computes settlements

---

### 🧵 Kafka Producers

* `ExpenseEventProducer`
* `BalanceEventProducer`
* `SettlementEventProducer`

Producers publish **domain events**, not DTOs.

---

### 🎧 Kafka Consumers

* `BalanceEventConsumer` – handles expenses
* `BalanceViewConsumer` – builds read model
* `SettlementConsumer` – computes settlements

All consumers are:

* Idempotent
* Transactional
* Safe for reprocessing

---

## 🔒 Idempotency & Safety

### ✔ Consumer Idempotency

Handled using:

* `processed_events` table
* Event ID checks before processing

Ensures:

* Safe retries
* No duplicate balance updates

---

### ✔ Exactly‑Once‑Like Semantics

Using:

* Kafka consumer groups
* Manual offset acknowledgment
* Database transactions

This provides **practical exactly‑once behavior**.

---

## 🧪 Failure Handling

### Poison Messages

Invalid events (schema errors, business rule violations) are:

* Logged
* Can be persisted for analysis
* Do not block the consumer group

---

### Consumer Lag Monitoring

Consumers calculate lag at runtime:

```text
lag = endOffset - currentOffset
```

High lag is logged for alerting and debugging.

---

## 🗄️ Database Design

### Write Tables

* `expenses`
* `expense_shares`
* `balances`
* `processed_events`

### Read Models

* `group_balance_view`
* `settlement_view`

Read models are **derived**, never written directly by APIs.

---

## 🧩 Tech Stack

* **Java 21**
* **Spring Boot 3.x**
* **Spring Kafka**
* **PostgreSQL**
* **Docker & Docker Compose**
* **Hibernate / JPA**

---

## 🧠 Design Decisions (Interview Gold)

* Events over direct service calls
* Net balances instead of debt graphs
* CQRS for scalability
* Kafka as source of truth
* Idempotent consumers
* Eventually consistent read models

---

## 🧪 How to Run Locally

```bash
docker-compose up -d
```

```bash
./mvnw spring-boot:run
```

---

## 📌 Sample APIs

### Create Expense

```http
POST /expenses
```

### Get Balances

```http
GET /balances/{groupId}
```

### Get Settlements

```http
GET /groups/{groupId}/settlements
```

---

## 🎯 What This Project Demonstrates

✅ Real‑world event‑driven backend design
✅ Kafka mastery (consumers, offsets, lag)
✅ Clean separation of concerns
✅ Interview‑ready system explanation

---

## 🙌 Final Note

This project intentionally prioritizes **clarity, correctness, and architecture** over premature optimization.

It reflects how production systems like **Splitwise, Uber, and Stripe** are actually built.

---

**Author:** Shubham Jain
**Purpose:** Learning, interviews, and system design mastery
