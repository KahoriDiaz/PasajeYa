package com.pasajeya.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.ErrorRed
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.feature.auth.domain.validation.PasswordRequirement
import com.pasajeya.app.feature.auth.domain.validation.PasswordStrength
import com.pasajeya.app.feature.auth.domain.validation.PasswordValidation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) {
                            Text(
                                text = stringResource(R.string.action_back),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled && !loading
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(text = text)
    }
}

@Composable
fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        Text(text = text)
    }
}

@Composable
fun FormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun ScreenHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = TextGray
            )
        }
    }
}

@Composable
fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.email_label)) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        supportingText = errorText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        )
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.password_label),
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        supportingText = errorText?.let { { Text(it) } },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(
                onClick = onToggleVisibility,
                enabled = enabled
            ) {
                Text(
                    text = stringResource(
                        if (visible) R.string.action_hide_password
                        else R.string.action_show_password
                    )
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        )
    )
}

enum class MessageTone {
    ERROR,
    SUCCESS,
    INFO
}

@Composable
fun InlineMessage(
    text: String,
    tone: MessageTone,
    modifier: Modifier = Modifier
) {
    val color = when (tone) {
        MessageTone.ERROR -> ErrorRed
        MessageTone.SUCCESS -> SuccessGreen
        MessageTone.INFO -> PrimaryBlue
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("inline_message"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
fun PasswordRequirements(
    validation: PasswordValidation?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.password_requirements_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        if (validation == null) {
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
            return@Column
        }

        PasswordRequirementRow(
            text = stringResource(R.string.password_requirement_length),
            met = validation.meets(PasswordRequirement.LENGTH)
        )
        PasswordRequirementRow(
            text = stringResource(R.string.password_requirement_uppercase),
            met = validation.meets(PasswordRequirement.UPPERCASE)
        )
        PasswordRequirementRow(
            text = stringResource(R.string.password_requirement_lowercase),
            met = validation.meets(PasswordRequirement.LOWERCASE)
        )
        PasswordRequirementRow(
            text = stringResource(R.string.password_requirement_digit),
            met = validation.meets(PasswordRequirement.DIGIT)
        )
        PasswordRequirementRow(
            text = stringResource(R.string.password_requirement_symbol),
            met = validation.meets(PasswordRequirement.SYMBOL)
        )
        PasswordRequirementRow(
            text = stringResource(R.string.password_requirement_no_whitespace),
            met = validation.meets(PasswordRequirement.NO_WHITESPACE)
        )
        PasswordRequirementRow(
            text = stringResource(R.string.password_requirement_not_email),
            met = validation.meets(PasswordRequirement.NOT_EMAIL_LOCAL_PART)
        )
        PasswordRequirementRow(
            text = stringResource(R.string.password_requirement_not_common),
            met = validation.meets(PasswordRequirement.NOT_COMMON)
        )

        val strength = when (validation.strength) {
            PasswordStrength.WEAK -> stringResource(R.string.password_strength_weak)
            PasswordStrength.FAIR -> stringResource(R.string.password_strength_fair)
            PasswordStrength.GOOD -> stringResource(R.string.password_strength_good)
            PasswordStrength.STRONG -> stringResource(R.string.password_strength_strong)
        }
        val strengthColor = when (validation.strength) {
            PasswordStrength.WEAK -> ErrorRed
            PasswordStrength.FAIR -> Color(0xFFE0A100)
            PasswordStrength.GOOD -> PrimaryBlue
            PasswordStrength.STRONG -> SuccessGreen
        }
        Text(
            text = stringResource(R.string.password_strength, strength),
            style = MaterialTheme.typography.bodyMedium,
            color = strengthColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PasswordRequirementRow(text: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (met) "✓" else "•",
            modifier = Modifier.width(20.dp),
            color = if (met) SuccessGreen else TextGray
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (met) SuccessGreen else TextGray
        )
    }
}

@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.loading)
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = PrimaryBlue)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextGray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StartupErrorScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.startup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.startup_message),
            style = MaterialTheme.typography.bodyLarge,
            color = TextGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryButton(
            text = stringResource(R.string.action_retry),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
    }
}
