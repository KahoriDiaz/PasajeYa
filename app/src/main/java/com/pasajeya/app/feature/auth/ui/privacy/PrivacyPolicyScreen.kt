package com.pasajeya.app.feature.auth.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.auth.domain.PrivacyConsent

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    AuthScreen(
        title = stringResource(R.string.privacy_title),
        modifier = modifier.testTag("privacy_policy_screen"),
        onBack = onBack
    ) {
        ScreenHeading(
            title = stringResource(R.string.privacy_title),
            subtitle = stringResource(R.string.privacy_version, PrivacyConsent.CURRENT_VERSION)
        )
        Text(
            text = stringResource(R.string.privacy_last_updated),
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )
        Text(
            text = stringResource(R.string.privacy_intro),
            style = MaterialTheme.typography.bodyLarge
        )

        FormCard {
            PrivacySection(
                title = stringResource(R.string.privacy_data_title),
                body = stringResource(R.string.privacy_data_body)
            )
            PrivacySection(
                title = stringResource(R.string.privacy_dni_title),
                body = stringResource(R.string.privacy_dni_body)
            )
            PrivacySection(
                title = stringResource(R.string.privacy_location_title),
                body = stringResource(R.string.privacy_location_body)
            )
            PrivacySection(
                title = stringResource(R.string.privacy_security_title),
                body = stringResource(R.string.privacy_security_body)
            )
            PrivacySection(
                title = stringResource(R.string.privacy_rights_title),
                body = stringResource(R.string.privacy_rights_body)
            )
        }
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}
