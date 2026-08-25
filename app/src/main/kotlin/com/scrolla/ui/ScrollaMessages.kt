package com.scrolla.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One transient message, on its way to the snackbar.
 *
 * [action] is deliberately a lambda rather than an id: the thing to retry is
 * almost always the call that just failed, and the ViewModel that made it is
 * the only place that knows how.
 */
data class UserMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

/**
 * The app's transient-message bus (PREMIUM_CHECKLIST P2.3a).
 *
 * Every error state in Scrolla was inline and permanent, which is right for a
 * screen that could not load and wrong for an action that failed. Tapping "set
 * as primary" and having it fail used to write into `LeaderboardUiState
 * .errorMessage` — the same field a failed *load* uses — so one failed tap
 * replaced the entire leaderboard with "Couldn't load", losing rows that were
 * on screen and perfectly valid. The board had not failed; a button had.
 *
 * A singleton rather than a parameter threaded through every ViewModel, matching
 * how [ScrollaGraph] already works in this codebase. It holds no Android
 * references and no state beyond a small replay-free buffer, so nothing leaks.
 *
 * Not for form validation ("group code must be 6 characters") — that belongs
 * next to the field, stays put, and does not time out.
 */
object ScrollaMessages {

    // extraBufferCapacity lets tryEmit() succeed without suspending, so a
    // ViewModel can report a failure from a non-suspending context. DROP_OLDEST
    // because if messages ever queue up, the newest one is the one the user is
    // waiting on.
    private val _messages = MutableSharedFlow<UserMessage>(
        extraBufferCapacity = MESSAGE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    /** Report something that failed but did not break the screen. */
    fun show(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _messages.tryEmit(UserMessage(text, actionLabel, action))
    }

    private const val MESSAGE_BUFFER = 8
}
