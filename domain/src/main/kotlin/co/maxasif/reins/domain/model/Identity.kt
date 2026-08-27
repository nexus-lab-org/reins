package co.maxasif.reins.domain.model

/**
 * An SSH key, referenced by one or more Hosts (ticket 009, CONTEXT.md's Identity definition). A
 * sealed type - the two variants have genuinely different fields and different sshj signing paths
 * (decrypt-then-sign vs. delegate-to-Keystore), so this is deliberately not a flat/nullable shape.
 *
 * Key material itself (the decrypted private key / passphrase for [ImportedKeyIdentity]) never
 * appears on this domain type - it stays inside `:data` (see `IdentityRepositoryImpl` and
 * `ImportedKeySigningMaterial`), so `:presentation` only ever sees the reference, never the secret.
 */
sealed class Identity {
    abstract val id: String
    abstract val displayName: String
    abstract val publicKeyFingerprint: String

    data class ImportedKeyIdentity(
        override val id: String,
        override val displayName: String,
        override val publicKeyFingerprint: String,
    ) : Identity()

    /** `KeystoreIdentity` (Android Keystore alias, EC P-256, no key material stored) is ticket 020. */
    data class KeystoreIdentity(
        override val id: String,
        override val displayName: String,
        override val publicKeyFingerprint: String,
        val keystoreAlias: String,
    ) : Identity()
}
