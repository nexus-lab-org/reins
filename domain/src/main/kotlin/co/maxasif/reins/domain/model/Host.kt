package co.maxasif.reins.domain.model

/**
 * A saved remote target (ticket 009, CONTEXT.md's Host definition). Holds no live connection
 * state itself - a Host being "connected" is runtime state owned above the navigation graph, not
 * a Host field.
 *
 * [username] and [hostname] are separate fields (not a combined `user@host` string) so the Add
 * Host form can validate/edit them independently.
 *
 * [authMethod] picks how this Host authenticates - an existing [Identity] key, or a password
 * entered at connect time (never persisted). A successful password connect upgrades a Host to
 * [HostAuthMethod.Key] automatically (see `ConnectionService`'s post-connect key setup) so the
 * password is only ever needed once.
 *
 * [hostKeyFingerprint] is the TOFU pin: `null` until the first successful connect, then hard-blocks
 * with a loud warning on any later mismatch (no silent bypass).
 */
data class Host(
    val id: String,
    val displayName: String,
    val username: String,
    val hostname: String,
    val port: Int,
    val transport: Transport,
    val authMethod: HostAuthMethod,
    val hostKeyFingerprint: String?,
)

/** How a [Host] authenticates (ticket 009's Identity reference, now alongside a password option). */
sealed class HostAuthMethod {
    /** Authenticate with a saved [Identity] - either variant, referenced by [identityId]. */
    data class Key(val identityId: String) : HostAuthMethod()

    /**
     * Authenticate with a password entered at connect time - never persisted on the [Host] row.
     * On a successful connect, `ConnectionService` generates a new [Identity.KeystoreIdentity],
     * installs its public key on the remote's `authorized_keys`, and upgrades this Host to
     * [Key] so future connects no longer need the password.
     */
    object Password : HostAuthMethod()
}
