# sshlib грузит алгоритмы через рефлексию/имена классов
-keep class com.trilead.ssh2.** { *; }
-dontwarn com.trilead.ssh2.**
# termlib: JNI вызывает Kotlin-методы по имени
-keep class org.connectbot.terminal.** { *; }
