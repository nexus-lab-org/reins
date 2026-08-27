package co.maxasif.reins.data.db.mapper

import co.maxasif.reins.data.db.entity.IdentityEntity
import co.maxasif.reins.domain.model.Identity

/** Never maps key material - [IdentityEntity.encryptedPrivateKey]/[IdentityEntity.encryptedPassphrase]
 * stay in `:data`, decrypted only by `IdentityRepositoryImpl.loadSigningMaterial`. */
fun IdentityEntity.toDomain(): Identity = when (type) {
    IdentityEntity.TYPE_IMPORTED_KEY -> Identity.ImportedKeyIdentity(
        id = id,
        displayName = displayName,
        publicKeyFingerprint = publicKeyFingerprint,
    )
    IdentityEntity.TYPE_KEYSTORE -> Identity.KeystoreIdentity(
        id = id,
        displayName = displayName,
        publicKeyFingerprint = publicKeyFingerprint,
        keystoreAlias = requireNotNull(keystoreAlias) { "KeystoreIdentity row $id missing keystoreAlias" },
    )
    else -> error("Unknown Identity.type in Room row: $type")
}
