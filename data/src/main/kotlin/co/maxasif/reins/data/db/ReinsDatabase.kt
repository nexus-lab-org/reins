package co.maxasif.reins.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import co.maxasif.reins.data.db.dao.HostDao
import co.maxasif.reins.data.db.dao.IdentityDao
import co.maxasif.reins.data.db.entity.HostEntity
import co.maxasif.reins.data.db.entity.IdentityEntity

/**
 * Ticket 018's Room database - Host/Identity persistence only so far.
 *
 * Bumped to version 3 for dropping the `herdrSessionName` Host column - Reins no longer launches
 * herdr itself (the SSH/Mosh Data Channel is a plain shell now; the user runs whatever they need,
 * including herdr, once attached). No migration is written - see [ReinsApplication]'s
 * `fallbackToDestructiveMigration()`.
 */
@Database(entities = [HostEntity::class, IdentityEntity::class], version = 3, exportSchema = false)
abstract class ReinsDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun identityDao(): IdentityDao

    companion object {
        const val FILE_NAME = "reins.db"
    }
}
