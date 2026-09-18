# Simplify Money — Ledger Sync Take-home

Java backend submission for the Simplify Money Software Engineer/Intern assignment.

## Task 4 — MongoDB document store

Implemented:
- `MongoDocumentStore` with one document per logical transaction.
- Compound index `(accountLast4, occurredAt)` for account/month newest-first reads.
- Multikey index on `sourceMessageIds` for message-id lookup.
- Materialized `category_totals` document per account for category totals.
- Idempotent upsert using a stable logical transaction identity.
- SQL → Mongo backfill with duplicate collapsing so reruns are safe.
- Field-level `ConsistencyChecker` for missing, extra, and changed transaction fields.
- Deterministic 100,000-transaction benchmark using Mongo `explain.executionStats`.

### Benchmark

The deterministic generator targets the three required reads: account/month newest-first, account category totals, and message id → transaction.

Runtime MongoDB execution was not available in the preparation environment, so no unverified runtime numbers are presented as measured facts. The benchmark code is included in `DocumentStoreBenchmark`.

### Run

Start MongoDB with `docker compose up -d`, then run the benchmark using the Gradle wrapper from the assignment package.

## Other submission material

Task 2 outputs, Task 3 incident material, Task 0/1 evidence, CV, and supporting files are included in the prepared submission ZIP.

AI was used for implementation review, debugging, test design, and documentation; concrete AI mistakes are documented in the full submission README.

## Submission

Repository: https://github.com/adarshdixit989/simplify-money-ledger-sync
