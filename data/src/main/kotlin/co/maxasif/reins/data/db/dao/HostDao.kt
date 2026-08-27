package co.maxasif.reins.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import co.maxasif.reins.data.db.entity.HostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getById(id: String): HostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(host: HostEntity)

    @Update
    suspend fun update(host: HostEntity)

    @Delete
    suspend fun delete(host: HostEntity)

    @Query("DELETE FROM hosts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE hosts SET hostKeyFingerprint = :fingerprint WHERE id = :id")
    suspend fun updateHostKeyFingerprint(id: String, fingerprint: String)
}
