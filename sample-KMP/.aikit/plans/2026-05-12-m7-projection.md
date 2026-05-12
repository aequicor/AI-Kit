# Plan: M7 — Режим проекции
**id:** `2026-05-12-m7-projection`
**created:** 2026-05-12
**milestone:** M7 — Трансляция viewport + штрихов в real-time, роли host/viewer
**depends-on:** `2026-05-12-m6-stroke-sync`

## Goal
Режим «ведущий → ведомые»: host транслирует текущую страницу, viewport (зум + смещение), указку и активный штрих. Ведомые отображают зеркально. Возможность «отстегнуться» в свободный режим и снова «прицепиться». Целевая задержка ≤100 мс в LAN.

## Steps

### Step 1 — Протокол проекции
**Commit:** `feat(shared/domain): projection protocol messages`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/projection/ProjectionMessage.kt`
  ```kotlin
  @Serializable
  sealed class ProjectionMessage {
      @Serializable data class ProjectionFrame(
          val page: Int,
          val viewport: ViewportDto,
          val pointer: PointerDto?,
          val strokeDelta: StrokeDto?    // текущий активный штрих (неполный)
      ) : ProjectionMessage()
      @Serializable data class ProjectionStarted(val hostId: String) : ProjectionMessage()
      @Serializable data class ProjectionStopped(val hostId: String) : ProjectionMessage()
      @Serializable data class ViewerDetached(val viewerId: String) : ProjectionMessage()
      @Serializable data class ViewerAttached(val viewerId: String) : ProjectionMessage()
  }
  ```
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/projection/ViewportDto.kt`
  — `data class ViewportDto(val scale: Float, val offsetX: Float, val offsetY: Float)`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/projection/PointerDto.kt`
  — `data class PointerDto(val x: Float, val y: Float, val visible: Boolean)`

---

### Step 2 — WebSocket endpoint для проекции в `:server`
**Commit:** `feat(server): projection WebSocket endpoint + frame throttling`

Files:
- `server/src/main/kotlin/io/aequicor/server/routes/ProjectionRoutes.kt`
  — `webSocket("/projection/{sessionId}")` — host отправляет, viewers получают fan-out
  — throttle: сервер пропускает `ProjectionFrame` раз в 16 мс (≤60 fps) — не буферизует, дропает старые
  — отдельный канал от `/sync` чтобы не блокировать синхронизацию штрихов
- `server/src/main/kotlin/io/aequicor/server/state/ProjectionState.kt`
  — `Map<SessionId, HostSession>` где `HostSession = { hostSocket, viewers: Set<Socket> }`

---

### Step 3 — ProjectionSender (host side)
**Commit:** `feat(shared/data): ProjectionSender — throttled frame broadcast`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/projection/ProjectionSender.kt`
  — `class ProjectionSender(private val client: HttpClient)`
  — принимает `Flow<ProjectionFrame>` от `ProjectionViewModel`
  — throttle через `conflate()` + `debounce(16)` — только последний фрейм за 16 мс отправляется
  — `fun startProjection(host: DeviceInfo, sessionId: String)`
  — `fun stopProjection()`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/projection/ProjectionReceiver.kt`
  — `class ProjectionReceiver` — подключается как viewer
  — возвращает `Flow<ProjectionMessage>`

---

### Step 4 — ProjectionViewModel
**Commit:** `feat(shared/presentation): ProjectionViewModel — host + viewer state machine`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/presentation/ProjectionViewModel.kt`
  — `sealed class ProjectionRole { Host; Viewer(val hostId: String); Detached }`
  — `StateFlow<ProjectionRole>`
  — Host mode: подписывается на `ViewportState` + `DrawingViewModel.activeStroke` → собирает `ProjectionFrame` → отправляет через `ProjectionSender`
  — Viewer mode: получает `ProjectionFrame` → обновляет локальный `ViewportState` и `activeStroke`
  — `fun detach()` → переходит в `Detached`, сохраняет текущий viewport
  — `fun reattach()` → запрашивает последний `ProjectionFrame` от сервера и синхронизируется

---

### Step 5 — Viewer UI
**Commit:** `feat(composeApp): viewer mode — mirrored viewport + remote pointer`

Files:
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/projection/ViewerModeOverlay.kt`
  — в Viewer mode: `PdfViewerScreen` использует `ViewportState` из `ProjectionViewModel` (read-only)
  — полупрозрачный баннер «Просмотр (Viewer)» с кнопками «Отстегнуться» и «Вернуться»
  — пользовательский ввод заблокирован (стилус/жесты не работают на страницах)
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/projection/RemotePointerOverlay.kt`
  — рисует `Canvas` с цветным кружком на позиции `PointerDto` с плавной интерполяцией (`animateOffset`)
  — fade-out через 2 сек если `visible = false`
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/projection/LiveStrokeOverlay.kt`
  — отображает `strokeDelta` из `ProjectionFrame` поверх страницы как in-progress штрих
  — при получении полного штриха из sync-канала — убирает delta overlay

---

### Step 6 — Host UI
**Commit:** `feat(composeApp): host projection mode — start/stop + viewers list`

Files:
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/projection/ProjectionHostControls.kt`
  — кнопка «Начать проекцию» → запускает `ProjectionViewModel.startHosting()`
  — список подключённых viewers с именами устройств
  — кнопка «Остановить»
- pointer broadcasting: `Modifier.pointerInput` в `PdfAnnotationOverlay` отправляет текущие координаты в `ProjectionViewModel`

---

### Step 7 — Latency optimization
**Commit:** `perf: projection latency — binary frame encoding, socket priority`

- `ProjectionFrame` кодируется в компактный binary формат (manual `ByteArray` pack: 4 floats + 1 int = 20 bytes) вместо JSON для снижения размера фрейма
- Ktor WebSocket: `pingInterval = 5.seconds`, `maxFrameSize = 64KB`
- Измерение round-trip: `ProjectionFrame` содержит `sentTs: Long`, viewer логирует `receivedTs - sentTs` → Kermit лог

---

### Step 8 — Unit + integration тесты
**Commit:** `test(shared+server): projection session, frame throttle, role transitions`

- `ProjectionViewModelTest` — Host→Viewer→Detached→Viewer state machine
- `ProjectionSenderTest` — conflate: 10 frames за 16мс → 1 отправленный
- `server/ProjectionWebSocketTest` — fan-out к двум viewers, throttle
- `RemotePointerTest` — координатное преобразование (viewport scale + offset)

---

### Step 9 — Docs
**Commit:** `docs: ADR-008 projection protocol + README M7`

- `docs/adr/ADR-008-projection.md` — binary vs JSON, throttle strategy, detach/reattach design
- `README.md` — M7 секция

## Definition of Done (M7)
- [ ] Android (host) → Desktop (viewer): viewport mirror с задержкой <100 мс в LAN
- [ ] Удалённый указатель виден на viewer
- [ ] Активный штрих хоста виден на viewer до его завершения
- [ ] Viewer может отстегнуться, листать самостоятельно, прицепиться обратно
- [ ] Остановка проекции хостом корректно уведомляет viewers
- [ ] Тесты зелёные

## Open questions
1. Нужно ли показывать аватары нескольких хостов одновременно (multi-host projection), или только 1 host в M7?
2. При detach — viewer получает право рисовать (его штрихи синхронизируются через M6-канал)?
