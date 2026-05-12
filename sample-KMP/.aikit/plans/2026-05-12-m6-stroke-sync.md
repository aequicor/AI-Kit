# M6 — Синхронизация штрихов

**Created:** 2026-05-12
**Branch:** feature/m6-stroke-sync
**Source task:** M6 — real-time синхронизация штрихов между устройствами, конфликты,
оффлайн-очередь.

## Context (digest)

- Строится поверх M5: WebSocket-канал установлен, pairing завершён, WsMessage types определены.
- Модель конфликтов: штрихи append-only → конфликтов при добавлении нет; удаления — tombstone
  с логическим вектором часов (Lamport clock достаточно для двух устройств).
- Last-writer-wins per stroke: Stroke идентифицируется по UUID, timestamp — Lamport clock.
- Оффлайн-очередь: SQLDelight table `pending_sync_events` (strokeId, eventType, payload, clock).
- При reconnect: обмен Lamport clock → host отправляет дельту событий с большим clock.
- Тип сообщения `StrokeAdded(stroke, clock)` и `StrokeDeleted(strokeId, clock)` добавляются в WsMessage.

## Invariants

- Stroke UUID генерируется на устройстве-авторе и не изменяется при синхронизации.
- Применение входящего события идемпотентно (повторная доставка не дублирует штрих).
- Оффлайн-очередь опустошается строго в порядке Lamport clock при reconnect.
- Новые public API в `shared` покрыты unit-тестами.
- Синхронизация не блокирует UI — event loop на `Dispatchers.IO`.

## Steps

### Step 1 — sync-protocol-extension
- **Goal:** добавить в `WsMessage`: `StrokeAdded(documentId, pageIndex, stroke: Stroke, clock: Long)`,
  `StrokeDeleted(documentId, pageIndex, strokeId: String, clock: Long)`,
  `SyncStateRequest(lastKnownClock: Long)`, `SyncStateDelta(events: List<SyncEvent>)`;
  `LamportClock` value class в `shared/domain/sync/`; unit-тесты clock increment/merge.
- **DoD:** `./gradlew.bat :shared:jvmTest` зелёный.
- **Review:** standard
- **What would be wrong:** `clock` передаётся как `Int` → overflow при ~2B событий; используем `Long`.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest"`
- **Expect:** green

### Step 2 — sync-event-queue
- **Goal:** SQLDelight table `pending_sync_events (id, document_id, page_index, event_type,
  stroke_id, stroke_json, clock, created_at)`; `SyncQueueRepository` port + impl;
  `EnqueueSyncEventUseCase`, `DrainSyncQueueUseCase`; unit-тесты с in-memory SQLDelight driver.
- **DoD:** `./gradlew.bat :shared:jvmTest` зелёный.
- **Review:** standard
- **What would be wrong:** `pending_sync_events` не имеет индекса по `clock` → O(N) scan при
  drain после долгого оффлайн-периода; нужен `CREATE INDEX idx_clock ON pending_sync_events(clock)`.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest"`
- **Expect:** green

### Step 3 — host-sync-logic
- **Goal:** `SyncSessionManager` на сервере: при получении `StrokeAdded`/`StrokeDeleted` —
  broadcast всем connected clients (кроме sender); при `SyncStateRequest` — отправить `SyncStateDelta`
  с событиями после `lastKnownClock`; хранить in-memory лог последних 1000 событий на сервере
  (ring buffer), старее — ответить `SyncStateRequest` с `requireFullSync=true`.
- **DoD:** `./gradlew.bat :server:test` зелёный.
- **Review:** standard
- **What would be wrong:** broadcast в цикле по `sessions` без snapshot → ConcurrentModificationException
  при одновременном disconnect; нужен `synchronized` snapshot или `CopyOnWriteArrayList`.
- **Verify:** `shell: "./gradlew.bat :server:test :server:build"`
- **Expect:** green

### Step 4 — client-sync-logic
- **Goal:** `StrokeSyncUseCase` в `shared`: при добавлении/удалении штриха → enqueue + отправить
  WsMessage если online; при получении входящего события → apply to local store (идемпотентно,
  проверка по strokeId); reconnect-flow — отправить `SyncStateRequest(lastClock)`;
  unit-тест: два fake-клиента, verify eventual consistency.
- **DoD:** `./gradlew.bat :shared:jvmTest` зелёный.
- **Review:** standard
- **What would be wrong:** входящий `StrokeAdded` применяется без проверки дубликата →
  один штрих появляется дважды при повторной доставке; нужна idempotency by `stroke.id`.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest"`
- **Expect:** green

### Step 5 — sync-ui-indicator
- **Goal:** `SyncStatusBadge` в toolbar (иконка + текст: «Синхронизировано», «Ожидание...»,
  «Оффлайн: N событий»); `SyncViewModel` с `StateFlow<SyncStatus>`; анимация при активной синхронизации.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug :composeApp:compileKotlinJvm` exit 0.
- **Review:** standard
- **What would be wrong:** `(n/a)` — UI-only шаг, основные риски в steps 3–4.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug :composeApp:compileKotlinJvm"`
- **Expect:** green

## Out of scope

- Operational transformation (LWW per stroke достаточно для данной модели).
- Синхронизация ToolSettings между устройствами.
- Conflict resolution UI (нет конфликтов при append-only модели).
- Server-side persistent storage (in-memory ring buffer достаточно для LAN-сессии).
