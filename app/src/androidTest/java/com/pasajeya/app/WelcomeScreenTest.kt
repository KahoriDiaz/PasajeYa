package com.pasajeya.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pasajeya.app.core.theme.PasajeYaTheme
import com.pasajeya.app.feature.auth.ui.welcome.WelcomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsWelcomeMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            PasajeYaTheme {
                WelcomeScreen(
                    onLogin = {},
                    onCreateAccount = {}
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.welcome_subtitle))
            .assertExists()
    }
}
