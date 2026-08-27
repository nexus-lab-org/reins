package co.maxasif.reins.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a [co.maxasif.reins.domain.model.Host]. [transport] is stored as
 * [TRANSPORT_SSH]/[TRANSPORT_MOSH]; [authMethod] as [AUTH_KEY]/[AUTH_PASSWORD]. [identityId] is
 * only set (and the foreign key only enforced) when [authMethod] is [AUTH_KEY] - a
 * password-authenticated Host has no Identity yet.
 */
@Entity(
    tableName = "hosts",
    foreignKeys = [
        ForeignKey(
            entity = IdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["identityId"],
            // A Key-auth Host always references exactly one Identity (ticket 009: "no fallback
            // list") - block deleting an Identity that's still in use rather than silently
            // orphaning Hosts. Room/SQLite don't enforce this for null identityId rows
            // (Password-auth Hosts), which is exactly what's wanted here.
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("identityId")],
)
data class HostEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val username: String,
    val hostname: String,
    val port: Int,
    val transport: String,
    val authMethod: String,
    val identityId: String?,
    val hostKeyFingerprint: String?,
) {
    companion object {
        const val TRANSPORT_SSH = "ssh"
        const val TRANSPORT_MOSH = "mosh"
        const val AUTH_KEY = "key"
        const val AUTH_PASSWORD = "password"
    }
}
