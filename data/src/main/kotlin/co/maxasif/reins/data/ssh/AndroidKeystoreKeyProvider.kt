package co.maxasif.reins.data.ssh

import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.KeyProvider

/**
 * sshj [KeyProvider] for a [co.maxasif.reins.domain.model.Identity.KeystoreIdentity] (ticket 004
 * asset §4, ticket 006 asset §4, ticket 020): delegates signing to the Android Keystore instead of
 * decrypting a key blob. [getPrivate] returns Android's opaque `AndroidKeyStorePrivateKey` handle -
 * `getEncoded()` on it returns `null` and the key material never leaves the Keystore/TEE/StrongBox.
 * sshj's own signing path (`AbstractSignature.initSign`/`sign`) never calls `getEncoded()`, so this
 * plugs into `SSHClient.authPublickey` with no custom `Signature` implementation required.
 */
class AndroidKeystoreKeyProvider(private val alias: String) : KeyProvider {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun getPrivate(): PrivateKey {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("No Keystore private key entry for alias '$alias'")
        return entry.privateKey
    }

    override fun getPublic(): PublicKey =
        keyStore.getCertificate(alias)?.publicKey
            ?: throw IllegalStateException("No Keystore certificate for alias '$alias'")

    override fun getType(): KeyType = KeyType.fromKey(public)
}
