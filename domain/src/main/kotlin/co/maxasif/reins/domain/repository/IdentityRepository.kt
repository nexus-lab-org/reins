package co.maxasif.reins.domain.repository

import co.maxasif.reins.domain.model.Identity
import kotlinx.coroutines.flow.Flow

/** Room-backed persistence for [Identity], implemented in `:data`. */
interface IdentityRepository {
    fun observeIdentities(): Flow<List<Identity>>

    suspend fun getIdentity(id: String): Identity?

    /**
     * Imports a private key (PEM text - OpenSSH, PKCS8, or PuTTY format) as a new
     * [Identity.ImportedKeyIdentity], reusable across multiple Hosts. The key is validated and its
     * public-key fingerprint derived immediately; throws if [privateKeyPem]/[passphrase] don't
     * parse into a valid key pair.
     */
    suspend fun importKeyIdentity(displayName: String, privateKeyPem: String, passphrase: String?): Identity

    /**
     * Generates a new on-device [Identity.KeystoreIdentity] (EC P-256, Android Keystore-backed,
     * StrongBox where available - ticket 006/020). No key material is stored anywhere outside the
     * Keystore itself; only the alias and derived public-key fingerprint are persisted.
     */
    suspend fun createKeystoreIdentity(displayName: String): Identity

    /**
     * The `authorized_keys`-format line for a [Identity.KeystoreIdentity]'s public key, for the
     * user to copy onto a remote host. The private key never leaves the Keystore - only the
     * (always-exportable) public key is encoded here.
     */
    suspend fun exportAuthorizedKeysLine(identity: Identity.KeystoreIdentity): String

    suspend fun deleteIdentity(id: String)
}
