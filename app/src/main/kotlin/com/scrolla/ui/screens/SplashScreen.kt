package com.scrolla.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrolla.R
import com.scrolla.ui.theme.ScrollaMotion
import com.scrolla.ui.theme.ScrollaUILabTheme
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    viewModel: SplashViewModel = viewModel()
) {
    val isFinished by viewModel.isSplashFinished.collectAsState()

    val scale = remember { Animatable(0.96f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Subtle scale in
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = EaseOutQuint)
            )
        }
        
        // Fade in
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300, easing = EaseOutQuint)
        )
    }

    // Trigger navigation after brief hold
    LaunchedEffect(isFinished) {
        if (isFinished) {
            onSplashFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.scrolla_wordmark),
            contentDescription = ScrollaStrings.APP_NAME,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .scale(scale.value)
                .alpha(alpha.value)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    ScrollaUILabTheme {
        SplashScreen(onSplashFinished = {})
    }
}
