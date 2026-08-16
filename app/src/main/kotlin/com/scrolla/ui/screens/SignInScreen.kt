package com.scrolla.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.scrolla.R
import com.scrolla.auth.AuthRepository
import com.scrolla.ui.components.ScrollaPrimaryButton
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.spacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SignInScreen(
    onSignInSuccess: (isNewUser: Boolean) -> Unit = {},
    onSignInError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    // Build the GoogleSignInClient once, tied to this composition.
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // Modern Compose-friendly equivalent of startActivityForResult.
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    val authResult = AuthRepository().signInWithGoogleCredential(idToken).await()
                    val isNewUser = authResult.additionalUserInfo?.isNewUser == true
                    isLoading = false
                    onSignInSuccess(isNewUser)
                } else {
                    isLoading = false
                    Log.w("SignInScreen", "ID token is null after Google sign-in")
                    onSignInError("Authentication failed: ID token missing")
                }
            } catch (e: ApiException) {
                isLoading = false
                Log.w("SignInScreen", "Google sign-in failed: ${e.statusCode}", e)
                onSignInError("Google sign-in failed (code ${e.statusCode})")
            } catch (e: Exception) {
                isLoading = false
                Log.e("SignInScreen", "Firebase sign-in failed", e)
                onSignInError("Authentication failed — please try again")
            }
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = MaterialTheme.colorScheme.background
    val headlineColor = MaterialTheme.colorScheme.onBackground
    val finePrintColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = if (isDark) 0.15f else 0.18f),
                                    primaryColor.copy(alpha = if (isDark) 0f else 0.03f)
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.35f),
                                radius = if (isDark) (size.width * 1.2f) else (size.width * 2.0f)
                            )
                        )
                    }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .systemBarsPadding()
                    .padding(horizontal = MaterialTheme.spacing.large)
                    .padding(bottom = MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.weight(1.5f))

                Image(
                    painter = painterResource(id = R.drawable.scrolla_logo),
                    contentDescription = ScrollaStrings.APP_NAME,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = MaterialTheme.spacing.extraExtraSmall)
                        .fillMaxWidth(1f)
                        .graphicsLayer {
                            scaleX = 1.3f
                            scaleY = 1.3f
                        },
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )

                Spacer(modifier = Modifier.weight(0.7f))

                Column(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = ScrollaStrings.SIGN_IN_HEADLINE,
                        style = MaterialTheme.typography.displayLarge.copy(lineHeight = 62.sp),
                        color = headlineColor,
                        modifier = Modifier
                            .semantics { heading() }
                            .padding(bottom = MaterialTheme.spacing.extraExtraLarge)
                    )

                    ScrollaPrimaryButton(
                        text = ScrollaStrings.SIGN_IN_BUTTON,
                        onClick = {
                            isLoading = true
                            launcher.launch(googleSignInClient.signInIntent)
                        },
                        isLoading = isLoading,
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_g),
                                contentDescription = "Google Logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = MaterialTheme.spacing.large)
                    )

                    Text(
                        text = ScrollaStrings.SIGN_IN_FINE_PRINT,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = finePrintColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenPreview() {
    ScrollaUILabTheme {
        SignInScreen()
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SignInScreenDarkPreview() {
    ScrollaUILabTheme(darkTheme = true) {
        SignInScreen()
    }
}

@Preview(showBackground = true, fontScale = 2.0f)
@Composable
private fun SignInScreenLargeFontPreview() {
    ScrollaUILabTheme {
        SignInScreen()
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 360)
@Composable
private fun SignInScreenLandscapePreview() {
    ScrollaUILabTheme {
        SignInScreen()
    }
}
