# Simplify Money — Ledger Sync Submission

Repository: adarshdixit989/simplify-money-ledger-sync

The complete working submission bundle is the ZIP prepared alongside this repository. This repository is the public GitHub workspace for the submission.

## Included work
- Task 2: corpus ingestion, normalization, deduplication, categories, reports and reconciliation.
- Task 3: whole-rupee amount parsing incident fix and regression coverage.
- Task 4: MongoDB document store, backfill and consistency-check design.
- Task 0 / Task 1: candidate-supplied app screenshots and teardown documents are in the prepared submission bundle.

## Verification
- `./verify.sh` is the dependency-free smoke pipeline.
- `./gradlew test` runs the Java tests.
- `docker compose up -d` starts MongoDB for Task 4.
- `./gradlew run --args="benchmark"` runs the document-store benchmark.

## Honesty / remaining runtime verification
The MongoDB benchmark values in the prepared README are explicitly marked as derived/preparation-environment values where Docker/MongoDB runtime execution was unavailable. They should be replaced with actual `explain.executionStats` output before a final submission if runtime Docker is available.
