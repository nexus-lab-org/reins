package co.maxasif.reins.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a [co.maxasif.reins.domain.model.Identity]. Flat with a [type] discriminator plus
 * nullable per-variant columns, rather than two tables - the domain/mapper layer is what enforces
 * the sealed-type shape; Room itself doesn't need it. `keystoreAlias` is unused until ticket 020
 * builds `KeystoreIdentity` creation, but the column exists now so that variant doesn't need a
 * migration later.
 *
 * [encryptedPrivateKey]/[encryptedPassphrase] are ciphertext only, produced by
 * [co.maxasif.reins.data.identity.IdentityKeyCipher] (Android Keystore AES-GCM) - never plaintext
 * key material at rest.
 */
@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val publicKeyFingerprint: String,
    val type: String,
    val encryptedPrivateKey: ByteArray?,
    val privateKeyIv: ByteArray?,
    val encryptedPassphrase: ByteArray?,
    val passphraseIv: ByteArray?,
    val keystoreAlias: String?,
) {
    companion object {
        const val TYPE_IMPORTED_KEY = "imported_key"
        const val TYPE_KEYSTORE = "keystore"
    }
}
