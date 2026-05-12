# Plan: M5 — Сервер и Discovery
**id:** `2026-05-12-m5-server-discovery`
**created:** 2026-05-12
**milestone:** M5 — Ktor-сервер, mDNS, парное подключение, передача документов
**depends-on:** `2026-05-12-m4-pdf-export`

## Goal
Одно устройство запускает embedded Ktor-сервер (host). Остальные обнаруживают его через mDNS и подключаются. Авторизация через 6-значный PIN. TLS с self-signed сертификатом и fingerprint pinning. Chunked передача PDF-файлов.

## Steps

### Step 1 — Network доменные модели в `shared`
**Commit:** `feat(shared/domain): network models — DeviceInfo, PairingCode, TransferSession`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/network/DeviceInfo.kt`
  — `data class DeviceInfo(val id: DeviceId, val name: String, val address: String, val port: Int)`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/network/PairingCode.kt`
  — `@JvmInline value class PairingCode(val value: String)` + `fun generate(): PairingCode` (6 цифр)
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/network/TransferSession.kt`
  — `data class TransferSession(val id: String, val documentId: PdfDocumentId, val totalChunks: Int, val receivedChunks: Int)`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/network/NetworkMessage.kt`
  — `@Serializable sealed class NetworkMessage` — базовый тип для всех WS-сообщений

---

### Step 2 — Ktor-сервер в модуле `:server`
**Commit:** `feat(server): Ktor server — pairing, document transfer endpoints`

Files:
- `server/src/main/kotlin/io/aequicor/server/ServerApplication.kt` — рефактор `Application.kt`
- `server/src/main/kotlin/io/aequicor/server/routes/PairingRoutes.kt`
  — `POST /pair/request` → генерирует и возвращает `PairingCode`
  — `POST /pair/confirm { code }` → подтверждает, возвращает session token
- `server/src/main/kotlin/io/aequicor/server/routes/DocumentRoutes.kt`
  — `GET /documents` → список документов (JSON)
  — `POST /documents/{id}/transfer/start` → инициирует chunked transfer
  — `POST /documents/{id}/transfer/{chunk}` → принимает chunk (binary body)
  — `GET /documents/{id}/transfer/status` → прогресс
- `server/src/main/kotlin/io/aequicor/server/plugins/TlsPlugin.kt`
  — self-signed cert генерируется при старте через BouncyCastle (`X509v3CertificateBuilder` + `JcaContentSignerBuilder`), RSA-2048, валиден 1 год
  — сертификат сохраняется в app storage (PKCS12 keystore с генерированным паролем в OS keychain)
  — fingerprint (SHA-256) публикуется в mDNS TXT record

Catalog additions:
```toml
bouncycastle = { module = "org.bouncycastle:bcpkix-jdk18on", version = "1.78" }
```
Добавляется в `server` (JVM) и `shared/jvmMain` (для Desktop host-mode). Android: bouncycastle уже встроен в системе, можно использовать `android.security.KeyPairGeneratorSpec` + `android.security.X500Principal` без сторонних библиотек — но проще использовать тот же BouncyCastle для единого кода. iOS host-mode из M5 исключён (генерация в M7).

---

### Step 3 — TLS + fingerprint pinning
**Commit:** `feat(shared): TLS certificate generation + fingerprint pinning`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/network/TlsCertificate.kt`
  — `data class TlsCertificate(val fingerprint: String)` — хранит pinned fingerprint после первого соединения
- `shared/src/jvmMain/kotlin/io/aequicor/pdf/data/network/TlsManager.jvm.kt`
  — генерация self-signed через `java.security.KeyPairGenerator` + BouncyCastle (уже в Ktor)
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/network/FingerprintValidator.kt`
  — при первом подключении: показать fingerprint пользователю, сохранить; при повторных — валидировать

---

### Step 4 — mDNS Discovery: `expect`/`actual`
**Commit:** `feat(shared): mDNS discovery — NsdManager (Android), JmDNS (JVM), Network.framework (iOS), server-registry (JS)`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/network/DeviceDiscovery.kt`
  — `interface DeviceDiscovery { fun startAdvertising(info: DeviceInfo); fun startDiscovery(): Flow<List<DeviceInfo>>; fun stop() }`
- `shared/src/androidMain/kotlin/io/aequicor/pdf/data/network/NsdDeviceDiscovery.kt`
  — `NsdManager.registerService` + `NsdManager.discoverServices("_pdfkit._tcp")`
- `shared/src/jvmMain/kotlin/io/aequicor/pdf/data/network/JmDnsDeviceDiscovery.kt`
  — `JmDNS.create()`, `ServiceInfo.create(...)`, `addServiceListener`
  — catalog: `jmdns = { module = "org.jmdns:jmdns", version = "3.5.9" }`
- `shared/src/iosMain/kotlin/io/aequicor/pdf/data/network/NetServiceDeviceDiscovery.kt`
  — `platform.Foundation.NSNetService` + `NSNetServiceBrowser` через K/N interop
- `shared/src/jsMain/kotlin/io/aequicor/pdf/data/network/ServerRegistryDiscovery.js.kt`
  — fallback: `GET /peers` с polling раз в 5 сек (браузер не имеет доступа к mDNS)

---

### Step 5 — Ktor HTTP-клиент в `shared`
**Commit:** `feat(shared/data): KtorNetworkClient — pairing + document transfer`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/network/NetworkClient.kt`
  — `class NetworkClient(engine: HttpClientEngine)`
  — `suspend fun requestPairing(host: DeviceInfo): PairingCode`
  — `suspend fun confirmPairing(host: DeviceInfo, code: PairingCode): SessionToken`
  — `suspend fun transferDocument(host: DeviceInfo, path: String, onProgress: (Float) -> Unit)` — chunked upload (1MB chunks)
  — TLS: `install(HttpsConfig)` с custom `TrustManager` валидирующим по fingerprint

Engine selection: через per-platform Gradle deps (без `expect/actual` — Ktor выбирает engine автоматически при наличии артефакта в classpath):
- `androidMain` / `jvmMain` → `io.ktor:ktor-client-cio`
- `iosMain` → `io.ktor:ktor-client-darwin`
- `jsMain` / `wasmJsMain` → `io.ktor:ktor-client-js`

`HttpClient { install(WebSockets); install(ContentNegotiation) { json() } }` — общий код в `commonMain`.

---

### Step 6 — NetworkViewModel + UI экранов
**Commit:** `feat(composeApp): NetworkViewModel + DeviceDiscovery + Pairing UI`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/presentation/NetworkViewModel.kt`
  — `StateFlow<List<DeviceInfo>>` (discovered devices)
  — `StateFlow<PairingState>` (Idle / WaitingCode / Paired / Error)
  — `fun startServer()`, `fun stopServer()`, `fun connectTo(device)`, `fun confirmPairing(code)`
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/network/DeviceDiscoveryScreen.kt`
  — список найденных устройств, кнопка «Host» (запускает сервер), «Connect» (подключиться)
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/network/PairingDialog.kt`
  — отображает 6-значный код (host mode) или поле ввода (client mode)
  — показывает TLS fingerprint для верификации

---

### Step 7 — Document transfer UI
**Commit:** `feat(composeApp): document transfer — progress UI + delta check`

Files:
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/network/TransferProgressDialog.kt`
  — `LinearProgressIndicator` + отменяемый transfer
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/network/DocumentSyncManager.kt`
  — перед transfer: `GET /documents/{id}/hash` сравнивает SHA-256 → пропускает если одинаковый (дельта-проверка)

---

### Step 8 — Unit + integration тесты
**Commit:** `test(server+shared): pairing flow + chunked transfer tests`

- `server/src/test/kotlin/PairingTest.kt` — full HTTP pairing roundtrip (MockEngine)
- `server/src/test/kotlin/DocumentTransferTest.kt` — chunked upload, status endpoint
- `shared/src/commonTest/kotlin/NetworkClientTest.kt` — MockEngine-based client tests
- `shared/src/commonTest/kotlin/FingerprintValidatorTest.kt`

---

### Step 9 — Docs
**Commit:** `docs: ADR-005 mDNS strategy + ADR-006 TLS pairing + README M5`

- `docs/adr/ADR-005-mdns-discovery.md`
- `docs/adr/ADR-006-tls-pairing.md`
- `README.md` — M5 секция

## Definition of Done (M5)
- [ ] Android ↔ Desktop: обнаруживают друг друга, паруются через PIN, передают PDF
- [ ] TLS: fingerprint отображается при первом подключении
- [ ] iOS: mDNS реклама работает, обнаруживается Android/Desktop
- [ ] Web: видит устройства через server-registry fallback
- [ ] Chunked transfer: файл >10MB передаётся корректно, прогресс отображается
- [ ] Тесты зелёные

## Open questions
1. Embedded сервер запускается только в Host-режиме вручную, или автоматически при старте приложения?
2. Максимальный размер PDF для transfer — ограничивать в M5 (например, 100MB)?
3. Нужен ли QR-код для pairing в M5, или PIN достаточен (QR отложить)?
