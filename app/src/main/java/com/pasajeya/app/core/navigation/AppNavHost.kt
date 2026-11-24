package com.pasajeya.app.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasajeya.app.core.ui.NavBarItem
import com.pasajeya.app.core.ui.PasajeYaNavBar
import com.pasajeya.app.feature.auth.ui.common.SessionActionsViewModel
import com.pasajeya.app.feature.profile.ui.ProfileDetailScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pasajeya.app.R
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.ui.company.CompanyDetailsScreen
import com.pasajeya.app.feature.auth.ui.profile.CompanyPersonalProfileScreen
import com.pasajeya.app.feature.company.ui.CompanyHomeScreen
import com.pasajeya.app.feature.auth.ui.forgot.ForgotPasswordScreen
import com.pasajeya.app.feature.auth.ui.home.OperatorHomeScreen
import com.pasajeya.app.feature.auth.ui.home.PassengerHomeScreen
import com.pasajeya.app.feature.auth.ui.login.LoginScreen
import com.pasajeya.app.feature.auth.ui.operator.OperatorDetailsScreen
import com.pasajeya.app.feature.auth.ui.privacy.PrivacyPolicyScreen
import com.pasajeya.app.feature.auth.ui.profile.OperatorPersonalProfileScreen
import com.pasajeya.app.feature.auth.ui.profile.PassengerProfileScreen
import com.pasajeya.app.feature.auth.ui.register.RegisterScreen
import com.pasajeya.app.feature.auth.ui.role.RoleSelectionScreen
import com.pasajeya.app.feature.auth.ui.verification.VerificationScreen
import com.pasajeya.app.feature.auth.ui.welcome.WelcomeScreen
import com.pasajeya.app.feature.benefit.ui.BenefitScreen
import com.pasajeya.app.feature.benefit.ui.CarneCaptureScreen
import com.pasajeya.app.feature.fare.ui.FareEstimateScreen
import com.pasajeya.app.feature.history.ui.HistoryScreen
import com.pasajeya.app.feature.trip.ui.summary.TripSummaryScreen
import com.pasajeya.app.feature.wallet.ui.WalletScreen

object AppRoutes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
    const val VERIFY_EMAIL = "verify_email"
    const val ROLE_SELECTION = "role_selection"
    const val PASSENGER_PROFILE = "passenger_profile"
    const val PASSENGER_HOME = "passenger_home"
    const val OPERATOR_PROFILE = "operator_profile"
    const val OPERATOR_DETAILS = "operator_details"
    const val OPERATOR_HOME = "operator_home"
    const val PROFILE = "profile"
    const val COMPANY_PROFILE = "company_profile"
    const val COMPANY_DETAILS = "company_details"
    const val COMPANY_HOME = "company_home"
    const val PRIVACY_POLICY = "privacy_policy"
    const val FARE_ESTIMATE = "fare_estimate"
    const val TRIP_SUMMARY = "trip_summary/{tripId}"
    const val BENEFITS = "benefits"
    const val CAPTURE_CARNE = "capture_carne"
    const val HISTORY = "history"
    const val WALLET = "wallet"
}

fun routeFor(destination: AuthDestination): String = when (destination) {
    AuthDestination.WELCOME -> AppRoutes.WELCOME
    AuthDestination.LOGIN -> AppRoutes.LOGIN
    AuthDestination.VERIFY_EMAIL -> AppRoutes.VERIFY_EMAIL
    AuthDestination.CHOOSE_ROLE -> AppRoutes.ROLE_SELECTION
    AuthDestination.PASSENGER_HOME -> AppRoutes.PASSENGER_HOME
    AuthDestination.DRIVER_PROFILE -> AppRoutes.OPERATOR_DETAILS
    AuthDestination.DRIVER_HOME -> AppRoutes.OPERATOR_HOME
    AuthDestination.COMPANY_DETAILS -> AppRoutes.COMPANY_DETAILS
    AuthDestination.COMPANY_HOME -> AppRoutes.COMPANY_HOME
}

/** Pestañas de la barra inferior del pasajero. */
@Composable
private fun passengerTabs(): List<NavBarItem> = listOf(
    NavBarItem(AppRoutes.PASSENGER_HOME, stringResource(R.string.tab_home), Icons.Filled.Home),
    NavBarItem(
        AppRoutes.FARE_ESTIMATE,
        stringResource(R.string.tab_fare),
        Icons.Filled.DirectionsBus
    ),
    NavBarItem(AppRoutes.HISTORY, stringResource(R.string.tab_history), Icons.Filled.History),
    NavBarItem(
        AppRoutes.WALLET,
        stringResource(R.string.tab_wallet),
        Icons.Filled.AccountBalanceWallet
    ),
    NavBarItem(AppRoutes.PROFILE, stringResource(R.string.tab_profile), Icons.Filled.Person)
)

@Composable
fun AppNavHost(
    startDestination: AuthDestination,
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    fun clearAndNavigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    fun navigateTo(destination: AuthDestination) {
        clearAndNavigate(routeFor(destination))
    }

    // Las pestañas no apilan pantallas: se cambia entre ellas y se conserva su estado.
    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(AppRoutes.PASSENGER_HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val tabs = passengerTabs()
    val passengerBar: @Composable (String) -> Unit = { current ->
        PasajeYaNavBar(items = tabs, currentRoute = current, onSelect = ::openTab)
    }

    NavHost(
        navController = navController,
        startDestination = routeFor(startDestination),
        modifier = modifier
    ) {
        composable(AppRoutes.WELCOME) {
            WelcomeScreen(
                onLogin = { navController.navigate(AppRoutes.LOGIN) },
                onCreateAccount = { navController.navigate(AppRoutes.REGISTER) }
            )
        }

        composable(AppRoutes.LOGIN) {
            LoginScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        clearAndNavigate(AppRoutes.WELCOME)
                    }
                },
                onForgotPassword = {
                    navController.navigate(AppRoutes.FORGOT_PASSWORD)
                },
                onCreateAccount = {
                    navController.navigate(AppRoutes.REGISTER)
                },
                onNavigate = ::navigateTo
            )
        }

        composable(AppRoutes.REGISTER) {
            RegisterScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        clearAndNavigate(AppRoutes.LOGIN)
                    }
                },
                onLogin = {
                    if (!navController.popBackStack()) {
                        clearAndNavigate(AppRoutes.LOGIN)
                    }
                },
                onNavigate = ::navigateTo
            )
        }

        composable(AppRoutes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        clearAndNavigate(AppRoutes.LOGIN)
                    }
                }
            )
        }

        composable(AppRoutes.VERIFY_EMAIL) {
            VerificationScreen(onNavigate = ::navigateTo)
        }

        composable(AppRoutes.ROLE_SELECTION) {
            RoleSelectionScreen(
                onPassenger = {
                    navController.navigate(AppRoutes.PASSENGER_PROFILE)
                },
                onOperator = {
                    navController.navigate(AppRoutes.OPERATOR_PROFILE)
                },
                onCompany = {
                    navController.navigate(AppRoutes.COMPANY_PROFILE)
                },
                onNavigate = ::navigateTo
            )
        }

        composable(AppRoutes.PASSENGER_PROFILE) {
            PassengerProfileScreen(
                onBack = { navController.popBackStack() },
                onPrivacy = {
                    navController.navigate(AppRoutes.PRIVACY_POLICY)
                },
                onNavigate = ::navigateTo
            )
        }

        composable(AppRoutes.OPERATOR_PROFILE) {
            OperatorPersonalProfileScreen(
                onBack = { navController.popBackStack() },
                onPrivacy = {
                    navController.navigate(AppRoutes.PRIVACY_POLICY)
                },
                onNavigate = ::navigateTo
            )
        }

        composable(AppRoutes.COMPANY_PROFILE) {
            CompanyPersonalProfileScreen(
                onBack = { navController.popBackStack() },
                onPrivacy = {
                    navController.navigate(AppRoutes.PRIVACY_POLICY)
                },
                onNavigate = ::navigateTo
            )
        }

        composable(AppRoutes.COMPANY_DETAILS) {
            CompanyDetailsScreen(onNavigate = ::navigateTo)
        }

        composable(AppRoutes.OPERATOR_DETAILS) {
            OperatorDetailsScreen(onNavigate = ::navigateTo)
        }

        composable(AppRoutes.PASSENGER_HOME) {
            PassengerHomeScreen(
                onNavigate = ::navigateTo,
                onEstimate = { openTab(AppRoutes.FARE_ESTIMATE) },
                onHistory = { openTab(AppRoutes.HISTORY) },
                onWallet = { openTab(AppRoutes.WALLET) },
                onBenefits = { navController.navigate(AppRoutes.BENEFITS) },
                onProfile = { openTab(AppRoutes.PROFILE) },
                onTripFinished = { tripId ->
                    navController.navigate(AppRoutes.TRIP_SUMMARY.replace("{tripId}", tripId))
                },
                bottomBar = { passengerBar(AppRoutes.PASSENGER_HOME) }
            )
        }

        composable(AppRoutes.PROFILE) {
            val sessionViewModel: SessionActionsViewModel = hiltViewModel()
            LaunchedEffect(sessionViewModel) {
                sessionViewModel.events.collect(::navigateTo)
            }
            ProfileDetailScreen(
                onLogout = sessionViewModel::signOut,
                bottomBar = { passengerBar(AppRoutes.PROFILE) }
            )
        }

        composable(AppRoutes.OPERATOR_HOME) {
            OperatorHomeScreen(
                onNavigate = ::navigateTo,
                onProfile = { navController.navigate(AppRoutes.PROFILE) },
                onTripFinished = { tripId ->
                    navController.navigate(AppRoutes.TRIP_SUMMARY.replace("{tripId}", tripId))
                }
            )
        }

        composable(AppRoutes.COMPANY_HOME) {
            CompanyHomeScreen(onNavigate = ::navigateTo)
        }

        composable(AppRoutes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.FARE_ESTIMATE) {
            FareEstimateScreen(bottomBar = { passengerBar(AppRoutes.FARE_ESTIMATE) })
        }

        composable(
            route = AppRoutes.TRIP_SUMMARY,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) {
            TripSummaryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.BENEFITS) {
            BenefitScreen(
                onBack = { navController.popBackStack() },
                onVerifyUniversity = { navController.navigate(AppRoutes.CAPTURE_CARNE) }
            )
        }

        composable(AppRoutes.HISTORY) {
            HistoryScreen(
                onTripClick = { tripId ->
                    navController.navigate(AppRoutes.TRIP_SUMMARY.replace("{tripId}", tripId))
                },
                bottomBar = { passengerBar(AppRoutes.HISTORY) }
            )
        }

        composable(AppRoutes.WALLET) {
            WalletScreen(bottomBar = { passengerBar(AppRoutes.WALLET) })
        }

        composable(AppRoutes.CAPTURE_CARNE) {
            CarneCaptureScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }

    val sessionExpired by appViewModel.sessionExpired.collectAsStateWithLifecycle()
    if (sessionExpired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.session_closed_title)) },
            text = { Text(stringResource(R.string.session_closed_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        appViewModel.acknowledgeSessionExpired()
                        clearAndNavigate(AppRoutes.LOGIN)
                    }
                ) {
                    Text(stringResource(R.string.action_understood))
                }
            }
        )
    }
}
