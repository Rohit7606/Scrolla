package com.scrolla.ui

import android.content.Context
import com.scrolla.firestore.SyncEngine
import com.scrolla.room.ScrollRepository
import com.scrolla.room.ScrollRepositoryImpl
import com.scrolla.room.ScrollaDatabase

/**
 * The composition root for B's UI layer.
 *
 * `ScrollRepositoryImpl` needs the three Room DAOs, which need a Context — but
 * the screens construct their ViewModels with a bare `viewModel()` call and no
 * factory. Holding the built repository here lets a ViewModel take it as a
 * default constructor argument, the same shape `GroupViewModel` already uses
 * for `AuthRepository` / `GroupRepository`, while staying injectable in tests.
 *
 * [init] is called once from `ScrollaApplication.onCreate()`.
 */
object ScrollaGraph {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val labelCache = mutableMapOf<String, String>()

    /**
     * Human-readable app name for a package id ("com.instagram.android" → "Instagram").
     *
     * Falls back to the last dotted segment when the package is not installed —
     * the scroll history outlives an uninstall, so this must never throw.
     */
    @Synchronized
    fun appLabel(packageName: String): String = labelCache.getOrPut(packageName) {
        val context = appContext ?: return@getOrPut fallbackLabel(packageName)
        try {
            val packageManager = context.packageManager
            packageManager
                .getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
                .toString()
        } catch (e: Exception) {
            fallbackLabel(packageName)
        }
    }

    /**
     * Segments that identify a publisher or platform rather than the app, and so
     * never make a useful name on their own.
     */
    private val GENERIC_SEGMENTS = setOf(
        "com", "org", "net", "io", "co", "www",
        "app", "apps", "mobile", "android", "google"
    )

    /**
     * Best guess at a name when the label lookup fails.
     *
     * Taking the last dotted segment is wrong far more often than it looks:
     * "com.instagram.android" ends in "android", so Instagram, Twitter and every
     * other `.android` package all render as "Android". Use the first segment
     * that names something instead.
     */
    private fun fallbackLabel(packageName: String): String {
        val segment = packageName.split('.')
            .firstOrNull { it.isNotBlank() && it.lowercase() !in GENERIC_SEGMENTS }
            ?: packageName.substringAfterLast('.')
        return segment.replaceFirstChar { it.uppercase() }
    }

    /**
     * The one [SyncEngine] for this process (BUILD_PLAN 1.1).
     *
     * Deliberately shared between `SyncViewModel` and `SyncWorker`. The engine
     * serialises sync passes against itself, so a foreground pass and a
     * background one can only be stopped from doing the same work twice if they
     * are holding the same instance. Two engines would each be perfectly
     * well-behaved on their own and race each other — the 2026-09-14 duplicate
     * bug rebuilt with more moving parts.
     */
    val syncEngine: SyncEngine by lazy { SyncEngine(scrollRepository = scrollRepository) }

    val scrollRepository: ScrollRepository by lazy {
        val context = appContext
            ?: error(
                "ScrollaGraph.init() was never called. It must run in " +
                    "ScrollaApplication.onCreate() before any ViewModel is constructed."
            )
        val database = ScrollaDatabase.getDatabase(context)
        ScrollRepositoryImpl(
            scrollEventDao = database.scrollEventDao(),
            dailyTotalDao = database.dailyTotalDao(),
            serviceHealthDao = database.serviceHealthDao()
        )
    }
}
