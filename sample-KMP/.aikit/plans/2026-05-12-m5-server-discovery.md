# M5 — Сервер и Discovery

**Created:** 2026-05-12
**Branch:** feature/m5-server-discovery
**Source task:** M5 — Ktor-сервер, mDNS/Bonjour discovery, авторизация пары (QR / 6-значный код),
передача документов (chunked file transfer).

## Context (digest)

- `server/` уже использует Ktor 3.4.3 (Netty). Нужно добавить WebSocket, kotlinx.serialization.
- Одно устройство запускает embedded Ktor server (роль host); остальные — Ktor-клиент из shared.
- Ktor-клиент (`ktor-client-core`) — KMP, движок: OkHttp (Android), CIO (Desktop/iOS/Server).
- mDNS: Android → `android.net.nsd.NsdManager`; Desktop → JmDNS; iOS → `Network.framework` (Assumption: macOS).
- Авторизация: host генерирует 6-значный код; клиент вводит код; после верификации обмен
  fingerprint сертификата (self-signed TLS); дальнейшее общение только через pinned cert.
- Документы: chunked transfer бинарными WebSocket-фреймами (64 KB chunk), offset + total в header.
- QR-код: генерация через `qrcode-kotlin` (KMP-совместимая) или zxing-android-embedded (Android).

## Invariants

- WebSocket-протокол (типы сообщений, поля) определён в `shared/domain/network/` — единый source of truth.
- TLS self-signed сертификат генерируется при первом запуске сервера; fingerprint — строка для ручного сравнения.
- File transfer не блокирует UI-thread и поддерживает отмену через корутины.
- Новые public API в `shared` покрыты unit-тестами.
- `composeApp` логика discovery изолирована в expect/actual — NsdManager / JmDNS / NetService.

## Steps

### Step 1 — ws-protocol-shared
- **Goal:** `shared/domain/network/` — sealed class `WsMessage` (kotlinx.serialization @Serializable):
  `PairingRequest`, `PairingChallenge`, `PairingAck`, `DocumentChunkHeader`,
  `DocumentChunk`, `DocumentComplete`, `ErrorMessage`;
  `WsMessageSerializer` (Json config); unit-тесты round-trip сериализации всех типов.
- **DoD:** `./gradlew.bat :shared:jvmTest` зелёный.
- **Review:** standard
- **What would be wrong:** `WsMessage` sealed class с `@Serializable` без `@SerialName` на каждом
  subtype → десериализация по имени класса ломается при ProGuard/obfuscation.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest"`
- **Expect:** green

### Step 2 — ktor-client-shared
- **Goal:** добавить `ktor-client-core`, `ktor-client-cio`, `ktor-client-content-negotiation`,
  `ktor-serialization-kotlinx-json` в `libs.versions.toml` + `shared/commonMain`;
  platform-specific engine deps (`ktor-client-okhttp` в androidMain, `ktor-client-cio` в jvmMain/iosMain);
  `NetworkClient` wrapper в `shared/data/network/`.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug :shared:compileKotlinJvm` exit 0.
- **Review:** heavy
- **What would be wrong:** ktor-client-okhttp в androidMain конфликтует с okhttp3 из другой зависимости
  — DuplicateClass; ktor-client-cio для iOS требует Darwin-engine вместо CIO.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug :shared:compileKotlinJvm"`
- **Expect:** green
- **Shape:**
    - **files-glob:** `**/{libs.versions.toml,build.gradle.kts}`
    - **max-diff-lines:** 80
    - **no-test-changes:** true

### Step 3 — server-setup
- **Goal:** в `server/`: добавить `ktor-server-websockets`, `ktor-server-content-negotiation`,
  `ktor-serialization-kotlinx-json`, TLS config (self-signed cert через `keytool` на first-run);
  `WsSessionManager` (подключённые клиенты, broadcast); WebSocket endpoint `/ws`;
  unit-тест `ApplicationTest` с `testApplication`.
- **DoD:** `./gradlew.bat :server:test` зелёный.
- **Review:** standard
- **What would be wrong:** TLS self-signed cert генерируется заново при каждом старте сервера →
  fingerprint меняется, уже paired клиенты отключаются.
- **Verify:** `shell: "./gradlew.bat :server:test :server:build"`
- **Expect:** green

### Step 4 — pairing-flow
- **Goal:** `PairingUseCase` в `shared/domain/usecase/`: генерация 6-значного кода (host),
  верификация кода (client), обмен fingerprint; `PairingViewModel` + `PairingScreen`
  (host показывает код + QR, client вводит код); QR-генерация через `qrcode-kotlin` в commonMain.
- **DoD:** `./gradlew.bat :shared:jvmTest :composeApp:assembleDebug` зелёный.
- **Review:** standard
- **What would be wrong:** 6-значный код не имеет TTL и принимается неограниченное число раз →
  brute-force паринга; нужен rate-limit (3 попытки, потом 30с cooldown) и TTL 60с.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest :composeApp:assembleDebug"`
- **Expect:** green

### Step 5 — mdns-android
- **Goal:** `AndroidMdnsDiscovery` в `composeApp/androidMain` через `NsdManager`;
  register service (host mode), discover services (client mode);
  `MdnsDiscoveryPort` expect/actual interface в `shared`; `Flow<List<PeerDevice>>`.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug` exit 0.
- **Review:** standard
- **What would be wrong:** `NsdManager.discoverServices` callback приходит на произвольном thread
  → обращение к UI State без `withContext(Main)` → race condition.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug"`
- **Expect:** green

### Step 6 — mdns-desktop
- **Goal:** `JvmMdnsDiscovery` в `composeApp/jvmMain` через JmDNS (добавить dependency);
  добавить `jmdns` в libs.versions.toml + jvmMain.dependencies; register + browse service.
- **DoD:** `./gradlew.bat :composeApp:compileKotlinJvm` exit 0.
- **Review:** standard
- **What would be wrong:** JmDNS создаёт background thread без daemon=true → JVM не завершается
  при закрытии приложения; нужен `jmdns.close()` в application shutdown hook.
- **Verify:** `shell: "./gradlew.bat :composeApp:compileKotlinJvm"`
- **Expect:** green

### Step 7 — mdns-ios
- **Goal:** `IosMdnsDiscovery` в `composeApp/iosMain` через `Network.framework`
  (`NWBrowser`/`NWListener` через cinterop); register + browse `_pdfeditor._tcp`.
- **DoD:** Assumption: requires macOS.
  `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` exit 0.
- **Review:** standard
- **What would be wrong:** `NWBrowser` callback thread — нужен dispatch на Main queue для Flow emit.
- **Verify:** `shell: "./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64"`
- **Expect:** green
- **Assumptions:** macOS host required.

### Step 8 — document-transfer
- **Goal:** `DocumentTransferUseCase` (host: send file in 64KB chunks via WS binary frames;
  client: receive + reassemble + verify SHA-256 checksum); прогресс через `Flow<TransferProgress>`;
  resume support: client отправляет уже полученный offset; unit-тест с fake WS channel.
- **DoD:** `./gradlew.bat :shared:jvmTest :composeApp:assembleDebug` зелёный.
- **Review:** standard
- **What would be wrong:** transfer не отменяется при disconnect → goroutine/coroutine leak на
  сервере; отсутствует checksum верификация → повреждённый PDF незамечен.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest :composeApp:assembleDebug"`
- **Expect:** green

## Out of scope

- TLS с external CA (self-signed достаточно для LAN).
- Bluetooth transport (только WiFi/LAN).
- Peer-to-peer без сервера (всегда один host).
- Pre-shared key (только 6-digit + QR в M5).
