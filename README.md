# Termaff

Бесплатный минималистичный SSH-клиент для Android — лёгкий аналог Termius.

- **Серверы** — список с поиском и тегами, вход по паролю или ключу (Ed25519, RSA, ECDSA).
- **Нормальная клавиатура** — команды набираются в обычной строке ввода: работают свайпы,
  автозамена и голосовой ввод. Для `vim`/`htop`/`mc` — прямой режим.
- **Панель клавиш** — Ctrl/Alt (липкие), Esc, Tab, стрелки, Home/End, PgUp/PgDn, спецсимволы.
- **Безопасность** — пароли и ключи шифруются Android Keystore; ключ сервера проверяется
  по отпечатку при первом входе, подмена ключа блокирует подключение. Без рекламы, аналитики и облака.

## Установка

APK — на странице [Releases](https://github.com/Dimaff355/Termaff/releases).
Для большинства телефонов подходит `arm64-v8a`; если не уверены — `universal`.

## Сборка

```bash
./gradlew assembleDebug
```

Kotlin, Jetpack Compose. Терминал — [connectbot/termlib](https://github.com/connectbot/termlib),
SSH — [connectbot/sshlib](https://github.com/connectbot/sshlib). Лицензии зависимостей — Apache-2.0.
