package dev.aura.auradroid.data.local

import androidx.room.*
import dev.aura.auradroid.data.model.Memo
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {

    @Query("SELECT * FROM memos ORDER BY createdAt DESC")
    fun getAllMemos(): Flow<List<Memo>>

    @Query("SELECT * FROM memos WHERE id = :memoId")
    suspend fun getMemoById(memoId: String): Memo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemo(memo: Memo)

    @Update
    suspend fun updateMemo(memo: Memo)

    @Query("DELETE FROM memos WHERE id = :memoId")
    suspend fun deleteMemoById(memoId: String)
}
