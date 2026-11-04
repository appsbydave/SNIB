package com.snib.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.snib.app.AppState
import com.snib.app.data.SessionRepository
import com.snib.app.ui.setup.SetupFlow
import com.snib.app.ui.theme.Snib
import kotlinx.coroutines.delay

private object Route {
    const val HOME = "home"
    const val APPS = "apps"
    const val TOKENS = "tokens"
    const val CODES = "codes"
    const val REDEEM = "redeem"
    const val BREAK = "break"
}

@Composable
fun SnibRoot() {
    val context = LocalContext.current
    val setupComplete by AppState.setupComplete.collectAsState()
    val notice by AppState.notice.collectAsState()
    val allowed by SessionRepository.allowed.collectAsState()
    val navController = rememberNavController()

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4_000)
            AppState.clearNotice()
        }
    }

    Surface(color = Snib.slate, modifier = Modifier.fillMaxSize()) {
        if (!setupComplete) {
            SetupFlow { AppState.completeSetup(context) }
            return@Surface
        }

        NavHost(
            navController = navController,
            startDestination = Route.HOME,
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        ) {
            composable(Route.HOME) {
                HomeScreen(
                    notice = notice,
                    keptCount = allowed.size,
                    onOpenBlocklist = { navController.navigate(Route.APPS) },
                    onOpenTokens = { navController.navigate(Route.TOKENS) },
                    onOpenCodes = { navController.navigate(Route.CODES) },
                    onTakeBreak = { navController.navigate(Route.BREAK) },
                    onUseCode = { navController.navigate(Route.REDEEM) },
                )
            }
            composable(Route.APPS) {
                AppsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.TOKENS) {
                TokensScreen(notice = notice, onBack = { navController.popBackStack() })
            }
            composable(Route.CODES) {
                RecoveryCodesScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.REDEEM) {
                RedeemCodeScreen(
                    onBack = { navController.popBackStack() },
                    onRedeemed = {
                        AppState.say("Unsnibbed with a recovery code.")
                        navController.popBackStack(Route.HOME, inclusive = false)
                    },
                )
            }
            composable(Route.BREAK) {
                BreakScreen(
                    onBack = { navController.popBackStack() },
                    onStarted = { navController.popBackStack(Route.HOME, inclusive = false) },
                )
            }
        }
    }
}
