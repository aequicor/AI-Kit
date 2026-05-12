# ADR-002 — Stroke storage format

**Status:** Accepted  
**Date:** 2026-05-12  
**Milestone:** M2 — Stylus Input

---

## Context

Each annotated PDF page produces a list of `Stroke` objects. Each stroke contains a `DrawingTool` and a list of `StrokePoint` (x, y, pressure, tiltX, tiltY, timestamp). We need to:

1. Persist strokes locally so they survive app restarts.
2. Retrieve them quickly when a page is scrolled into view.
3. Keep the implementation simple enough to ship in M2 and refactor in a later milestone.

Candidate serialization formats evaluated: **JSON**, **Protobuf (kotlinx-serialization-protobuf)**, **SQLite BLOB (raw bytes)**.

---

## Decision

### Format: JSON (kotlinx-serialization-json)

JSON is chosen for M2. Rationale:

| Criterion | JSON | Protobuf | Raw BLOB |
|---|---|---|---|
| Schema evolution (add field) | safe (`ignoreUnknownKeys = true`) | safe (field numbers stable) | breaks without versioning |
| Human-readable / debuggable | ✓ | ✗ | ✗ |
| Wire size | ~3× Protobuf | smallest | medium |
| KMP support | `kotlinx-serialization-json` (stable) | `kotlinx-serialization-protobuf` (experimental in 1.7.x) | manual |
| Implementation effort | low | medium | high |

Wire size is not a bottleneck in M2: a typical handwritten page produces ~500–2000 points × ~60 bytes JSON each ≈ 30–120 KB per page, comfortably within local storage limits.

**Migration path:** `kotlinx-serialization-protobuf` can replace JSON in a future milestone with a one-time migration (read JSON, write Protobuf) using `ignoreUnknownKeys` for backward compatibility. No schema changes are needed.

### Index: SQLDelight (SQLite)

SQLDelight manages a lightweight `annotation` table with `(documentId, pageIndex)` as the composite primary key:

```sql
CREATE TABLE annotation (
    documentId TEXT NOT NULL,
    pageIndex  INTEGER NOT NULL,
    strokeCount INTEGER NOT NULL,
    strokesJson TEXT NOT NULL DEFAULT '[]',
    PRIMARY KEY (documentId, pageIndex)
);
```

The `strokesJson` column holds the full serialised layer inline. This differs from the original plan's "JSON files + filePath pointer" approach.

**Why inline vs. files:**  
Separate files require an `expect`/`actual` file-system abstraction across Android, JVM, iOS, and JS — roughly 5 platform files with path-resolution logic. Inline storage eliminates that surface, keeps the transaction atomic (no partial writes), and is sufficient for M2 page sizes.

A `document` table is also created for future document-listing features (not yet surfaced in the UI).

### Drivers per platform

| Platform | SQLDelight driver |
|---|---|
| Android | `AndroidSqliteDriver` |
| JVM (Desktop) | `JdbcSqliteDriver` (file `pdfkit.db`, `migrateEmptySchema = true`) |
| iOS | `NativeSqliteDriver` |
| JS | `NotImplementedError` stub — web storage deferred to M3 |

---

## Consequences

- **Positive:** simple, debuggable, works on all targeted platforms in M2.
- **Positive:** `ignoreUnknownKeys = true` means adding fields to `StrokePointDto` / `StrokeDto` is always backward-compatible.
- **Negative:** JSON is ~3× larger than Protobuf. Acceptable for local storage but may matter if strokes are ever synced over a network (addressed in M6 — server sync).
- **Negative:** `strokesJson` is loaded fully into memory per page. Pages with thousands of strokes will require a chunk-based approach (tracked as a future improvement).
- **Note:** wasmJs target removed in M2 because SQLDelight 2.3.2 does not ship wasmJs artifacts for `runtime` and `coroutines-extensions`. Re-enable after a SQLDelight release that supports wasmJs.
