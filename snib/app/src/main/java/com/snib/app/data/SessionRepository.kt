package com.snib.app.data

import android.content.Context
import com.snib.app.recovery.Breaks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one source of truth for "is this phone snibbed, and does this package get through".
 *
 * Everything runs in a single process, so the accessibility service, the foreground service and
 * the UI all read the same object. The blocking hot path never touches the database: it reads two
 * volatile fields. Writes go to Room synchronously first, then update the cache.
 */
object SessionRepository {

    lateinit var dao: SnibDao
        private set

    @Volatile private var blockedCache: Set<String> = emptySet()
    @Volatile private var stateCache: SessionStateEntity = SessionStateEntity()
    @Volatile private var loaded = false

    private val _state = MutableStateFlow(SessionStateEntity())
    val state: StateFlow<SessionStateEntity> = _state.asStateFlow()

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    fun init(context: Context) {
        if (loaded) return
        dao = SnibDatabase.get(context).dao()
        reload()
    }

    /** Safe to call from any thread. Cheap after the first call. */
    fun ensureLoaded(context: Context) {
        if (!loaded) init(context)
    }

    @Synchronized
    fun reload() {
        stateCache = dao.sessionStateNow() ?: SessionStateEntity()
        blockedCache = dao.blockedPackagesNow().toSet()
        loaded = true
        _state.value = stateCache
        _blocked.value = blockedCache
    }

    // --- reads on the hot path ---

    val snibbedAt: Long? get() = stateCache.snibbedAt
    val isSnibbed: Boolean get() = stateCache.snibbedAt != null

    fun onBreak(now: Long = System.currentTimeMillis()): Boolean =
        (stateCache.breakUntil ?: 0L) > now

    fun breakEndsAt(): Long? = stateCache.breakUntil?.takeIf { it > System.currentTimeMillis() }

    /** Called from the accessibility callback. Must stay allocation-light. */
    fun shouldBlock(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val state = stateCache
        if (state.snibbedAt == null) return false
        if ((state.breakUntil ?: 0L) > now) return false
        return packageName in blockedCache
    }

    // --- writes ---

    @Synchronized
    fun snib(now: Long = System.currentTimeMillis()) {
        if (stateCache.snibbedAt != null) return
        write(stateCache.copy(snibbedAt = now, breakUntil = null, lastTokenSeenAt = now))
    }

    /** Returns the duration of the session just ended, in millis, or null if nothing was running. */
    @Synchronized
    fun unsnib(endedBy: String, now: Long = System.currentTimeMillis()): Long? {
        val startedAt = stateCache.snibbedAt ?: return null
        dao.recordSession(SessionEntity(startedAt = startedAt, endedAt = now, endedBy = endedBy))
        write(stateCache.copy(snibbedAt = null, breakUntil = null, lastTokenSeenAt = now))
        return now - startedAt
    }

    @Synchronized
    fun markTokenSeen(now: Long = System.currentTimeMillis()) {
        write(stateCache.copy(lastTokenSeenAt = now))
    }

    /** Starts an emergency break. Returns false if none are left this week. */
    @Synchronized
    fun startBreak(now: Long = System.currentTimeMillis()): Boolean {
        if (stateCache.snibbedAt == null) return false
        if (Breaks.remaining(dao, now) <= 0) return false
        dao.recordBreak(BreakEntity(startedAt = now, weekKey = Breaks.weekKey(now)))
        write(stateCache.copy(breakUntil = now + Breaks.DURATION_MS))
        return true
    }

    @Synchronized
    fun setBlocked(packageName: String, blocked: Boolean) {
        // Editable only while unsnibbed. Without this line someone eventually builds an edit
        // path and the edit path becomes the bypass.
        if (isSnibbed) return
        if (blocked) dao.block(BlockedAppEntity(packageName)) else dao.unblock(packageName)
        blockedCache = dao.blockedPackagesNow().toSet()
        _blocked.value = blockedCache
    }

    private fun write(next: SessionStateEntity) {
        dao.putSessionState(next)   // synchronous, before anything else believes it
        stateCache = next
        _state.value = next
    }
}
