# Termaff — справка для разработки

Минималистичный бесплатный SSH-клиент для Android. План и этапы — `PLAN.md`. Дизайн — `интерфейс.png`.
Репозиторий: https://github.com/Dimaff355/Termaff (токен и логин в `.env`, НЕ коммитить `.env`).

## Статус
- Этапы 0–2 готовы, релиз **v0.1.0** (2026-09-26): серверы (список/редактор/хранение/шифрование),
  TOFU с сохранением, SSH-терминал со строкой ввода и панелью клавиш. Дальше — этап 3 (клавиатура).

## Архитектура (кратко)
- Kotlin + Jetpack Compose + Material 3, 1 модуль `app`, 1 Activity.
- Терминал: `org.connectbot:termlib` (libvterm/JNI). SSH: `org.connectbot:sshlib` (Trilead).
- Данные: `files/state.json` через встроенный `org.json` (без kotlinx.serialization — ноль зависимостей),
  запись атомарная (tmp + rename). Пароли/ключи в JSON зашифрованы `Vault` (Keystore AES-GCM).
  Проверить на устройстве: `adb shell run-as app.termaff cat files/state.json`.
- Секреты в редакторе держатся в `remember` (не `rememberSaveable`), чтобы не попадать в Bundle.
- Ввод: строка ввода (обычный TextField → свайпы работают) + прямой режим для TUI-программ.
- Навигация без navigation-compose: в `MainActivity` три флага (редактор / терминал / список).
  «Назад» из терминала НЕ рвёт сессию (зелёная точка на карточке), крестик в шапке — отключиться.
- `android:configChanges` в манифесте — поворот не пересоздаёт Activity, терминал не теряется.

```
app/src/main/java/app/termaff/
  MainActivity.kt      — выбор экрана
  ssh/SshSession.kt    — Connection → TOFU → auth → PTY-shell; read-loop → emulator; запись через Channel (не в UI-потоке)
  data/Store.kt        — Server, список серверов, knownHosts, JSON
  data/Vault.kt        — encrypt/decrypt через Android Keystore
  ssh/Sessions.kt      — текущая сессия (переживает экраны)
  ui/Theme.kt          — палитра из макета
  ui/ServersScreen.kt  — список серверов, поиск, меню, FAB
  ui/ServerEditScreen.kt — редактор сервера (пароль/ключ, ключ из файла, теги, команда после входа)
  ui/TerminalScreen.kt — Terminal + KeysBar (липкие Ctrl/Alt) + строка ввода
```

## Библиотеки — важные детали
- SSH: **`org.connectbot:sshlib:2.2.48`** (зрелый форк Trilead, API `com.trilead.ssh2.*`, его использует
  приложение ConnectBot). НЕ путать с `org.connectbot.sshlib:sshlib` 0.4.x — это новый Kotlin-переписанный
  (cbssh), пока молодой.
  - `Connection(host, port).connect(verifier, connectTimeout, kexTimeout)`; `authenticateWithPassword` /
    `authenticateWithPublicKey(user, pemChars, passphrase)`; `openSession()` → `requestPTY` → `startShell`.
  - Пароль: сначала `password`, затем `keyboard-interactive` (многие PAM-серверы принимают только его).
  - Парсер ключей построчный: ключ, склеенный в одну строку, ломается → `pemLines()` в SshSession.kt.
- Терминал: **`org.connectbot:termlib:0.3.7`** (Compose, libvterm JNI, minSdk 24, ~3 МБ .so на ABI →
  debug APK ~36 МБ со всеми ABI; для релиза сделать ABI splits).
  - `TerminalEmulatorFactory.create(onKeyboardInput, onResize, ...)`, `writeInput(bytes)` — вывод сервера.
  - `dispatchKey(mods, VTermKey.X)` / `dispatchCharacter(mods, codepoint)` — клавиши (сам учитывает
    application cursor mode). Модификаторы libvterm: SHIFT=1, ALT=2, CTRL=4.
  - Встроенный IME termlib отдаёт `TYPE_NULL` → свайпы не работают, поэтому своя строка ввода.
  - `ModifierManager` — через него наши липкие Ctrl/Alt работают и в прямом режиме.
  - `onResize` приходит ДО открытия PTY → размер запоминаем и отдаём в `requestPTY` (иначе 80x24).
  - Исходники для справки: `https://repo1.maven.org/maven2/org/connectbot/termlib/<v>/termlib-<v>-sources.jar`.

## Окружение (машина разработчика, Linux)
- Android SDK: `~/Android/Sdk` (platforms: android-37; build-tools 36.0.0, 37.0.0; есть emulator, platform-tools).
- `ANDROID_HOME` не задан — указывать `sdk.dir` в `local.properties`.
- **JDK в PATH нет**, но есть JBR от Android Studio (OpenJDK 25). Экспортировать в каждой команде сборки:
  ```bash
  export JAVA_HOME=/home/dimafwork/opt/android-studio/jbr
  export PATH="$JAVA_HOME/bin:$PATH"   # без java в PATH apksigner молча падает
  ```
- Проверенный стек соседнего проекта (`../Notoday`, тоже Compose): Kotlin 2.4 / AGP 9.1 / Gradle 9.3 (wrapper),
  compileSdk 37, minSdk 26 — брать те же версии, gradle wrapper можно скопировать оттуда.
- Эмулятор: AVD `notoday` (общий). Запуск без окна:
  `~/Android/Sdk/emulator/emulator -avd notoday -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &`,
  затем `adb wait-for-device`, ждать `getprop sys.boot_completed` = 1. UI искать через `uiautomator dump`.
- `gh` нет — релизы через GitHub REST API (`curl`), см. `../Notoday/HELP.md`. `git push` по https иногда
  падает с `GnuTLS handshake failed` — просто повторить.
- Эмулятор НЕ достаёт хост по `10.0.2.2` (таймаут; вероятно фаервол/VPN) → использовать
  `adb reverse tcp:2222 tcp:2222` и подключаться к `127.0.0.1:2222`.
- Тестовый sshd без root (систему не трогает): `sshd -f <cfg>` с `Port 2222`, своим HostKey,
  `AuthorizedKeysFile`, `UsePAM no`, `StrictModes no`; вход только по ключу (пароль без root не работает).
  Ключ в форму вводится через `adb shell input text` по токенам + `keyevent 62` (пробел).
- Грабли: `pgrep -f`/`pkill -f` с паттерном из той же команды находят/убивают саму команду —
  эмулятор запускать отдельной фоновой командой.
- В `ui.sh`-подобных хелперах текст с `(`/`)` ломает `grep -E` — тапать по координатам из дампа.
- Debug и release подписаны разными ключами: перед установкой другого типа — `adb uninstall app.termaff`.

## Релиз
- Ключ подписи: `signing/termaff-release.jks` + `signing/keystore.properties` (в .gitignore, пароль там же).
  **Потеря ключа = нельзя обновить установленное приложение.** Держать резервную копию вне репозитория.
- `app/build.gradle.kts`: поднять `versionCode` (+1) и `versionName`.
- `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-{arm64-v8a,armeabi-v7a,x86_64,universal}-release.apk`
  (ABI splits: arm64 ≈ 5 МБ, universal ≈ 14 МБ). Проверка: `apksigner verify --print-certs`.
- R8: нужны `-dontwarn javax.annotation.**` (tink) и keep для `com.trilead.ssh2.**`, `org.connectbot.terminal.**`.
- Перед публикацией прогнать release-APK на эмуляторе (`x86_64`): подключение → shell.
- Публикация: тег `vX.Y.Z`, релиз и ассеты через REST API (как в `../Notoday/HELP.md`), ассеты сырыми байтами.
  Имена: `Termaff-X.Y.Z-arm64-v8a.apk` и т.д.
- `.env` не в формате KEY=VALUE: токен и логин просто строками. Токен доставать
  `grep -oE 'github_pat_[A-Za-z0-9_]+' .env`, **никогда не печатать файл целиком**.
