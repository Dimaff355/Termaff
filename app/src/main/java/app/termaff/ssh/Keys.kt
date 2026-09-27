package app.termaff.ssh

import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.keys.Ed25519KeyPairGenerator
import com.trilead.ssh2.crypto.keys.Ed25519PrivateKey
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import com.trilead.ssh2.signature.DSASHA1Verify
import com.trilead.ssh2.signature.ECDSASHA2Verify
import com.trilead.ssh2.signature.Ed25519Verify
import com.trilead.ssh2.signature.RSASHA1Verify
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/** SSH-ключи: генерация Ed25519 в формате OpenSSH и публичная строка для authorized_keys. */
object Keys {
    /** Новый Ed25519-ключ: приватный (openssh-key-v1 без пароля — на устройстве его шифрует Vault) и публичная строка. */
    fun generate(comment: String): Pair<String, String> {
        val pair = Ed25519KeyPairGenerator().generateKeyPair()
        val pub = Ed25519Verify.get().encodePublicKey(pair.public)
        val raw = (pair.public as Ed25519PublicKey).abyte
        val check = SecureRandom().nextInt()
        val secret = ssh {
            writeInt(check); writeInt(check)
            str("ssh-ed25519".toByteArray()); str(raw); str((pair.private as Ed25519PrivateKey).seed + raw); str(comment.toByteArray())
            var pad = 1
            while (size() % 8 != 0) write(pad++)
        }
        val blob = ssh {
            write("openssh-key-v1\u0000".toByteArray())
            str("none".toByteArray()); str("none".toByteArray()); str(ByteArray(0))
            writeInt(1); str(pub); str(secret)
        }
        val pem = Base64.getEncoder().encodeToString(blob).chunked(70)
            .joinToString("\n", "-----BEGIN OPENSSH PRIVATE KEY-----\n", "\n-----END OPENSSH PRIVATE KEY-----\n")
        return pem to "ssh-ed25519 ${Base64.getEncoder().encodeToString(pub)} $comment"
    }

    /** Публичная строка из приватного ключа (OpenSSH/PEM). Бросает IOException, если формат или пароль неверны. */
    fun publicKey(pem: String, passphrase: String, comment: String): String {
        val pub = PEMDecoder.decode(pemLines(pem).toCharArray(), passphrase.ifEmpty { null }).public
        val sig = when (pub) {
            is RSAPublicKey -> RSASHA1Verify.get()
            is ECPublicKey -> ECDSASHA2Verify.getVerifierForKey(pub)
            is DSAPublicKey -> DSASHA1Verify.get()
            else -> Ed25519Verify.get()
        }
        return "${sig.keyFormat} ${Base64.getEncoder().encodeToString(sig.encodePublicKey(pub))} $comment"
    }

    private fun ssh(write: DataOutputStream.() -> Unit) =
        ByteArrayOutputStream().also { DataOutputStream(it).write() }.toByteArray()

    private fun DataOutputStream.str(b: ByteArray) {
        writeInt(b.size)
        write(b)
    }
}

/** Ключ, вставленный одной строкой (мессенджеры съедают переносы), возвращаем к PEM-виду по строкам. */
internal fun pemLines(key: String): String {
    val k = key.trim()
    if ('\n' in k) return k
    val m = Regex("(-----BEGIN [A-Z ]+-----)(.+)(-----END [A-Z ]+-----)").find(k) ?: return k
    val (begin, body, end) = m.destructured
    return (listOf(begin) + body.trim().split(Regex("\\s+")) + end).joinToString("\n")
}
