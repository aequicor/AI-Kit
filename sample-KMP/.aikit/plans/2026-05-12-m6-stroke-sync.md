# Plan: M6 — Синхронизация штрихов
**id:** `2026-05-12-m6-stroke-sync`
**created:** 2026-05-12
**milestone:** M6 — Real-time синхронизация штрихов, конфликты, оффлайн-очередь
**depends-on:** `2026-05-12-m5-server-discovery`

## Goal
Real-time двунаправленная синхронизация штрихов между устройствами по WebSocket. Штрих — атомарная единица (append-only). Удаления через tombstones с логическими часами. Оффлайн-очередь с автоматической отправкой при восстановлении соединения.

## Steps

### Step 1 — Протокол синхронизации: сообщения WebSocket
**Commit:** `feat(shared/domain): sync protocol messages`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/sync/SyncMessage.kt`
  ```kotlin
  @Serializable
  sealed class SyncMessage {
      @Serializable data class StrokeAdded(val documentId: String, val pageIndex: Int, val stroke: StrokeDto) : SyncMessage()
      @Serializable data class StrokeDeleted(val strokeId: String, val tombstoneTs: Long, val logicalClock: Long) : SyncMessage()
      @Serializable data class SyncRequest(val documentId: String, val fromClock: Long) : SyncMessage()
      @Serializable data class SyncSnapshot(val documentId: String, val layers: List<AnnotationLayerDto>, val clock: Long) : SyncMessage()
      @Serializable data class Ack(val messageId: String) : SyncMessage()
  }
  ```
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/sync/LogicalClock.kt`
  — Lamport clock: `class LogicalClock { fun tick(): Long; fun update(received: Long): Long }`
- DTO-классы: `StrokeDto`, `AnnotationLayerDto` (сериализуемые копии domain моделей)

---

### Step 2 — WebSocket канал в `:server`
**Commit:** `feat(server): WebSocket sync endpoint`

Files:
- `server/src/main/kotlin/io/aequicor/server/routes/SyncRoutes.kt`
  — `webSocket("/sync/{documentId}")` — bidirectional channel
  — сервер хранит `Map<DocumentId, MutableSet<DefaultWebSocketSession>>` (fan-out)
  — при `StrokeAdded` от клиента A: сохраняет в memory state + рассылает всем остальным клиентам
  — при `SyncRequest`: отправляет `SyncSnapshot` с дельтой от `fromClock`
- `server/src/main/kotlin/io/aequicor/server/state/SyncState.kt`
  — in-memory: `Map<DocumentId, Map<PageIndex, List<StrokeDto>>>` + `Map<StrokeId, Tombstone>`
  — thread-safe через `Mutex`

---

### Step 3 — WebSocket клиент в `shared`
**Commit:** `feat(shared/data): WebSocketSyncClient — send/receive stroke stream`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/sync/WebSocketSyncClient.kt`
  — `class WebSocketSyncClient(private val client: HttpClient)`
  — `fun connect(host: DeviceInfo, documentId: PdfDocumentId): Flow<SyncMessage>`
  — `suspend fun send(message: SyncMessage)`
  — автоматический reconnect: exponential backoff (1s, 2s, 4s, max 30s)
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/sync/SyncConnectionState.kt`
  — `sealed class SyncConnectionState { Connected; Reconnecting(attempt); Offline }`

---

### Step 4 — Оффлайн-очередь
**Commit:** `feat(shared/data): offline stroke queue — SQLDelight persistence`

Files:
- `shared/src/commonMain/sqldelight/io/aequicor/pdf/data/SyncQueue.sq`
  — таблица `sync_queue(id, document_id, message_json, created_at, status)`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/sync/OfflineQueue.kt`
  — `class OfflineQueue(private val db: Database)`
  — `suspend fun enqueue(message: SyncMessage)`
  — `suspend fun flush(client: WebSocketSyncClient)` — при восстановлении соединения отправляет накопленное
  — сообщения удаляются из очереди только после `Ack` от сервера

---

### Step 5 — Lamport clock на существующих tombstones
**Commit:** `feat(shared/data): Lamport clock on tombstones — network-aware deletion`

> Таблица `tombstones(stroke_id, document_id, page_index, logical_clock, deleted_at)` уже создана в M3 Step 2. В M3 `logical_clock = 0` для всех записей. Здесь — заполняем реальный clock и реплицируем.

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/sync/TombstoneRepository.kt`
  — при локальном удалении: `LogicalClock.tick()` → INSERT с актуальным значением
  — при получении `StrokeDeleted`: `LogicalClock.update(received)` → UPSERT (если уже существует с меньшим clock — перезаписать)
  — при `SyncSnapshot`: bulk UPSERT всех tombstones из снапшота
- `DrawingViewModel.deleteStroke(id: StrokeId)` обновляется: вместо `tombstones.insert(..., 0)` теперь `tombstones.insert(..., clock.tick())` + отправка `StrokeDeleted`
- Миграция: в M6 при первом запуске после обновления — все существующие tombstones с `logical_clock = 0` сохраняются как есть (initial state), новые удаления получают реальный clock

---

### Step 6 — SyncViewModel
**Commit:** `feat(shared/presentation): SyncViewModel — orchestrates sync lifecycle`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/presentation/SyncViewModel.kt`
  — подписывается на `DrawingViewModel.strokeAdded` Flow → отправляет `StrokeAdded` по WS
  — подписывается на WS Flow → применяет входящие штрихи к `DrawingViewModel` (игнорирует собственные по `deviceId`)
  — управляет `OfflineQueue.flush()` при `Connected`
  — `StateFlow<SyncConnectionState>`

---

### Step 7 — UI: статус синхронизации
**Commit:** `feat(composeApp): sync status indicator + conflict badge`

Files:
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/SyncStatusBar.kt`
  — иконка + текст: "Synced" / "Syncing..." / "Offline (N queued)" / "Reconnecting..."
  — при получении чужого штриха — кратковременная анимация (пульс)
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/PeerCursorOverlay.kt`
  — показывает аватар/имя пира рядом с его последним штрихом (опционально в M6, обязательно в M7)

---

### Step 8 — Unit + integration тесты
**Commit:** `test(shared+server): sync protocol, offline queue, tombstone tests`

- `shared/src/commonTest/kotlin/sync/LogicalClockTest.kt` — Lamport clock monotonicity
- `shared/src/commonTest/kotlin/sync/OfflineQueueTest.kt` — enqueue, flush, ack-based delete
- `shared/src/commonTest/kotlin/sync/TombstoneRepositoryTest.kt` — apply tombstone removes stroke
- `server/src/test/kotlin/SyncWebSocketTest.kt` — fan-out через MockEngine, 2 clients

---

### Step 9 — Docs
**Commit:** `docs: ADR-007 sync strategy + README M6`

- `docs/adr/ADR-007-sync-strategy.md` — LWW vs OT, выбор штриха как атомарной единицы, tombstones, Lamport clock
- `README.md` — M6 секция

## Definition of Done (M6)
- [ ] Android ↔ Desktop: штрихи появляются на обоих устройствах в реальном времени
- [ ] Оффлайн: штрихи нарисованные без сети отправляются при восстановлении
- [ ] Удаление через ластик синхронизируется (tombstone)
- [ ] Конфликтов нет при одновременном рисовании (LWW, append-only)
- [ ] Статус синхронизации отображается корректно
- [ ] Тесты зелёные

## Open questions
1. Нужна ли персистентность sync-state на сервере (при перезапуске сервера — потеря состояния), или in-memory достаточен для M6?
2. Лимит на размер `StrokeAdded` сообщения (штрих с тысячами точек может быть большим)?
