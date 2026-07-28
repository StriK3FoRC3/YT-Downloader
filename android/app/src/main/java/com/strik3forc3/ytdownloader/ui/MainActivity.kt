package com.strik3forc3.ytdownloader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.strik3forc3.ytdownloader.ui.home.HomeScreen
import com.strik3forc3.ytdownloader.ui.settings.SettingsScreen
import com.strik3forc3.ytdownloader.ui.theme.Motion
import com.strik3forc3.ytdownloader.ui.theme.YtDownloaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Android 13+ withholds notifications until asked. Without the grant the download
     * service still runs, but its progress notification never appears — so a backgrounded
     * batch looks like nothing is happening.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            YtDownloaderTheme {
                YtDownloaderApp()
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun YtDownloaderApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // Settings slides in over home rather than cutting, so the hierarchy is legible.
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(Motion.DurationMedium, easing = Motion.Emphasized),
            ) + fadeIn(tween(Motion.DurationFast))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(Motion.DurationMedium, easing = Motion.Emphasized),
            ) + fadeOut(tween(Motion.DurationFast))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(Motion.DurationMedium, easing = Motion.Emphasized),
            ) + fadeIn(tween(Motion.DurationFast))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(Motion.DurationMedium, easing = Motion.Emphasized),
            ) + fadeOut(tween(Motion.DurationFast))
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
