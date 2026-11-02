package com.snib.app.data

import android.content.Context
import android.os.SystemClock
import com.snib.app.recovery.Breaks
import com.snib.app.recovery.TrustedClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one source of truth for "is this phone snibbed, and does this package get through".
 *
 * Everything runs in a single process, so the accessibility service, the foreground service and
 * the UI all read the same object. The blocking hot path never touches the database: it reads two
 * volatile fields and a cached boot count. Writes go to Room synchronously first, then update the
 * cache and emit on the state flow — the accessibility service re-decides on every emission, so a
 * snib, unsnib or break start enforces itself immediately.
 */
object SessionRepository {

    lateinit var dao: SnibDao
        private set

    private lateinit var appContext: Context

    @Volatile private var blockedCache: Set<String> = emptySet()
    @Volatile private var stateCache: SessionStateEntity = SessionStateEntity()
    @Volatile private var loaded = false

    /**
     * BOOT_COUNT, read once. It cannot change without a reboot, and a reboot restarts this
     * process, so caching it keeps the content-provider query off the accessibility hot path.
     */
    @Volatile private var currentBoot: Int = -1

    private val _state = MutableStateFlow(SessionStateEntity())
    val state: StateFlow<SessionStateEntity> = _state.asStateFlow()

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    fun init(context: Context) {
        if (loaded) return
        appContext = context.applicationContext
        dao = SnibDatabase.get(appContext).dao()
        currentBoot = TrustedClock.bootCount(appContext)
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

    /** True while an emergency break is running. Timed on elapsedRealtime; a reboot ends it. */
    fun onBreak(): Boolean {
        val state = stateCache
        val startedElapsed = state.breakStartedElapsed ?: return false
        if (state.breakBoot != currentBoot) return false
        return SystemClock.elapsedRealtime() - startedElapsed < Breaks.DURATION_MS
    }

    /** Milliseconds left on the current break, or 0 if none. Drives the countdown and the watcher. */
    fun breakRemainingMs(): Long {
        val state = stateCache
        val startedElapsed = state.breakStartedElapsed ?: return 0L
        if (state.breakBoot != currentBoot) return 0L
        return (Breaks.DURATION_MS - (SystemClock.elapsedRealtime() - startedElapsed)).coerceAtLeast(0L)
    }

    /** Called from the accessibility callback and the usage poll. Must stay allocation-light. */
    fun shouldBlock(packageName: String): Boolean =
        BlockDecision.shouldBlock(isSnibbed, onBreak(), packageName, blockedCache)

    /** Trusted wall-clock "now" for week boundaries and the stranded check. */
    private fun trustedNow(): Long {
        val s = stateCache
        val anchor = if (s.anchorWall != null && s.anchorElapsed != null) {
            TrustedClock.Anchor(s.anchorWall, s.anchorElapsed, s.anchorBoot)
        } else {
            null
        }
        return TrustedClock.project(
            anchor, SystemClock.elapsedRealtime(), currentBoot, System.currentTimeMillis(),
        )
    }

    fun breaksRemaining(): Int = Breaks.remaining(dao, trustedNow())

    fun strandedSinceMs(): Long {
        val s = stateCache
        val since = s.lastTokenSeenAt ?: s.snibbedAt ?: return 0L
        return (trustedNow() - since).coerceAtLeast(0L)
    }

    // --- writes ---

    @Synchronized
    fun snib() {
        if (stateCache.snibbedAt != null) return
        val anchor = TrustedClock.anchor(appContext)
        currentBoot = anchor.boot
        write(
            stateCache.copy(
                snibbedAt = anchor.wall,
                lastTokenSeenAt = anchor.wall,
                anchorWall = anchor.wall,
                anchorElapsed = anchor.elapsed,
                anchorBoot = anchor.boot,
                breakStartedElapsed = null,
                breakBoot = -1,
            )
        )
    }

    /** Returns the duration of the session just ended, in millis, or null if nothing was running. */
    @Synchronized
    fun unsnib(endedBy: String): Long? {
        val startedAt = stateCache.snibbedAt ?: return null
        val now = System.currentTimeMillis()
        dao.recordSession(SessionEntity(startedAt = startedAt, endedAt = now, endedBy = endedBy))
        write(
            stateCache.copy(
                snibbedAt = null,
                lastTokenSeenAt = now,
                anchorWall = null,
                anchorElapsed = null,
                anchorBoot = -1,
                breakStartedElapsed = null,
                breakBoot = -1,
            )
        )
        return now - startedAt
    }

    @Synchronized
    fun markTokenSeen() {
        write(stateCache.copy(lastTokenSeenAt = System.currentTimeMillis()))
    }

    /** Starts an emergency break. Returns false if none are left this week. */
    @Synchronized
    fun startBreak(): Boolean {
        if (stateCache.snibbedAt == null) return false
        val trustedNow = trustedNow()
        if (Breaks.remaining(dao, trustedNow) <= 0) return false
        dao.recordBreak(BreakEntity(startedAt = trustedNow, weekKey = Breaks.weekKey(trustedNow)))
        write(
            stateCache.copy(
                breakStartedElapsed = SystemClock.elapsedRealtime(),
                breakBoot = currentBoot,
            )
        )
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
