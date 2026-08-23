package com.scrolla.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.scrolla.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthResult
import com.google.android.gms.tasks.Task

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
    fun signOut(context: Context) {
        firebaseAuth.signOut()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso).signOut()
    }
}