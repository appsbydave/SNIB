package com.snib.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SnibDao {

    // --- session state: synchronous by design, see SnibDatabase ---

    @Query("SELECT * FROM session_state WHERE id = 1")
    fun sessionStateNow(): SessionStateEntity?

    @Query("SELECT * FROM session_state WHERE id = 1")
    fun sessionStateFlow(): Flow<SessionStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putSessionState(state: SessionStateEntity)

    // --- blocklist ---

    @Query("SELECT packageName FROM blocked_apps")
    fun blockedPackagesNow(): List<String>

    @Query("SELECT packageName FROM blocked_apps")
    fun blockedPackagesFlow(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun block(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    fun unblock(packageName: String)

    // --- history ---

    @Insert
    fun recordSession(session: SessionEntity)

    // --- breaks ---

    @Query("SELECT COUNT(*) FROM breaks WHERE weekKey = :weekKey")
    fun breaksTakenNow(weekKey: String): Int

    @Query("SELECT COUNT(*) FROM breaks WHERE weekKey = :weekKey")
    fun breaksTakenFlow(weekKey: String): Flow<Int>

    @Insert
    fun recordBreak(brk: BreakEntity)

    // --- recovery codes ---

    @Query("SELECT * FROM recovery_codes")
    fun codesNow(): List<RecoveryCodeEntity>

    @Query("SELECT COUNT(*) FROM recovery_codes WHERE usedAt IS NULL")
    fun unusedCodeCountFlow(): Flow<Int>

    @Query("DELETE FROM recovery_codes")
    fun clearCodes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putCodes(codes: List<RecoveryCodeEntity>)

    @Query("UPDATE recovery_codes SET usedAt = :now WHERE hash = :hash AND usedAt IS NULL")
    fun spendCode(hash: String, now: Long): Int

    // --- tokens ---

    @Query("SELECT * FROM tokens ORDER BY addedAt")
    fun tokensFlow(): Flow<List<TokenEntity>>

    @Query("SELECT COUNT(*) FROM tokens")
    fun tokenCountNow(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putToken(token: TokenEntity)

    @Query("DELETE FROM tokens WHERE tokenId = :tokenId")
    fun forgetToken(tokenId: String)
}
