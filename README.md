# Simplify Money — Ledger Sync Take-home

Java backend submission for the Simplify Money Software Engineer/Intern (Backend, Java) assignment.

## Task 4 — MongoDB document store

I chose MongoDB because the three required reads map directly to a transaction document, an account/time compound index, a message-id multikey index, and a small materialized category-total document per account.

Implemented:
- `MongoDocumentStore` with one document per logical transaction.
- Compound index `(accountLast4, occurredAt)` for account/month newest-first reads.
- Multikey index on `sourceMessageIds` for message-id lookup.
- Materialized `category_totals` document per account.
- Idempotent upsert using a deterministic logical transaction identity.
- SQL → Mongo backfill with duplicate collapsing and rerunnable writes.
- Field-level `ConsistencyChecker` for missing, extra, and changed transaction fields.
- Deterministic 100,000-document MongoDB benchmark.

### Document model

`transactions` stores:
- `_id`: deterministic logical transaction identity
- `accountLast4`
- `occurredAt`
- `direction`
- `amount`: BSON Decimal128
- `category`
- `merchant`
- `sourceMessageIds`

Indexes:
- `accountLast4_1_occurredAt_-1`
- `sourceMessageIds_1`
- unique `_id`

`category_totals` stores materialized per-account totals for SPEND, INCOME, MICRO and TRANSFER.

### Runtime benchmark — 100,000 transactions

The benchmark dataset was loaded into local MongoDB 7.0.43 and the required queries were executed with `explain("executionStats")`.

| Required query | totalDocsExamined | nReturned |
|---|---:|---:|
| Account + month, newest-first (account 1234, July 2026) | **8,333** | **8,333** |
| Category totals (account 1234) | **33,334** | **3 final groups** |
| Message ID → transaction (`benchmark-msg-50000`) | **1** | **1** |

Additional observed execution details:
- Q1 used `accountLast4_1_occurredAt_-1`; 8,333 keys and documents examined; 17 ms.
- Q2 used the account/time index; 33,334 keys and documents examined. The cursor produced 33,334 source documents and the final `$group` produced 3 category totals; 62 ms.
- Q3 used `sourceMessageIds_1`; 1 key and 1 document examined; 1 ms.

These values are actual MongoDB `executionStats` observations from the local 100,000-document benchmark, not estimates.

### Start MongoDB

```bash
docker compose up -d
```

The compose file starts MongoDB 7 on port 27017. The default database is `ledger_sync`.

## Task 3 — Incident

The incident fix addresses the parser confusing an available balance with a whole-rupee transaction amount. Amount extraction now accepts both whole-rupee and decimal values while balance extraction remains balance-specific.

The regression test is:
`src/test/java/in/simplifymoney/ledgersync/Task3IncidentTest.java`

The five-line incident note is:
`incident/INC-2026-09-11-five-line-note.txt`

## Task 2

The implementation separates real transaction events from uploaded messages, preserves source message IDs, uses explicit categories, and keeps reruns idempotent.

## Decision log

1. Use Java/JDK-first code to keep the ingest core dependency-light.
2. Group evidence before creating ledger transactions because one transaction can have multiple uploaded messages.
3. Use account + bank transaction time + direction + amount + normalized merchant as the logical event identity.
4. Use exact decimal arithmetic for money.
5. Detect MICRO from qualifying UPI debits at or below ₹100.
6. Match own-account transfer legs so transfers are not counted as spend/income.
7. Keep reconciliation explicit rather than inventing transactions for unexplained movements.
8. Choose MongoDB because its indexes and document model map directly to the three required reads.
9. Make backfill idempotent with the same deterministic transaction identity.
10. Make consistency checking field-level rather than comparing only row counts.

## AI disclosure

AI was used for implementation review, debugging, test design and documentation. One concrete correction was rejecting an AI suggestion to infer an additional Task 1 defect and force corpus totals to match a checkpoint. The source evidence was checked instead, and the implementation preserves an honest reconciliation difference rather than fabricating a transaction or defect.

## Submission material

Task 0/1 documentation, Task 2 outputs, Task 3 incident material, CV and supporting evidence belong in the submission bundle.

Repository:
https://github.com/adarshdixit989/simplify-money-ledger-sync
