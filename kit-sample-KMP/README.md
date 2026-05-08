# kit-sample-KMP

Минимальный шаблон Kotlin Multiplatform-проекта, используемый как
**пример target-проекта** для `kit-setup`. Сейчас здесь только скелет
KMP — никакой конфигурации ИИ-агента (нет `.aikit/`, `CLAUDE.md`,
`.claude/`, `opencode.json` и т.п.). Это сделано намеренно: подразумевается,
что `kit-setup` сам сгенерирует все эти файлы поверх данного проекта.

## Структура

```
kit-sample-KMP/
├── build.gradle.kts          KMP-плагин 2.1.20, таргеты jvm/js/iosX64/iosArm64/iosSimulatorArm64
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/           Gradle 8.10 (как в kit-setup)
├── gradlew, gradlew.bat
└── src/
    ├── commonMain/kotlin/com/aikit/sample/
    │   ├── Platform.kt       expect-интерфейс Platform + expect fun platform()
    │   └── Greeting.kt       пример общего кода
    ├── commonTest/kotlin/com/aikit/sample/
    │   └── GreetingTest.kt   общий тест (исполняется на каждом таргете)
    ├── jvmMain/kotlin/com/aikit/sample/Platform.jvm.kt
    ├── iosMain/kotlin/com/aikit/sample/Platform.ios.kt   (общий для iosX64/Arm64/SimulatorArm64)
    └── jsMain/kotlin/com/aikit/sample/Platform.js.kt
```

## Сборка и запуск тестов

Требуется JDK 11+ (рекомендуется 21, как в `kit-setup/`):

```sh
cd kit-sample-KMP
./gradlew build                # собрать все включённые таргеты
./gradlew jvmTest              # быстрый прогон тестов на JVM
./gradlew allTests             # тесты на всех доступных таргетах
```

iOS-таргеты включаются Gradle-ом только на macOS — это поведение
`kotlin.native.ignoreDisabledTargets=true` (см. `gradle.properties`),
такое же, как в `kit-setup/`.

## Как используется в AI-Kit

Этот каталог — пример проекта, на который агент-оркестратор натравливает
`kit-setup`:

1. Агент изучает `kit-sample-KMP/` и пишет `.aikit/manifest.yaml`.
2. `kit-setup verify .aikit/manifest.yaml` валидирует манифест.
3. `kit-setup generate .aikit/manifest.yaml` раскладывает рядом
   `CLAUDE.md`, `.claude/`, `opencode.json`, sub-агентов, slash-команды
   и т.д. — всё то, что в данный момент в репозитории отсутствует.

Ничего из перечисленного коммитить в этот каталог **не нужно** — пусть
будущий запуск `kit-setup` сам всё создаёт.
