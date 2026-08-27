package co.maxasif.reins.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.maxasif.reins.data.db.entity.IdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<IdentityEntity>>

    @Query("SELECT * FROM identities WHERE id = :id")
    suspend fun getById(id: String): IdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(identity: IdentityEntity)

    @Query("DELETE FROM identities WHERE id = :id")
    suspend fun deleteById(id: String)
}
