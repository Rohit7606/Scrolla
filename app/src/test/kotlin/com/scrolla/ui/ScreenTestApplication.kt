package com.scrolla.ui

import android.app.Application

/**
 * The Application these screen tests run against — deliberately empty.
 *
 * Robolectric would otherwise instantiate the real `ScrollaApplication`, which
 * initialises Firebase, registers an auth listener and schedules a WorkManager
 * job. None of that has anything to do with whether a screen renders the right
 * copy when it has no data, and all of it caused failures that looked like the
 * screens were broken:
 *
 *  - `WorkManager.getInstance()` threw because the startup initializer never
 *    ran, killing all eight tests in `Application.onCreate`. That one was worth
 *    finding — it is now guarded in `SyncWorker.schedule`, because the same
 *    throw on a real phone would be a launch crash.
 *  - Firebase Sessions then failed on a background thread
 *    (`SessionLifecycleClient.sendMessageToServer` → `Messenger.send` NPE),
 *    which has no fix and no meaning outside a device.
 *
 * That these screens run against a bare Application is also worth stating: they
 * are pure presentational composables that take their data as parameters and
 * reach for nothing global. Keep it that way — the moment one of them reads
 * `ScrollaGraph` directly, this file stops working and that is the signal.
 */
class ScreenTestApplication : Application()
