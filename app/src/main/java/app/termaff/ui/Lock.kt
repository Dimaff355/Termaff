package app.termaff.ui

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.termaff.R
import app.termaff.data.Store

/**
 * Вход по отпечатку (или PIN/графическому ключу телефона) через системный BiometricPrompt — без androidx.biometric.
 * Нужен Android 11+ (API 30: отпечаток и PIN в одном диалоге). Блокирует UI, SSH-сессии в фоне продолжают жить.
 */
object AppLock {
    /** Сколько можно пробыть в фоне без повторной разблокировки (файловый пикер, переключение на минутку). */
    private const val GRACE_MS = 60_000L
    private const val AUTH = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    var locked by mutableStateOf(false)
        private set
    private var hiddenAt = 0L

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun onStart() {
        if (Store.lock && supported && SystemClock.elapsedRealtime() - hiddenAt > GRACE_MS) locked = true
    }

    fun onStop() {
        if (!locked) hiddenAt = SystemClock.elapsedRealtime()
    }

    /** На телефоне настроена блокировка экрана (иначе спрашивать нечем). */
    fun available(context: Context) = supported &&
        context.getSystemService(BiometricManager::class.java).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(context: Context, subtitle: String, onResult: (Boolean) -> Unit) {
        if (!supported) return onResult(false)
        BiometricPrompt.Builder(context)
            .setTitle("Termaff")
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTH)
            .build()
            .authenticate(CancellationSignal(), context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(code: Int, msg: CharSequence) = onResult(false)
            })
    }

    fun unlock(context: Context) {
        // Блокировку экрана сняли в настройках телефона — проверить нечем, не запираем пользователя навсегда
        if (!available(context)) { locked = false; return }
        prompt(context, "Разблокировка") { if (it) locked = false }
    }
}

@Composable
fun LockScreen() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppLock.unlock(context) }
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(24.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.ic_launcher_fg), null, Modifier.size(144.dp).clip(CircleShape))
        Text("Termaff", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { AppLock.unlock(context) }) { Text("Разблокировать") }
    }
}
