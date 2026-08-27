package co.maxasif.reins.data.repository

import android.content.Context
import co.maxasif.reins.data.db.dao.IdentityDao
import co.maxasif.reins.data.db.entity.IdentityEntity
import co.maxasif.reins.data.db.mapper.toDomain
import co.maxasif.reins.data.identity.IdentityKeyCipher
import co.maxasif.reins.data.identity.KeystoreIdentityGenerator
import co.maxasif.reins.data.ssh.AndroidKeystoreKeyProvider
import co.maxasif.reins.data.ssh.SshKeyProviders
import co.maxasif.reins.domain.model.Identity
import co.maxasif.reins.domain.repository.IdentityRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.schmizz.sshj.userauth.keyprovider.KeyProvider

/** Decrypted [Identity.ImportedKeyIdentity] key material, for `:app`'s connect orchestration only -
 * deliberately not part of the [IdentityRepository] domain interface, so `:presentation` never sees it. */
data class ImportedKeySigningMaterial(val privateKeyPem: String, val passphrase: String?)

class IdentityRepositoryImpl(
    private val dao: IdentityDao,
    private val cipher: IdentityKeyCipher,
    private val context: Context,
) : IdentityRepository {
    override fun observeIdentities(): Flow<List<Identity>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getIdentity(id: String): Identity? = dao.getById(id)?.toDomain()

    override suspend fun importKeyIdentity(displayName: String, privateKeyPem: String, passphrase: String?): Identity {
        val fingerprint = SshKeyProviders.fingerprintOf(privateKeyPem, passphrase)

        val encryptedKey = cipher.encrypt(privateKeyPem.toByteArray(Charsets.UTF_8))
        val encryptedPassphrase = passphrase?.let { cipher.encrypt(it.toByteArray(Charsets.UTF_8)) }

        val entity = IdentityEntity(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            publicKeyFingerprint = fingerprint,
            type = IdentityEntity.TYPE_IMPORTED_KEY,
            encryptedPrivateKey = encryptedKey.ciphertext,
            privateKeyIv = encryptedKey.iv,
            encryptedPassphrase = encryptedPassphrase?.ciphertext,
            passphraseIv = encryptedPassphrase?.iv,
            keystoreAlias = null,
        )
        dao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun createKeystoreIdentity(displayName: String): Identity {
        val alias = "reins_keystore_identity_${UUID.randomUUID()}"
        val fingerprint = KeystoreIdentityGenerator.generate(context, alias)

        val entity = IdentityEntity(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            publicKeyFingerprint = fingerprint,
            type = IdentityEntity.TYPE_KEYSTORE,
            encryptedPrivateKey = null,
            privateKeyIv = null,
            encryptedPassphrase = null,
            passphraseIv = null,
            keystoreAlias = alias,
        )
        dao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun exportAuthorizedKeysLine(identity: Identity.KeystoreIdentity): String =
        KeystoreIdentityGenerator.exportAuthorizedKeysLine(identity.keystoreAlias, comment = identity.displayName)

    override suspend fun deleteIdentity(id: String) {
        val entity = dao.getById(id)
        dao.deleteById(id)
        if (entity?.type == IdentityEntity.TYPE_KEYSTORE) {
            entity.keystoreAlias?.let { KeystoreIdentityGenerator.deleteAlias(it) }
        }
    }

    /** The sshj [KeyProvider] for a [Identity.KeystoreIdentity], for `:app`'s connect orchestration
     * only - delegates every signing operation to the Keystore, never reads key material out of it. */
    fun keyProviderFor(identity: Identity.KeystoreIdentity): KeyProvider =
        AndroidKeystoreKeyProvider(identity.keystoreAlias)

    /** Decrypts the key material `:app` needs to authenticate over SSH (see [ImportedKeySigningMaterial]). */
    suspend fun loadSigningMaterial(identityId: String): ImportedKeySigningMaterial {
        val entity = requireNotNull(dao.getById(identityId)) { "Identity $identityId not found" }
        check(entity.type == IdentityEntity.TYPE_IMPORTED_KEY) {
            "loadSigningMaterial only supports ImportedKeyIdentity - KeystoreIdentity signing is ticket 020"
        }
        val pem = cipher.decrypt(
            IdentityKeyCipher.EncryptedBlob(
                ciphertext = requireNotNull(entity.encryptedPrivateKey),
                iv = requireNotNull(entity.privateKeyIv),
            ),
        ).toString(Charsets.UTF_8)
        val passphrase = if (entity.encryptedPassphrase != null) {
            cipher.decrypt(
                IdentityKeyCipher.EncryptedBlob(
                    ciphertext = entity.encryptedPassphrase,
                    iv = requireNotNull(entity.passphraseIv),
                ),
            ).toString(Charsets.UTF_8)
        } else {
            null
        }
        return ImportedKeySigningMaterial(privateKeyPem = pem, passphrase = passphrase)
    }
}
