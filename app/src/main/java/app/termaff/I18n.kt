package app.termaff

import app.termaff.data.Store
import java.util.Locale

/**
 * Перевод интерфейса. Ключ — русский текст из кода (он же русский вариант), значение — английский и китайский.
 * Без strings.xml: язык меняется сразу, без пересоздания Activity (tr читает Store.lang — Compose перерисует),
 * и работает вне UI (уведомление сервиса, ошибки SSH), где нет локализованного Context.
 * Нет перевода — показывается русский текст.
 */
fun tr(ru: String, vararg args: Any?): String {
    val s = when (lang) {
        "en" -> T[ru]?.get(0)
        "zh" -> T[ru]?.get(1)
        else -> null
    } ?: ru
    return if (args.isEmpty()) s else s.format(*args)
}

/** Язык интерфейса: выбранный в настройках, иначе системный (русский/китайский, остальные — английский). */
val lang: String get() = Store.lang.ifEmpty { Locale.getDefault().language.takeIf { it == "ru" || it == "zh" } ?: "en" }

val locale: Locale get() = Locale.forLanguageTag(lang)

/** Варианты в настройках: код → название на самом этом языке ("" — как в системе). */
val Languages = listOf("" to "Системный", "ru" to "Русский", "en" to "English", "zh" to "中文")

private fun l(en: String, zh: String) = arrayOf(en, zh)

private val T = mapOf(
    // Навигация и общее
    "Серверы" to l("Servers", "服务器"),
    "Команды" to l("Commands", "命令"),
    "Настройки" to l("Settings", "设置"),
    "Назад" to l("Back", "返回"),
    "Меню" to l("Menu", "菜单"),
    "Изменить" to l("Edit", "编辑"),
    "Сохранить" to l("Save", "保存"),
    "Удалить" to l("Delete", "删除"),
    "Отмена" to l("Cancel", "取消"),
    "Отменить" to l("Cancel", "取消"),
    "Копировать" to l("Copy", "复制"),
    "Скопировано" to l("Copied", "已复制"),
    "Отправить" to l("Send", "发送"),
    "Удалить «%s»?" to l("Delete “%s”?", "删除“%s”？"),
    "Системный" to l("System", "跟随系统"),
    "Язык" to l("Language", "语言"),

    // Серверы
    "Добавить сервер" to l("Add server", "添加服务器"),
    "Поиск серверов…" to l("Search servers…", "搜索服务器…"),
    "Пока пусто. Нажмите «+», чтобы добавить сервер." to l("Nothing here yet. Tap “+” to add a server.", "暂无服务器。点击“+”添加。"),
    "Обзор" to l("Overview", "概览"),
    "Файлы" to l("Files", "文件"),
    "%s (копия)" to l("%s (copy)", "%s（副本）"),
    "Новый сервер" to l("New server", "新服务器"),
    "Название (необязательно)" to l("Name (optional)", "名称（可选）"),
    "Хост или IP" to l("Host or IP", "主机或 IP"),
    "Пользователь" to l("User", "用户名"),
    "Порт" to l("Port", "端口"),
    "Пароль" to l("Password", "密码"),
    "Ключ" to l("Key", "密钥"),
    "Ключей пока нет. Создайте или импортируйте ключ в Настройках." to
        l("No keys yet. Create or import one in Settings.", "还没有密钥。请在设置中创建或导入。"),
    "Теги через запятую" to l("Tags, comma-separated", "标签，用逗号分隔"),
    "Команда после входа (необязательно)" to l("Command after login (optional)", "登录后执行的命令（可选）"),

    // Терминал
    "Строка ввода" to l("Input line", "输入行"),
    "Прямой ввод" to l("Direct input", "直接输入"),
    "Отключиться" to l("Disconnect", "断开连接"),
    "Пароль (не сохраняется)" to l("Password (not saved)", "密码（不保存）"),
    "Введите команду…" to l("Enter a command…", "输入命令…"),
    "Соединение закрыто" to l("Connection closed", "连接已关闭"),
    "Переподключиться" to l("Reconnect", "重新连接"),
    "%s. Переподключение…" to l("%s. Reconnecting…", "%s。正在重新连接…"),
    "Отпечаток ключа %s:\n\n%s\n\nСверьте его с сервером. Доверять?" to l(
        "Host key fingerprint for %s:\n\n%s\n\nCompare it with the server. Trust it?",
        "%s 的主机密钥指纹：\n\n%s\n\n请与服务器核对。是否信任？",
    ),
    "Доверять" to l("Trust", "信任"),

    // SSH
    "SSH-сессии" to l("SSH sessions", "SSH 会话"),
    "SSH-сессия открыта" to l("SSH session open", "SSH 会话已打开"),
    "SSH-сессий открыто: %s" to l("SSH sessions open: %s", "已打开 SSH 会话：%s"),
    "Отключить все" to l("Disconnect all", "全部断开"),
    "Нет соединения" to l("Not connected", "未连接"),
    "Связь потеряна" to l("Connection lost", "连接中断"),
    "Неверный логин, пароль или ключ" to l("Wrong username, password or key", "用户名、密码或密钥错误"),
    "Ключ сервера изменился! Возможна атака MITM.\nБыл: %s\nСейчас: %s" to l(
        "The server key has changed! Possible MITM attack.\nWas: %s\nNow: %s",
        "服务器密钥已更改！可能存在中间人攻击。\n原来：%s\n现在：%s",
    ),
    "Сервер не поддерживает SFTP" to l("The server does not support SFTP", "服务器不支持 SFTP"),

    // Обзор
    "ядер: %s" to l("cores: %s", "核心：%s"),
    "Диск /" to l("Disk /", "磁盘 /"),
    "Аптайм" to l("Uptime", "运行时间"),
    "нагрузка %s" to l("load %s", "负载 %s"),
    "Быстрые действия" to l("Quick actions", "快捷操作"),
    "Терминал" to l("Terminal", "终端"),
    "Система" to l("System", "系统"),
    "%s д %s ч" to l("%sd %sh", "%s天 %s小时"),
    "%s ч %s мин" to l("%sh %sm", "%s小时 %s分钟"),
    "%s мин" to l("%sm", "%s分钟"),

    // Файлы
    "Загрузить файл" to l("Upload file", "上传文件"),
    "Новая папка" to l("New folder", "新建文件夹"),
    "Наверх" to l("Up", "上一级"),
    "Скачать" to l("Download", "下载"),
    "Переименовать" to l("Rename", "重命名"),
    "Пусто" to l("Empty", "空"),
    "Удаляется только пустая папка." to l("Only an empty folder can be deleted.", "只能删除空文件夹。"),
    "Заменить «%s»?" to l("Replace “%s”?", "替换“%s”？"),
    "Файл с таким именем уже есть в этой папке." to l("A file with this name already exists in this folder.", "此文件夹中已有同名文件。"),
    "Заменить" to l("Replace", "替换"),
    "%s Б" to l("%s B", "%s B"),
    "КБ" to l("KB", "KB"),
    "МБ" to l("MB", "MB"),
    "ГБ" to l("GB", "GB"),
    "ТБ" to l("TB", "TB"),

    // Команды
    "Добавить команду" to l("Add command", "添加命令"),
    "Сохраняйте частые команды и сценарии из нескольких шагов — они появятся в терминале над клавишами." to l(
        "Save frequent commands and multi-step scripts — they will appear in the terminal above the keys.",
        "保存常用命令和多步骤脚本——它们会显示在终端的按键上方。",
    ),
    "Сценарии" to l("Scripts", "脚本"),
    "Выполнить" to l("Run", "运行"),
    "Все серверы" to l("All servers", "所有服务器"),
    "Новая команда" to l("New command", "新命令"),
    "Команда" to l("Command", "命令"),
    "Несколько строк — сценарий: строки выполняются по очереди" to
        l("Several lines make a script: they run one after another", "多行即为脚本：按顺序逐行执行"),
    "Удалить команду" to l("Delete command", "删除命令"),

    // Ключи
    "Не удалось прочитать ключ: неверный формат или пароль ключа" to
        l("Could not read the key: wrong format or passphrase", "无法读取密钥：格式或密钥密码错误"),
    "Новый ключ" to l("New key", "新密钥"),
    "Ключ %s" to l("Key %s", "密钥 %s"),
    "Название" to l("Name", "名称"),
    "Приватный ключ скрыт" to l("Private key is hidden", "私钥已隐藏"),
    "Показать ключ" to l("Show key", "显示密钥"),
    "Показать" to l("Show", "显示"),
    "Приватный ключ (OpenSSH/PEM)" to l("Private key (OpenSSH/PEM)", "私钥（OpenSSH/PEM）"),
    "Загрузить из файла" to l("Load from file", "从文件加载"),
    "Пароль ключа (если есть)" to l("Key passphrase (if any)", "密钥密码（如有）"),
    "Удалить ключ" to l("Delete key", "删除密钥"),
    "Серверы с этим ключом перейдут на вход по паролю: %s" to
        l("Servers using this key will switch to password login: %s", "使用此密钥的服务器将改为密码登录：%s"),
    "Публичный ключ — добавьте его на сервер в ~/.ssh/authorized_keys" to
        l("Public key — add it to ~/.ssh/authorized_keys on the server", "公钥——请添加到服务器的 ~/.ssh/authorized_keys"),

    // Настройки
    "Ключи SSH" to l("SSH keys", "SSH 密钥"),
    "Создать Ed25519" to l("Create Ed25519", "创建 Ed25519"),
    "Импортировать" to l("Import", "导入"),
    "Безопасность" to l("Security", "安全"),
    " · серверов: %s" to l(" · servers: %s", " · 服务器：%s"),
    "Размер шрифта" to l("Font size", "字体大小"),
    "%s sp · меняется и щипком" to l("%s sp · pinch to change", "%s sp · 也可双指缩放"),
    "Авто: 80 колонок по ширине" to l("Auto: 80 columns wide", "自动：宽度 80 列"),
    "Меньше" to l("Smaller", "缩小"),
    "Больше" to l("Larger", "放大"),
    "Вернуть авто" to l("Back to auto", "恢复自动"),
    "Цветовая схема" to l("Color scheme", "配色方案"),
    "Светлая" to l("Light", "浅色"),
    "Розовая" to l("Pink", "粉色"),
    "Панель клавиш" to l("Key bar", "按键栏"),
    "Нажмите клавишу, чтобы переместить или скрыть" to l("Tap a key to move or hide it", "点击按键以移动或隐藏"),
    "Левее" to l("Move left", "左移"),
    "Правее" to l("Move right", "右移"),
    "Скрыть" to l("Hide", "隐藏"),
    "Скрытые — нажмите, чтобы вернуть" to l("Hidden — tap to add back", "已隐藏 — 点击恢复"),
    "По умолчанию" to l("Default", "默认"),
    "Вход по отпечатку" to l("Fingerprint unlock", "指纹解锁"),
    "Отпечаток или PIN телефона при открытии приложения" to
        l("Phone fingerprint or PIN when opening the app", "打开应用时验证指纹或手机 PIN"),
    "Нужен Android 11 или новее" to l("Requires Android 11 or newer", "需要 Android 11 或更高版本"),
    "Включить вход по отпечатку" to l("Enable fingerprint unlock", "启用指纹解锁"),
    "Выключить вход по отпечатку" to l("Disable fingerprint unlock", "关闭指纹解锁"),
    "Сначала включите блокировку экрана в настройках телефона" to
        l("First turn on screen lock in the phone settings", "请先在手机设置中开启锁屏"),
    "Разблокировка" to l("Unlock", "解锁"),
    "Разблокировать" to l("Unlock", "解锁"),
)
