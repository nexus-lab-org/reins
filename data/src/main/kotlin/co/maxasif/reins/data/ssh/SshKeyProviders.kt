package co.maxasif.reins.data.ssh

import java.io.StringReader
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.userauth.keyprovider.BaseFileKeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyFormat
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import net.schmizz.sshj.userauth.password.PasswordUtils

/**
 * Parses pasted/imported private-key PEM text (`ImportedKeyIdentity`, ticket 018) into an sshj
 * [KeyProvider], for both fingerprint derivation at import time and SSH auth at connect time.
 * Format detection mirrors what [net.schmizz.sshj.SSHClient]'s own file-path `loadKeys` does
 * internally, just against in-memory text instead of a file.
 */
object SshKeyProviders {
    fun load(privateKeyPem: String, passphrase: String?): KeyProvider {
        val format = KeyProviderUtil.detectKeyFileFormat(StringReader(privateKeyPem), true)
        val provider: BaseFileKeyProvider = when (format) {
            KeyFormat.OpenSSHv1 -> OpenSSHKeyFile()
            KeyFormat.PKCS8, KeyFormat.OpenSSH -> PKCS8KeyFile()
            KeyFormat.PuTTY -> PuTTYKeyFile()
            else -> throw InvalidPrivateKeyException("Unrecognized private key format")
        }
        if (passphrase != null) {
            provider.init(privateKeyPem, null, PasswordUtils.createOneOff(passphrase.toCharArray()))
        } else {
            provider.init(privateKeyPem, null)
        }
        return provider
    }

    /** Validates the key/passphrase parse and returns its public-key fingerprint. */
    fun fingerprintOf(privateKeyPem: String, passphrase: String?): String {
        val provider = load(privateKeyPem, passphrase)
        return try {
            SecurityUtils.getFingerprint(provider.public)
        } catch (t: Exception) {
            throw InvalidPrivateKeyException(t.message ?: "Failed to read private key (wrong passphrase?)")
        }
    }
}

/** The pasted/imported text isn't a recognized private-key format, or the passphrase is wrong. */
class InvalidPrivateKeyException(message: String) : Exception(message)
