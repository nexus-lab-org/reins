package co.maxasif.reins.data.identity

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils

/**
 * On-device EC P-256 key generation for [co.maxasif.reins.domain.model.Identity.KeystoreIdentity]
 * (ticket 006/020) - `AndroidKeyStore`-backed, StrongBox where available, with no key material
 * (public or private) ever written outside the Keystore itself. Only the alias is persisted; the
 * public key is re-derived from the Keystore on demand via [exportAuthorizedKeysLine].
 */
object KeystoreIdentityGenerator {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val CURVE_P256 = "secp256r1"

    /**
     * Generates a new EC P-256 key pair under [alias], preferring StrongBox and falling back to
     * TEE-only per ticket 006's documented `StrongBoxUnavailableException` recovery (no device
     * allowlist). Returns the (always-exportable) public key's `authorized_keys` fingerprint.
     */
    fun generate(context: Context, alias: String): String {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
        val strongBoxAvailable = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        fun buildSpec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE_P256))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(strongBox)
            .build()

        val keyPair = try {
            kpg.initialize(buildSpec(strongBox = strongBoxAvailable))
            kpg.generateKeyPair()
        } catch (e: StrongBoxUnavailableException) {
            kpg.initialize(buildSpec(strongBox = false))
            kpg.generateKeyPair()
        }
        return SecurityUtils.getFingerprint(keyPair.public)
    }

    /** The `<key-type> <base64> <comment>` line for `authorized_keys`, per ticket 006 §3. */
    fun exportAuthorizedKeysLine(alias: String, comment: String): String {
        val publicKey = publicKeyOf(alias)
        val keyType = KeyType.fromKey(publicKey)
        val buf = Buffer.PlainBuffer()
        buf.putPublicKey(publicKey)
        val b64 = android.util.Base64.encodeToString(buf.compactData, android.util.Base64.NO_WRAP)
        return "$keyType $b64 $comment"
    }

    private fun publicKeyOf(alias: String) =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.getCertificate(alias).publicKey

    fun deleteAlias(alias: String) {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.deleteEntry(alias)
    }
}
