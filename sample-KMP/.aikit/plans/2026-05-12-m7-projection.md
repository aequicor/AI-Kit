# M7 — Режим проекции (Mirroring)

**Created:** 2026-05-12
**Branch:** feature/m7-projection
**Source task:** M7 — трансляция viewport + штрихов в реальном времени, роли host/viewer,
возможность viewer отстегнуться и снова прицепиться.

## Context (digest)

- Строится поверх M6: WebSocket-канал стабильный, синхронизация штрихов работает.
- Projection — отдельный тип WsMessage поверх того же канала.
- `ProjectionFrame { page: Int, viewport: ViewportState, pointer: PointerPos?, stroke_delta: StrokeEvent? }`
- Частота: при движении стилуса — до 60Hz; при idle — 1Hz keepalive.
- Viewer в locked-режиме не принимает input (только просмотр); в free-режиме — полный доступ.
- Latency target: ≤100мс в LAN (достигается через оптимистичный UI + низкий overhead WS-фреймов).
- `ViewportState { pageIndex, offsetX, offsetY, scale }` — нормализован в PDF-координатах.

## Invariants

- `ProjectionFrame` отправляется только при изменении состояния (delta, не polling).
- Viewer в locked-режиме не может изменять PDF-документ (input заблокирован на уровне ViewModel).
- Отстыковка viewer — мгновенная (локальный флаг), не требует round-trip к host.
- Новые public API в `shared` покрыты unit-тестами.
- `server/` не хранит историю ProjectionFrame (stateless relay).

## Steps

### Step 1 — projection-protocol
- **Goal:** добавить в `WsMessage`: `ProjectionFrame(pageIndex, offsetX, offsetY, scale,
  pointerX: Float?, pointerY: Float?, strokeDelta: StrokeSyncEvent?)`,
  `ProjectionStart(hostDeviceId)`, `ProjectionStop`, `ProjectionDetach`, `ProjectionAttach`;
  unit-тесты сериализации всех новых типов.
- **DoD:** `./gradlew.bat :shared:jvmTest` зелёный.
- **Review:** standard
- **What would be wrong:** `pointerX/Y` в экранных координатах вместо PDF-нормализованных →
  pointer indicator на viewer отображается неверно при разных разрешениях экранов.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest"`
- **Expect:** green

### Step 2 — host-projection-sender
- **Goal:** `ProjectionSender` в `shared/domain/projection/`: подписка на `ViewerStateFlow` +
  `StylusEventFlow`; throttle 60Hz через `conflate()` + `sample(16ms)`; при изменении
  pageIndex/viewport/pointer — emit `ProjectionFrame`; при новом штрихе — включить `strokeDelta`;
  server relay: `WsSessionManager.broadcastToProjectionViewers`.
- **DoD:** `./gradlew.bat :shared:jvmTest :server:test` зелёный.
- **Review:** standard
- **What would be wrong:** `conflate()` без `sample()` пропускает слишком много frames при быстром
  движении; `sample()` без `conflate()` создаёт backpressure buffer → latency растёт.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest :server:test"`
- **Expect:** green

### Step 3 — server-projection-relay
- **Goal:** `WsSessionManager` расширить: различать роли сессий (host / projection-viewer / sync-only);
  при получении `ProjectionFrame` от host — relay всем projection-viewers; при `ProjectionStart` —
  перевести текущую сессию в host-режим; broadcast без хранения (stateless relay).
- **DoD:** `./gradlew.bat :server:test` зелёный.
- **Review:** standard
- **What would be wrong:** relay копирует WS-фрейм через сериализацию → десериализуем и
  ресериализуем на каждый forward; лучше forward raw bytes — избегаем лишней аллокации.
- **Verify:** `shell: "./gradlew.bat :server:test :server:build"`
- **Expect:** green

### Step 4 — viewer-projection-receiver
- **Goal:** `ProjectionReceiver` в `shared/domain/projection/`: при `ProjectionFrame` →
  обновить `ViewerStateFlow` (page + viewport + pointer); при `strokeDelta` → apply to local store;
  в locked-режиме — input interceptor блокирует touch events через `Modifier.pointerInput { consumeAllChanges() }`;
  `ProjectionViewModel` с `isFollowing: StateFlow<Boolean>`.
- **DoD:** `./gradlew.bat :shared:jvmTest :composeApp:assembleDebug` зелёный.
- **Review:** standard
- **What would be wrong:** viewer применяет `strokeDelta` без idempotency-check → дублирование
  штрихов если тот же stroke уже пришёл через M6 sync-канал.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest :composeApp:assembleDebug"`
- **Expect:** green

### Step 5 — pointer-indicator-ui
- **Goal:** `PointerIndicatorOverlay` — отображение позиции курсора host на viewer (анимированный
  кружок/стрелка); плавное движение через `Animatable`; скрытие при отсутствии pointer >2с;
  не отображается на самом host-устройстве.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug :composeApp:compileKotlinJvm` exit 0.
- **Review:** standard
- **What would be wrong:** `(n/a)` — UI-only шаг.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug :composeApp:compileKotlinJvm"`
- **Expect:** green

### Step 6 — detach-attach-ui
- **Goal:** кнопка «Отстегнуться» / «Прицепиться» в toolbar viewer'а; при detach — `isFollowing=false`,
  ProjectionReceiver перестаёт применять viewport frames (но штрихи продолжают синхронизироваться);
  при attach — `ProjectionAttach` WsMessage + немедленный `ViewportSyncRequest` (получить текущее
  состояние host); badge «Следую за <DeviceName>» / «Свободный режим».
- **DoD:** `./gradlew.bat :composeApp:assembleDebug :composeApp:compileKotlinJvm` exit 0.
- **Review:** standard
- **What would be wrong:** при attach viewer не запрашивает текущий viewport → остаётся на своей
  странице до следующего ProjectionFrame от host.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug :composeApp:compileKotlinJvm"`
- **Expect:** green

## Out of scope

- Несколько одновременных host'ов (один host per session).
- Запись сессии проекции (screen capture).
- Projection через интернет (только LAN).
- Лазерная указка / spotlight эффект (M8, опционально).
