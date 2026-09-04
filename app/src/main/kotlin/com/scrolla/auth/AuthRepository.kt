package com.scrolla.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.scrolla.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthResult
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await

/**
 * Repository handling Firebase Authentication.
 */
class AuthRepository {
    private val firebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /**
     * Signs in with a Google ID token.
     * @param idToken The ID token from Google Sign-In.
     * @return A Task that completes when the sign-in finishes.
     */
    fun signInWithGoogleCredential(idToken: String): Task<AuthResult> {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return firebaseAuth.signInWithCredential(credential)
    }

    /**
     * Signs out the current user.
     *
     * Firebase alone is not enough: the app signs in through GoogleSignInClient,
     * which caches the chosen account independently. Clearing only Firebase left
     * that cache intact, so the next sign-in silently reused the same Google
     * account without ever showing the picker — there was no way to switch
     * accounts short of clearing app data.
     *
     * The options must match those used to sign in, or the client being signed
     * out is not the one holding the cached account.
     */
    /**
     * Deletes the Firebase account.
     *
     * **Call this last.** Firestore rules are all gated on `request.auth.uid`,
     * so the user's cloud data must already be gone — see
     * `GroupRepository.deleteAllUserData()`. Deleting the account first strands
     * that data permanently.
     *
     * Firebase refuses to delete an account authenticated too long ago, which is
     * the single most likely failure here and is not an error the user can do
     * anything with unless it is named: [RecentLoginRequired] is returned so the
     * screen can ask them to sign in again rather than showing a raw exception.
     */
    suspend fun deleteAccount(context: Context): Result<Unit> {
        val user = firebaseAuth.currentUser
            ?: return Result.failure(IllegalStateException("No signed-in account to delete"))
        return try {
            user.delete().await()
            // The Google client caches the chosen account independently of
            // Firebase, exactly as it does on sign-out. Without this the picker
            // is skipped on the next sign-in and the just-deleted account is
            // silently reused.
            googleClient(context).signOut()
            Result.success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(RecentLoginRequired)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Firebase will not delete an account whose sign-in is too old. */
    object RecentLoginRequired : Exception("Please sign in again before deleting your account")

    private fun googleClient(context: Context) = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    )

    fun signOut(context: Context) {
        firebaseAuth.signOut()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso).signOut()
    }
}