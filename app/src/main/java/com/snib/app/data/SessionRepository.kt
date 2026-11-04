package com.snib.app.data

import android.content.Context
import android.os.SystemClock
import com.snib.app.apps.InstalledApps
import com.snib.app.recovery.Breaks
import com.snib.app.recovery.TrustedClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one source of truth for "is this phone snibbed, and does this package get through".
 *
 * Everything runs in a single process, so the accessibility service, the foreground service and
 * the UI all read the same object. The blocking hot path never touches the database or the package
 * manager: it reads volatile fields — the session, the allowlist, the app catalog — and a cached
 * boot count. Writes go to Room synchronously first, then update the cache and emit on the state
 * flow — the accessibility service re-decides on every emission, so a snib, unsnib or break start
 * enforces itself immediately.
 */
object SessionRepository {

    /** Kept apps, at most. Digital Detox's lesson: a short list is the product. */
    const val MAX_ALLOWED = 5

    lateinit var dao: SnibDao
        private set

    private lateinit var appContext: Context

    @Volatile private var allowedCache: Set<String> = emptySet()
    @Volatile private var catalog: AppCatalog = AppCatalog.EMPTY
    @Volatile private var stateCache: SessionStateEntity = SessionStateEntity()
    @Volatile private var loaded = false

    /**
     * BOOT_COUNT, read once. It cannot change without a reboot, and a reboot restarts this
     * process, so caching it keeps the content-provider query off the accessibility hot path.
     */
    @Volatile private var currentBoot: Int = -1

    private val _state = MutableStateFlow(SessionStateEntity())
    val state: StateFlow<SessionStateEntity> = _state.asStateFlow()

    private val _allowed = MutableStateFlow<Set<String>>(emptySet())
    /** The kept apps. Shown on the snibbed home screen; everything else with an icon is blocked. */
    val allowed: StateFlow<Set<String>> = _allowed.asStateFlow()

    fun init(context: Context) {
        if (loaded) return
        appContext = context.applicationContext
        dao = SnibDatabase.get(appContext).dao()
        currentBoot = TrustedClock.bootCount(appContext)
        reload()
        refreshCatalog()
    }

    /** Safe to call from any thread. Cheap after the first call. */
    fun ensureLoaded(context: Context) {
        if (!loaded) init(context)
    }

    @Synchronized
    fun reload() {
        stateCache = dao.sessionStateNow() ?: SessionStateEntity()
        allowedCache = dao.allowedPackagesNow().toSet()
        loaded = true
        _state.value = stateCache
        _allowed.value = allowedCache
    }

    /**
     * Re-read which apps exist, which are protected and which is the launcher. Called at start-up,
     * on every snib, and whenever an app is installed, removed or changed — so an app installed
     * mid-session is blocked too, and a new default dialler or keyboard is protected straight away.
     */
    fun refreshCatalog() {
        if (!::appContext.isInitialized) return
        val next = InstalledApps.catalog(appContext)
        catalog = next
        // Forget kept apps that have since been uninstalled, so they don't use up one of the five.
        // Asked of the package manager directly, not inferred from the launcher list: mid-update
        // an app can briefly vanish from that list, and an auto-update must never un-keep it.
        if (next.launchable.isNotEmpty()) {
            val gone = allowedCache.filterNot { InstalledApps.isInstalled(appContext, it) }
            if (gone.isNotEmpty()) {
                synchronized(this) {
                    gone.forEach(dao::disallow)
                    allowedCache = dao.allowedPackagesNow().toSet()
                    _allowed.value = allowedCache
                }
            }
        }
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
    fun verdict(packageName: String): Verdict =
        BlockDecision.decide(isSnibbed, onBreak(), packageName, allowedCache, catalog)

    fun shouldBlock(packageName: String): Boolean = verdict(packageName) == Verdict.BLOCK

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
        refreshCatalog()
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

    /**
     * Keep or stop keeping an app. Returns false if nothing changed: snibbed, or already at five.
     */
    @Synchronized
    fun setAllowed(packageName: String, allowed: Boolean): Boolean {
        // Editable only while unsnibbed. Without this line someone eventually builds an edit
        // path and the edit path becomes the bypass.
        if (isSnibbed) return false
        if (allowed) {
            if (packageName !in allowedCache && allowedCache.size >= MAX_ALLOWED) return false
            dao.allow(AllowedAppEntity(packageName))
        } else {
            dao.disallow(packageName)
        }
        allowedCache = dao.allowedPackagesNow().toSet()
        _allowed.value = allowedCache
        return true
    }

    private fun write(next: SessionStateEntity) {
        dao.putSessionState(next)   // synchronous, before anything else believes it
        stateCache = next
        _state.value = next
    }
}
