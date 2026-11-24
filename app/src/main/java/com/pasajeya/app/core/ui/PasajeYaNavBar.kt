package com.pasajeya.app.core.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pasajeya.app.core.theme.TextGray

/** Una pestaña de la barra inferior. */
data class NavBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/** Barra inferior de la app: deja a mano lo que se usa todos los días. */
@Composable
fun PasajeYaNavBar(
    items: List<NavBarItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = TextGray,
        tonalElevation = 3.dp
    ) {
        items.forEach { item ->
            val selected = item.route == currentRoute
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(item.route) },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = {
                    Text(text = item.label, style = MaterialTheme.typography.labelSmall)
                },
                alwaysShowLabel = true,
                modifier = androidx.compose.ui.Modifier.testTag("tab_${item.route}"),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = TextGray,
                    unselectedTextColor = TextGray
                )
            )
        }
    }
}
