package com.pasajeya.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pasajeya.app.core.ui.WelcomeScreen

object AppDestinations {
    const val WELCOME = "welcome"
}

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.WELCOME,
        modifier = modifier
    ) {
        composable(AppDestinations.WELCOME) {
            WelcomeScreen()
        }
    }
}
